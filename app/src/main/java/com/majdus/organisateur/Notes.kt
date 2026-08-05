package com.majdus.organisateur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Liste des notes, en grille à hauteurs variables: une note de deux lignes n'a pas de raison
 * d'occuper autant de place qu'une liste de courses complète.
 *
 * L'ordre des tuiles appartient à l'utilisateur: un appui long soulève une note et la repose où
 * il veut, et rien ne la déplace ensuite dans son dos — modifier une note ne la fait plus remonter.
 *
 * L'écriture se fait dans [NoteEditor], qui porte aussi la suppression: l'appui long étant pris
 * par le déplacement, cet écran ne fait que présenter, réorganiser, et offrir l'annulation d'une
 * suppression demandée depuis l'éditeur.
 */
class Notes : AppCompatActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryView: TextView
    private lateinit var emptyState: View
    private lateinit var addButton: ExtendedFloatingActionButton
    private lateinit var noteAdapter: NoteAdapter

    private var firstLoad = true

    /**
     * L'éditeur demande la suppression plutôt que de l'exécuter: la note supprimée reste ainsi
     * en mémoire ici pour l'annulation, au lieu de transiter par une intention — dont la taille
     * est plafonnée à 1 Mo, largement dépassable par une note volumineuse.
     */
    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            NoteEditor.deleteRequest(result.data)?.let { delete(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        summaryView = findViewById(R.id.textSummary)
        emptyState = findViewById(R.id.emptyState)
        addButton = findViewById(R.id.addNote)
        recyclerView = findViewById(R.id.notes)

        noteAdapter = NoteAdapter(onClick = ::openEditor)
        recyclerView.layoutManager = StaggeredGridLayoutManager(
            SPAN_COUNT,
            StaggeredGridLayoutManager.VERTICAL
        ).apply {
            // Comblement des trous laissé actif, à dessein: chaque tuile va dans la colonne la
            // plus courte et la grille reste tassée en haut, sans creux. Le désactiver évitait
            // bien quelques mouvements de colonne pendant le glisser, mais laissait après coup
            // des affectations de colonne périmées — une tuile en haut à droite, sa voisine
            // décalée en dessous à gauche, et un trou en haut à gauche. Une grille qui se
            // réorganise vaut mieux qu'une grille trouée.
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        }
        recyclerView.adapter = noteAdapter
        recyclerView.addOnScrollListener(FabScrollBehaviour(addButton))

        (recyclerView.itemAnimator as? DefaultItemAnimator)?.apply {
            moveDuration = REORDER_DURATION_MS
            // Le contenu des tuiles ne change pas quand on réorganise: un fondu croisé n'aurait
            // rien à dire, et brouillerait le déplacement.
            supportsChangeAnimations = false
        }

        ItemTouchHelper(
            NoteDragCallback(
                onMoved = noteAdapter::moveItem,
                onDragStarted = { addButton.hide() },
                onDragFinished = ::onDragFinished
            )
        ).attachToRecyclerView(recyclerView)

        addButton.setOnClickListener { openEditor(null) }
        findViewById<MaterialButton>(R.id.emptyAction).setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        // Retour de l'éditeur: la note vient peut-être d'être créée, modifiée ou vidée.
        refresh()
    }

    /**
     * Tuile relâchée: l'ordre affiché devient l'ordre enregistré.
     *
     * L'écriture est retenue dans [orderWrite] et attendue par [refresh]: relire la base avant
     * que l'ordre y soit posé ferait ressauter la tuile à son ancienne place, sous les yeux de
     * l'utilisateur.
     */
    private fun onDragFinished() {
        addButton.show()
        val orderedIds = noteAdapter.currentOrderIds()
        val dao = db.noteDao()
        orderWrite = backgroundOrderWrites.launch { dao.applyOrder(orderedIds) }
    }

    private fun refresh() {
        lifecycleScope.launch {
            orderWrite?.join()
            val cards = withContext(Dispatchers.IO) {
                // La note unique des versions précédentes rejoint la collection au premier passage.
                NoteMigration.migrateLegacyNote(this@Notes, db.noteDao())
                db.noteDao().getAllNotesForList().map(::toCard)
            }
            noteAdapter.submit(cards)
            renderEmptyState(cards.isEmpty())
            summaryView.text = when (cards.size) {
                0 -> getString(R.string.notes_summary_none)
                1 -> getString(R.string.notes_summary_one)
                else -> getString(R.string.notes_summary_other, cards.size)
            }
            if (firstLoad && cards.isNotEmpty()) {
                firstLoad = false
                recyclerView.layoutAnimation =
                    AnimationUtils.loadLayoutAnimation(this@Notes, R.anim.layout_animation_slide_up)
                recyclerView.scheduleLayoutAnimation()
            }
        }
    }

    /**
     * Conversion faite hors du fil principal: elle parcourt le JSON de mise en forme. Le corps
     * arrive déjà tronqué par la requête, d'où la relecture tolérante au dernier tronçon coupé.
     */
    private fun toCard(note: Note): NoteCard {
        val body = RichTextParser.parseAstPrefixToSpannable(note.bodyAst)
        val preview =
            if (body.length > PREVIEW_MAX_CHARS) body.subSequence(0, PREVIEW_MAX_CHARS) else body
        return NoteCard(
            id = note.id,
            title = note.title,
            preview = preview.trim(),
            colorKey = note.color,
            updatedAt = note.updatedAt
        )
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        // L'état vide porte déjà son propre appel à l'action.
        if (isEmpty) addButton.hide() else addButton.show()
    }

    private fun openEditor(note: NoteCard?) {
        editorLauncher.launch(NoteEditor.intent(this, note?.id))
    }

    private fun delete(id: String) {
        lifecycleScope.launch {
            // Corps relu par tranches: une note très volumineuse doit rester supprimable,
            // et surtout restaurable à l'identique.
            val note = withContext(Dispatchers.IO) {
                db.noteDao().loadFullNote(id)?.also { db.noteDao().delete(it) }
            } ?: return@launch
            refresh()
            showUndoDeleteSnackbar(note)
        }
    }

    private fun showUndoDeleteSnackbar(note: Note) {
        Snackbar.make(findViewById(R.id.rootLayout), R.string.note_deleted, Snackbar.LENGTH_LONG)
            .setAnchorView(if (addButton.isShown) addButton else null)
            .setAction(R.string.action_undo) {
                lifecycleScope.launch {
                    // Réinsérée telle quelle, son rang compris: elle retrouve sa place exacte
                    // dans la grille, et non la tête de liste.
                    withContext(Dispatchers.IO) { db.noteDao().insert(note) }
                    refresh()
                }
            }
            .show()
    }

    private companion object {
        /** Au-delà, l'aperçu déborde de toute façon de la carte. */
        const val PREVIEW_MAX_CHARS = 400

        const val SPAN_COUNT = 2

        /** Assez court pour suivre le doigt, assez long pour que l'œil suive les tuiles. */
        const val REORDER_DURATION_MS = 180L

        /**
         * Portée détachée du cycle de vie, comme pour les sauvegardes de l'éditeur: déposer une
         * tuile puis quitter l'écran aussitôt ne doit pas faire perdre le rangement.
         */
        val backgroundOrderWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Enregistrement de l'ordre encore en vol; toute relecture de la base l'attend. */
        @Volatile
        var orderWrite: Job? = null
    }
}
