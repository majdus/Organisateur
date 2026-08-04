package com.majdus.organisateur

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Rédaction d'une note: un titre, un corps en texte enrichi, une couleur.
 *
 * Il n'y a pas de bouton « Enregistrer »: la note est écrite peu après la dernière frappe et en
 * quittant l'écran. Une note dont le titre et le corps sont vides n'est jamais conservée — ni
 * créée, ni gardée en coquille si on l'a vidée.
 */
class NoteEditor : AppCompatActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }

    private lateinit var rootView: View
    private lateinit var titleView: EditText
    private lateinit var bodyView: EditText

    private lateinit var noteId: String
    private var colorKey: String = NoteColors.DEFAULT
    private var createdAt: Long = System.currentTimeMillis()

    /** La note existe-t-elle en base ? Faux tant que rien n'a été écrit. */
    private var persisted = false

    /** Après suppression, plus rien ne doit être réécrit par l'enregistrement automatique. */
    private var discarded = false

    private var lastWritten: Snapshot? = null

    private val autoSave = Handler(Looper.getMainLooper())
    private val autoSaveTask = Runnable {
        val snapshot = snapshot()
        lifecycleScope.launch { write(snapshot) }
    }

    private var isBoldActive = false
    private var isItalicActive = false
    private var activeColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        rootView = findViewById(R.id.rootLayout)
        titleView = findViewById(R.id.noteTitle)
        bodyView = findViewById(R.id.note)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_color -> {
                    showColorSheet()
                    true
                }
                R.id.action_delete -> {
                    deleteAndFinish()
                    true
                }
                else -> false
            }
        }

        setupFormatToolbar()

        if (savedInstanceState == null) {
            val requestedId = intent.getStringExtra(EXTRA_NOTE_ID)
            noteId = requestedId ?: UUID.randomUUID().toString()
            if (requestedId != null) loadNote(requestedId) else startWatching()
        } else {
            noteId = savedInstanceState.getString(STATE_ID) ?: UUID.randomUUID().toString()
            colorKey = savedInstanceState.getString(STATE_COLOR) ?: NoteColors.DEFAULT
            createdAt = savedInstanceState.getLong(STATE_CREATED_AT, createdAt)
            persisted = savedInstanceState.getBoolean(STATE_PERSISTED)
            // Le texte et ses spans sont restaurés par le système: rien à relire en base,
            // sinon on écraserait une saisie en cours.
            startWatching()
        }
        applyColor()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = saveAndFinish()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, noteId)
        outState.putString(STATE_COLOR, colorKey)
        outState.putLong(STATE_CREATED_AT, createdAt)
        outState.putBoolean(STATE_PERSISTED, persisted)
    }

    override fun onPause() {
        super.onPause()
        autoSave.removeCallbacks(autoSaveTask)
        // L'écriture est confiée à une portée indépendante de l'activité: partir par le bouton
        // d'accueil détruit la portée du cycle de vie, et la note serait perdue en chemin.
        val snapshot = snapshot()
        backgroundSaves.launch { write(snapshot) }
    }

    private fun loadNote(id: String) {
        lifecycleScope.launch {
            val note = withContext(Dispatchers.IO) { db.noteDao().getById(id) }
            if (note != null) {
                persisted = true
                colorKey = note.color
                createdAt = note.createdAt
                titleView.setText(note.title)
                bodyView.setText(RichTextParser.parseAstToSpannable(note.bodyAst))
                applyColor()
            }
            // Les observateurs sont posés après le remplissage: sinon le chargement lui-même
            // compterait comme une modification.
            startWatching()
            lastWritten = snapshot()
        }
    }

    private fun startWatching() {
        titleView.addTextChangedListener { scheduleSave() }
        bodyView.addTextChangedListener { scheduleSave() }
    }

    private fun scheduleSave() {
        autoSave.removeCallbacks(autoSaveTask)
        autoSave.postDelayed(autoSaveTask, AUTO_SAVE_DELAY_MS)
    }

    private fun saveAndFinish() {
        autoSave.removeCallbacks(autoSaveTask)
        val snapshot = snapshot()
        lifecycleScope.launch {
            // On attend la fin de l'écriture: la liste se rafraîchit dès notre disparition et
            // doit voir la note à jour.
            write(snapshot)
            if (!isFinishing) finish()
        }
    }

    private fun deleteAndFinish() {
        autoSave.removeCallbacks(autoSaveTask)
        discarded = true
        lifecycleScope.launch {
            val note = withContext(Dispatchers.IO) {
                db.noteDao().getById(noteId)?.also { db.noteDao().delete(it) }
            }
            persisted = false
            // Une note jamais enregistrée n'a rien à faire annuler.
            setResult(RESULT_OK, note?.let(::deletedIntent) ?: Intent())
            finish()
        }
    }

    /** Photographie de l'écran, prise sur le fil principal, seule à pouvoir lire les champs. */
    private fun snapshot(): Snapshot {
        val title = titleView.text?.toString()?.trim().orEmpty()
        val body = bodyView.text
        return Snapshot(
            title = title,
            bodyAst = RichTextParser.generateAstJsonFromSpannable(body),
            color = colorKey,
            isEmpty = title.isEmpty() && body.isNullOrBlank()
        )
    }

    private suspend fun write(snapshot: Snapshot) {
        if (discarded || snapshot == lastWritten) return
        val dao = db.noteDao()
        withContext(Dispatchers.IO) {
            if (snapshot.isEmpty) {
                // Rien à garder: une note vide ne mérite pas une carte dans la liste.
                if (persisted) {
                    dao.getById(noteId)?.let { dao.delete(it) }
                    persisted = false
                }
            } else {
                val note = Note(
                    id = noteId,
                    title = snapshot.title,
                    bodyAst = snapshot.bodyAst,
                    color = snapshot.color,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                if (persisted) dao.update(note) else dao.insert(note).also { persisted = true }
            }
        }
        lastWritten = snapshot
    }

    // ===== Couleur de la note =====

    private fun showColorSheet() {
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_note_color, null)
        val row = view.findViewById<LinearLayout>(R.id.colorRow)
        val sheet = BottomSheetDialog(this)

        for (swatch in NoteColors.PALETTE) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_note_color, row, false)
            val card = item.findViewById<MaterialCardView>(R.id.swatch)
            val check = item.findViewById<ImageView>(R.id.check)
            card.setCardBackgroundColor(NoteColors.color(this, swatch.key))
            check.visibility = if (swatch.key == colorKey) View.VISIBLE else View.GONE
            item.contentDescription = getString(swatch.labelRes)
            item.setOnClickListener {
                colorKey = swatch.key
                applyColor()
                scheduleSave()
                sheet.dismiss()
            }
            row.addView(item)
        }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun applyColor() {
        val color = NoteColors.color(this, colorKey)
        rootView.setBackgroundColor(color)
        // Toutes les teintes de la palette sont claires: le texte de la barre d'état reste noir.
        window.statusBarColor = color
    }

    // ===== Mise en forme du texte =====

    private fun setupFormatToolbar() {
        val boldButton = findViewById<View>(R.id.btn_bold)
        val italicButton = findViewById<View>(R.id.btn_italic)

        boldButton.setOnClickListener {
            isBoldActive = !isBoldActive
            boldButton.isSelected = isBoldActive
            toggleStyleForTyping(Typeface.BOLD, isBoldActive)
            scheduleSave()
        }
        italicButton.setOnClickListener {
            isItalicActive = !isItalicActive
            italicButton.isSelected = isItalicActive
            toggleStyleForTyping(Typeface.ITALIC, isItalicActive)
            scheduleSave()
        }

        for ((id, hex) in TEXT_COLORS) {
            findViewById<View>(id).setOnClickListener {
                toggleColor(it, Color.parseColor(hex))
            }
        }
    }

    private fun toggleColor(view: View, color: Int) {
        if (activeColor == color) {
            activeColor = null
            view.isSelected = false
            view.setBackgroundResource(0)
            toggleColorForTyping(color, false)
        } else {
            // Une seule couleur active à la fois: l'anneau de sélection quitte les autres.
            for (id in TEXT_COLORS.keys) {
                findViewById<View>(id).apply {
                    isSelected = false
                    setBackgroundResource(0)
                }
            }
            activeColor = color
            view.isSelected = true
            view.setBackgroundResource(R.drawable.bg_swatch_ring)
            toggleColorForTyping(color, true)
        }
        // Poser un span ne modifie pas le texte: les observateurs de saisie ne se déclenchent
        // pas, il faut donc redéclencher l'enregistrement à la main.
        scheduleSave()
    }

    private fun toggleStyleForTyping(style: Int, activate: Boolean) {
        val start = bodyView.selectionStart
        val end = bodyView.selectionEnd
        if (start < 0 || end < 0) return
        val spannable = bodyView.text as? Spannable ?: return

        if (start != end) {
            if (activate) {
                spannable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                // L'utilisateur veut retirer le style sur la sélection: les spans existants
                // sont coupés s'ils débordent de celle-ci.
                val existingSpans = spannable.getSpans(start, end, StyleSpan::class.java)
                    .filter { it.style == style }
                for (span in existingSpans) {
                    val spanStart = spannable.getSpanStart(span)
                    val spanEnd = spannable.getSpanEnd(span)
                    val flags = spannable.getSpanFlags(span)
                    spannable.removeSpan(span)

                    if (spanStart < start) {
                        spannable.setSpan(StyleSpan(style), spanStart, start, flags)
                    }
                    if (spanEnd > end) {
                        spannable.setSpan(StyleSpan(style), end, spanEnd, flags)
                    }
                }
            }
            return
        }

        if (activate) {
            spannable.setSpan(StyleSpan(style), start, start, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        } else {
            val spans = spannable.getSpans(start, start, StyleSpan::class.java)
            for (span in spans) {
                if (span.style == style &&
                    spannable.getSpanFlags(span) == Spannable.SPAN_INCLUSIVE_INCLUSIVE
                ) {
                    val spanStart = spannable.getSpanStart(span)
                    spannable.removeSpan(span)
                    if (spanStart < start) {
                        spannable.setSpan(span, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    private fun toggleColorForTyping(color: Int, activate: Boolean) {
        val start = bodyView.selectionStart
        val end = bodyView.selectionEnd
        if (start < 0 || end < 0) return
        val spannable = bodyView.text as? Spannable ?: return

        if (start != end) {
            if (activate) {
                spannable.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val existingSpans = spannable.getSpans(start, end, ForegroundColorSpan::class.java)
                    .filter { it.foregroundColor == color }
                for (span in existingSpans) {
                    val spanStart = spannable.getSpanStart(span)
                    val spanEnd = spannable.getSpanEnd(span)
                    val flags = spannable.getSpanFlags(span)
                    spannable.removeSpan(span)

                    if (spanStart < start) {
                        spannable.setSpan(ForegroundColorSpan(color), spanStart, start, flags)
                    }
                    if (spanEnd > end) {
                        spannable.setSpan(ForegroundColorSpan(color), end, spanEnd, flags)
                    }
                }
            }
            return
        }

        if (activate) {
            spannable.setSpan(ForegroundColorSpan(color), start, start, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        } else {
            val spans = spannable.getSpans(start, start, ForegroundColorSpan::class.java)
            for (span in spans) {
                if (span.foregroundColor == color &&
                    spannable.getSpanFlags(span) == Spannable.SPAN_INCLUSIVE_INCLUSIVE
                ) {
                    val spanStart = spannable.getSpanStart(span)
                    spannable.removeSpan(span)
                    if (spanStart < start) {
                        spannable.setSpan(span, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    private data class Snapshot(
        val title: String,
        val bodyAst: String,
        val color: String,
        val isEmpty: Boolean
    )

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"
        private const val PREFIX_DELETED = "deleted_"
        private const val STATE_ID = "state_id"
        private const val STATE_COLOR = "state_color"
        private const val STATE_CREATED_AT = "state_created_at"
        private const val STATE_PERSISTED = "state_persisted"
        private const val AUTO_SAVE_DELAY_MS = 700L

        /** Palette de mise en forme du texte, alignée sur les pastilles de la barre d'outils. */
        private val TEXT_COLORS = mapOf(
            R.id.btn_color_black to "#0F172A",
            R.id.btn_color_red to "#EF4444",
            R.id.btn_color_blue to "#3B82F6",
            R.id.btn_color_green to "#10B981",
            R.id.btn_color_orange to "#F59E0B",
            R.id.btn_color_purple to "#8B5CF6"
        )

        /**
         * Portée d'écriture volontairement détachée du cycle de vie: la sauvegarde de sortie
         * doit aboutir même quand l'activité est détruite dans la foulée.
         */
        private val backgroundSaves = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** [noteId] nul pour une nouvelle note. */
        fun intent(context: Context, noteId: String?): Intent =
            Intent(context, NoteEditor::class.java).apply {
                noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
            }

        private fun deletedIntent(note: Note): Intent = Intent().apply {
            putExtra(PREFIX_DELETED + "id", note.id)
            putExtra(PREFIX_DELETED + "title", note.title)
            putExtra(PREFIX_DELETED + "body", note.bodyAst)
            putExtra(PREFIX_DELETED + "color", note.color)
            putExtra(PREFIX_DELETED + "created", note.createdAt)
            putExtra(PREFIX_DELETED + "updated", note.updatedAt)
        }

        /** Note supprimée depuis l'éditeur, à proposer en annulation. */
        fun deletedNote(data: Intent?): Note? {
            val id = data?.getStringExtra(PREFIX_DELETED + "id") ?: return null
            return Note(
                id = id,
                title = data.getStringExtra(PREFIX_DELETED + "title").orEmpty(),
                bodyAst = data.getStringExtra(PREFIX_DELETED + "body").orEmpty(),
                color = data.getStringExtra(PREFIX_DELETED + "color") ?: NoteColors.DEFAULT,
                createdAt = data.getLongExtra(PREFIX_DELETED + "created", 0L),
                updatedAt = data.getLongExtra(PREFIX_DELETED + "updated", 0L)
            )
        }
    }
}
