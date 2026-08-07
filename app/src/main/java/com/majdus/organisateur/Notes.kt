package com.majdus.organisateur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Note
import com.majdus.organisateur.data.isChecklist
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
    private lateinit var addButton: FloatingActionButton
    private lateinit var fabMenu: LinearLayout
    private lateinit var fabScrim: View
    private lateinit var noteAdapter: NoteAdapter

    private var firstLoad = true

    /** Le menu de création est-il déplié ? */
    private var fabMenuOpen = false

    /** Tant que le menu est ouvert, le retour le referme au lieu de quitter l'écran. */
    private val closeFabMenuOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setFabMenuOpen(false)
    }

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
        findViewById<View>(R.id.rootLayout).padForSystemBars()

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        summaryView = findViewById(R.id.textSummary)
        emptyState = findViewById(R.id.emptyState)
        addButton = findViewById(R.id.addNote)
        fabMenu = findViewById(R.id.fabMenu)
        fabScrim = findViewById(R.id.fabScrim)
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
        // Défiler referme le menu: il flotte au-dessus de la grille, le laisser ouvert pendant
        // qu'elle bouge dessous serait incohérent.
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) setFabMenuOpen(false)
            }
        })

        (recyclerView.itemAnimator as? DefaultItemAnimator)?.apply {
            moveDuration = REORDER_DURATION_MS
            // Le contenu des tuiles ne change pas quand on réorganise: un fondu croisé n'aurait
            // rien à dire, et brouillerait le déplacement.
            supportsChangeAnimations = false
        }

        ItemTouchHelper(
            NoteDragCallback(
                onMoved = noteAdapter::moveItem,
                onDragStarted = ::hideAddButtons,
                onDragFinished = ::onDragFinished
            )
        ).attachToRecyclerView(recyclerView)

        addButton.setOnClickListener { setFabMenuOpen(!fabMenuOpen) }
        fabScrim.setOnClickListener { setFabMenuOpen(false) }
        findViewById<MaterialButton>(R.id.addTextNote).setOnClickListener { newNote(Note.TYPE_TEXT) }
        findViewById<MaterialButton>(R.id.addChecklist)
            .setOnClickListener { newNote(Note.TYPE_CHECKLIST) }
        findViewById<MaterialButton>(R.id.emptyAction).setOnClickListener { newNote(Note.TYPE_TEXT) }
        findViewById<MaterialButton>(R.id.emptyActionChecklist)
            .setOnClickListener { newNote(Note.TYPE_CHECKLIST) }

        onBackPressedDispatcher.addCallback(this, closeFabMenuOnBack)
    }

    override fun onResume() {
        super.onResume()
        // Retour de l'éditeur: la note vient peut-être d'être créée, modifiée ou vidée.
        refresh()
    }

    /** Une tuile soulevée occupe l'écran: le bouton s'efface, menu compris s'il était ouvert. */
    private fun hideAddButtons() {
        setFabMenuOpen(false, animate = false)
        addButton.hide()
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
        if (note.isChecklist) {
            val items = Checklist.parsePrefix(note.items)
            return NoteCard(
                id = note.id,
                title = note.title,
                preview = "",
                checklist = items.take(PREVIEW_MAX_ITEMS)
                    .map { ChecklistLine(it.text, it.checked) },
                // Compté sur ce que la requête d'aperçu a rapporté: au-delà de quelques
                // centaines d'éléments, le reste n'est plus lu et n'est donc pas annoncé.
                remainingItems = (items.size - PREVIEW_MAX_ITEMS).coerceAtLeast(0),
                colorKey = note.color,
                updatedAt = note.updatedAt
            )
        }

        val body = RichTextParser.parseAstPrefixToSpannable(note.bodyAst)
        val preview =
            if (body.length > PREVIEW_MAX_CHARS) body.subSequence(0, PREVIEW_MAX_CHARS) else body
        return NoteCard(
            id = note.id,
            title = note.title,
            preview = preview.trim(),
            checklist = emptyList(),
            remainingItems = 0,
            colorKey = note.color,
            updatedAt = note.updatedAt
        )
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        // L'état vide porte déjà ses propres appels à l'action.
        if (isEmpty) hideAddButtons() else addButton.show()
    }

    /**
     * Déploiement du bouton de création: les deux choix sortent de dessous, en quinconce, le plus
     * proche du bouton d'abord, et la croix se forme par un quart de tour du « + ».
     *
     * Le repli sans animation sert aux changements d'état où le menu n'a plus lieu d'être visible
     * du tout — grille vide, tuile soulevée: le faire disparaître en fondu là-dedans donnerait
     * l'impression d'un menu qui se referme tout seul.
     */
    private fun setFabMenuOpen(open: Boolean, animate: Boolean = true) {
        if (open == fabMenuOpen) return
        fabMenuOpen = open
        closeFabMenuOnBack.isEnabled = open

        addButton.animate().rotation(if (open) FAB_OPEN_ROTATION else 0f)
            .setDuration(if (animate) FAB_MENU_DURATION_MS else 0)
            .start()

        if (!animate) {
            fabScrim.visibility = View.GONE
            fabMenu.visibility = View.GONE
            return
        }

        fabScrim.animate().cancel()
        if (open) {
            fabScrim.alpha = 0f
            fabScrim.visibility = View.VISIBLE
            fabScrim.animate().alpha(1f).setDuration(FAB_MENU_DURATION_MS).start()
            fabMenu.visibility = View.VISIBLE
        } else {
            fabScrim.animate().alpha(0f).setDuration(FAB_MENU_DURATION_MS)
                .withEndAction { fabScrim.visibility = View.GONE }
                .start()
        }

        val items = fabMenu.children.toList()
        for ((index, item) in items.withIndex()) {
            // Le dernier choix est le plus bas, donc le plus proche du bouton: c'est lui qui part
            // en premier à l'ouverture, et qui rentre en dernier à la fermeture.
            val rank = items.size - 1 - index
            item.animate().cancel()
            if (open) {
                item.alpha = 0f
                item.translationY = FAB_MENU_OFFSET_PX * resources.displayMetrics.density
                item.animate().alpha(1f).translationY(0f)
                    .setStartDelay(rank * FAB_MENU_STAGGER_MS)
                    .setDuration(FAB_MENU_DURATION_MS)
                    .start()
            } else {
                item.animate().alpha(0f)
                    .translationY(FAB_MENU_OFFSET_PX * resources.displayMetrics.density)
                    .setStartDelay((items.size - 1 - rank) * FAB_MENU_STAGGER_MS)
                    .setDuration(FAB_MENU_DURATION_MS)
                    .withEndAction { if (!fabMenuOpen) fabMenu.visibility = View.GONE }
                    .start()
            }
        }
    }

    private fun openEditor(note: NoteCard?) {
        setFabMenuOpen(false)
        editorLauncher.launch(NoteEditor.intent(this, note?.id))
    }

    /** Création: seul ce chemin choisit le type — une note existante rouvre dans le sien. */
    private fun newNote(type: String) {
        setFabMenuOpen(false)
        editorLauncher.launch(NoteEditor.intent(this, null, type))
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

        /** Autant de lignes de liste que la carte en tient sans s'étirer démesurément. */
        const val PREVIEW_MAX_ITEMS = 7

        const val SPAN_COUNT = 2

        /** Assez court pour suivre le doigt, assez long pour que l'œil suive les tuiles. */
        const val REORDER_DURATION_MS = 180L

        /** Déploiement du bouton de création: vif, décalé d'un choix à l'autre. */
        const val FAB_MENU_DURATION_MS = 160L
        const val FAB_MENU_STAGGER_MS = 40L
        const val FAB_MENU_OFFSET_PX = 16f
        const val FAB_OPEN_ROTATION = 45f

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
