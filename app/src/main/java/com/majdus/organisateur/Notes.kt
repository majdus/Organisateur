package com.majdus.organisateur

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Liste des notes, en grille à hauteurs variables: une note de deux lignes n'a pas de raison
 * d'occuper autant de place qu'une liste de courses complète.
 *
 * L'écriture se fait dans [NoteEditor]; cet écran ne fait que présenter et supprimer.
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

        noteAdapter = NoteAdapter(onClick = ::openEditor, onLongClick = ::confirmDelete)
        recyclerView.adapter = noteAdapter
        recyclerView.addOnScrollListener(FabScrollBehaviour(addButton))

        addButton.setOnClickListener { openEditor(null) }
        findViewById<MaterialButton>(R.id.emptyAction).setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        // Retour de l'éditeur: la note vient peut-être d'être créée, modifiée ou vidée.
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val cards = withContext(Dispatchers.IO) {
                // La note unique des versions précédentes rejoint la collection au premier passage.
                NoteMigration.migrateLegacyNote(this@Notes, db.noteDao())
                db.noteDao().getAllNotesForList().map(::toCard)
            }
            noteAdapter.submitList(cards) { renderEmptyState(cards.isEmpty()) }
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

    private fun confirmDelete(note: NoteCard) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.note_delete_title)
            .setMessage(note.title.ifBlank { getString(R.string.note_untitled) })
            .setPositiveButton(R.string.action_delete) { _, _ -> delete(note.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                    // Réinsérée telle quelle: même identifiant, même date, donc même place.
                    withContext(Dispatchers.IO) { db.noteDao().insert(note) }
                    refresh()
                }
            }
            .show()
    }

    private companion object {
        /** Au-delà, l'aperçu déborde de toute façon de la carte. */
        const val PREVIEW_MAX_CHARS = 400
    }
}
