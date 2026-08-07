package com.majdus.organisateur

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
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
import java.util.UUID

/**
 * Rédaction d'une note: un titre, un corps, une couleur.
 *
 * Le corps est de l'un des deux types de Keep, et l'on passe de l'un à l'autre depuis le menu:
 * du texte enrichi, ou une liste à cocher. Les deux ne coexistent jamais — convertir transporte
 * le contenu et vide l'autre corps.
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
    private lateinit var toolbar: MaterialToolbar
    private lateinit var checklistView: RecyclerView
    private lateinit var formatDivider: View
    private lateinit var formatToolbar: View
    private lateinit var checklistAdapter: ChecklistAdapter
    private lateinit var checklistTouchHelper: ItemTouchHelper

    /** Les lignes de la liste à cocher, tenues ici et pilotées par [checklistAdapter]. */
    private val checklistItems = mutableListOf<ChecklistItem>()

    private lateinit var noteId: String
    private var noteType: String = Note.TYPE_TEXT
    private var colorKey: String = NoteColors.DEFAULT
    private var createdAt: Long = System.currentTimeMillis()

    /**
     * Rang de la note dans la grille. Conservé au même titre que [createdAt]: chaque sauvegarde
     * réécrit la ligne entière, et repartir de zéro renverrait la note en tête de grille à la
     * première frappe — exactement ce que l'ordre manuel doit empêcher.
     */
    private var position: Int = 0

    /** La note existe-t-elle en base ? Faux tant que rien n'a été écrit. */
    private var persisted = false

    /** Après suppression, plus rien ne doit être réécrit par l'enregistrement automatique. */
    private var discarded = false

    /**
     * Le corps a-t-il réellement été chargé dans le champ de saisie ?
     *
     * Tant que non, aucune écriture: un éditeur qui affiche un champ vide parce que le
     * chargement n'a pas abouti — processus relancé, note très volumineuse — ne doit surtout
     * pas conclure que la note est vide et la supprimer.
     */
    private var contentLoaded = false

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
        // Seul écran à saisie en ligne: la barre de mise en forme doit rester au-dessus du clavier.
        rootView.padForSystemBars(includeIme = true)
        titleView = findViewById(R.id.noteTitle)
        bodyView = findViewById(R.id.note)
        checklistView = findViewById(R.id.checklist)
        formatDivider = findViewById(R.id.formatDivider)
        formatToolbar = findViewById(R.id.format_toolbar)

        toolbar = findViewById(R.id.toolbar)
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
                R.id.action_toggle_checkboxes -> {
                    if (noteType == Note.TYPE_CHECKLIST) convertToText() else convertToChecklist()
                    true
                }
                R.id.action_uncheck_all -> {
                    checklistAdapter.uncheckAll()
                    refreshMenu()
                    true
                }
                R.id.action_delete_checked -> {
                    deleteCheckedItems()
                    true
                }
                else -> false
            }
        }

        setupFormatToolbar()
        setupChecklist()

        val requestedId = if (savedInstanceState != null) {
            colorKey = savedInstanceState.getString(STATE_COLOR) ?: NoteColors.DEFAULT
            createdAt = savedInstanceState.getLong(STATE_CREATED_AT, createdAt)
            position = savedInstanceState.getInt(STATE_POSITION, position)
            persisted = savedInstanceState.getBoolean(STATE_PERSISTED)
            noteType = savedInstanceState.getString(STATE_TYPE) ?: Note.TYPE_TEXT
            savedInstanceState.getString(STATE_ID)
        } else {
            noteType = intent.getStringExtra(EXTRA_NOTE_TYPE) ?: Note.TYPE_TEXT
            intent.getStringExtra(EXTRA_NOTE_ID)
        }
        noteId = requestedId ?: UUID.randomUUID().toString()

        if (persisted || savedInstanceState == null && requestedId != null) {
            // Le contenu est toujours relu en base, jamais restauré depuis l'état d'instance:
            // un texte long n'y tient pas (limite des transactions Binder) et se retrouverait
            // silencieusement vide.
            applyType()
            loadNote(noteId)
        } else {
            contentLoaded = true
            applyType()
            // Une liste qu'on vient d'ouvrir s'ouvre sur sa première ligne, curseur dedans:
            // c'est la seule chose qu'on puisse vouloir y faire.
            if (noteType == Note.TYPE_CHECKLIST && checklistItems.isEmpty()) {
                checklistAdapter.appendItem()
            }
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
        outState.putInt(STATE_POSITION, position)
        outState.putBoolean(STATE_PERSISTED, persisted)
        outState.putString(STATE_TYPE, noteType)
    }

    override fun onPause() {
        super.onPause()
        autoSave.removeCallbacks(autoSaveTask)
        // L'écriture est confiée à une portée indépendante de l'activité: partir par le bouton
        // d'accueil détruit la portée du cycle de vie, et la note serait perdue en chemin.
        val snapshot = snapshot()
        pendingSave = backgroundSaves.launch { write(snapshot) }
    }

    private fun loadNote(id: String) {
        lifecycleScope.launch {
            // Une sauvegarde de sortie peut encore être en vol (rotation): la laisser finir,
            // sinon on relirait la version précédente du corps.
            pendingSave?.join()
            // Lecture par tranches: un corps de plusieurs mégaoctets ne passe pas par un
            // `SELECT *`, la fenêtre du curseur SQLite étant plafonnée à 2 Mo. La conversion
            // de l'AST reste elle aussi hors du fil principal: sur une note très longue, elle
            // se compte en secondes.
            val loaded = withContext(Dispatchers.IO) {
                val note = db.noteDao().loadFullNote(id) ?: return@withContext null
                Loaded(
                    note = note,
                    body = RichTextParser.parseAstToSpannable(note.bodyAst),
                    items = Checklist.parse(note.items)
                )
            }
            if (loaded != null) {
                val note = loaded.note
                persisted = true
                colorKey = note.color
                createdAt = note.createdAt
                position = note.position
                noteType = if (note.isChecklist) Note.TYPE_CHECKLIST else Note.TYPE_TEXT
                titleView.setText(note.title)
                bodyView.setText(loaded.body)
                checklistItems.clear()
                checklistItems.addAll(loaded.items)
                checklistAdapter.reload()
                applyType()
                applyColor()
            } else {
                // La note n'existe plus (supprimée ailleurs): repartir d'un éditeur vierge.
                persisted = false
            }
            // Les observateurs sont posés après le remplissage: sinon le chargement lui-même
            // compterait comme une modification.
            contentLoaded = true
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

    /**
     * La suppression est déléguée à la liste: c'est elle qui affiche l'annulation, et faire
     * transiter le corps de la note par une intention buterait sur la limite de 1 Mo des
     * transactions Binder.
     */
    private fun deleteAndFinish() {
        autoSave.removeCallbacks(autoSaveTask)
        discarded = true
        // Une note jamais enregistrée n'a rien à supprimer, ni à faire annuler.
        val result = if (persisted) Intent().putExtra(EXTRA_DELETE_REQUEST, noteId) else Intent()
        setResult(RESULT_OK, result)
        finish()
    }

    /** Photographie de l'écran, prise sur le fil principal, seule à pouvoir lire les champs. */
    private fun snapshot(): Snapshot {
        val title = titleView.text?.toString()?.trim().orEmpty()
        if (noteType == Note.TYPE_CHECKLIST) {
            // Les lignes laissées vides ne sont pas enregistrées: une ligne qu'on a ouverte sans
            // rien y écrire n'a pas à réapparaître à la prochaine ouverture.
            val filled = Checklist.withoutBlanks(checklistItems)
            return Snapshot(
                title = title,
                bodyAst = EMPTY_AST,
                type = Note.TYPE_CHECKLIST,
                items = Checklist.serialize(filled),
                color = colorKey,
                isEmpty = title.isEmpty() && filled.isEmpty()
            )
        }

        val body = bodyView.text
        return Snapshot(
            title = title,
            bodyAst = RichTextParser.generateAstJsonFromSpannable(body),
            type = Note.TYPE_TEXT,
            items = EMPTY_ITEMS,
            color = colorKey,
            isEmpty = title.isEmpty() && body.isNullOrBlank()
        )
    }

    private suspend fun write(snapshot: Snapshot) {
        // contentLoaded: sans corps chargé, un champ vide ne veut rien dire — et surtout pas
        // que la note doit être supprimée.
        if (!contentLoaded || discarded || snapshot == lastWritten) return
        val dao = db.noteDao()
        withContext(Dispatchers.IO) {
            if (snapshot.isEmpty) {
                // Rien à garder: une note vide ne mérite pas une carte dans la liste.
                if (persisted) {
                    dao.deleteById(noteId)
                    persisted = false
                }
            } else {
                // Une note qui vient de naître se pose en tête de grille, juste avant la
                // première: aucune autre ligne n'a besoin d'être renumérotée pour cela.
                if (!persisted) position = (dao.minPosition() ?: 0) - 1
                val note = Note(
                    id = noteId,
                    title = snapshot.title,
                    bodyAst = snapshot.bodyAst,
                    color = snapshot.color,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis(),
                    position = position,
                    type = snapshot.type,
                    items = snapshot.items
                )
                if (persisted) dao.update(note) else dao.insert(note).also { persisted = true }
            }
        }
        lastWritten = snapshot
    }

    // ===== Liste à cocher =====

    private fun setupChecklist() {
        checklistAdapter = ChecklistAdapter(
            items = checklistItems,
            onChanged = {
                scheduleSave()
                refreshMenu()
            },
            onStartDrag = { holder -> checklistTouchHelper.startDrag(holder) }
        )
        checklistView.layoutManager = LinearLayoutManager(this)
        checklistView.adapter = checklistAdapter
        // Le fondu croisé par défaut ferait clignoter le champ en cours de saisie à chaque fois
        // qu'une autre ligne change d'état.
        (checklistView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        checklistTouchHelper = ItemTouchHelper(
            ChecklistDragCallback(
                canMove = checklistAdapter::isMovable,
                canSwap = checklistAdapter::canSwap,
                onMoved = checklistAdapter::moveRow,
                onDragFinished = ::scheduleSave
            )
        )
        checklistTouchHelper.attachToRecyclerView(checklistView)
    }

    /** Montre le corps correspondant au type, et met le menu en accord. */
    private fun applyType() {
        val isChecklist = noteType == Note.TYPE_CHECKLIST
        bodyView.visibility = if (isChecklist) View.GONE else View.VISIBLE
        checklistView.visibility = if (isChecklist) View.VISIBLE else View.GONE
        // La mise en forme des caractères n'a pas cours dans une liste, comme dans Keep.
        formatDivider.visibility = if (isChecklist) View.GONE else View.VISIBLE
        formatToolbar.visibility = if (isChecklist) View.GONE else View.VISIBLE
        titleView.imeOptions = if (isChecklist) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
        refreshMenu()
    }

    /**
     * Le menu de la barre est déclaré en XML, donc jamais repréparé par le système: son état est
     * remis d'aplomb à la main après chaque changement qui le concerne.
     */
    private fun refreshMenu() {
        val menu = toolbar.menu
        val isChecklist = noteType == Note.TYPE_CHECKLIST
        val hasChecked = isChecklist && checklistAdapter.hasCheckedItems()
        menu.findItem(R.id.action_toggle_checkboxes)?.setTitle(
            if (isChecklist) R.string.note_hide_checkboxes else R.string.note_show_checkboxes
        )
        menu.findItem(R.id.action_uncheck_all)?.isVisible = hasChecked
        menu.findItem(R.id.action_delete_checked)?.isVisible = hasChecked
    }

    /**
     * Passage en liste: chaque ligne non vide du corps devient un élément. Le texte enrichi perd
     * sa mise en forme au passage — une liste n'en porte pas — et c'est aussi ce que fait Keep.
     */
    private fun convertToChecklist() {
        checklistItems.clear()
        checklistItems.addAll(Checklist.fromText(bodyView.text))
        bodyView.setText("")
        noteType = Note.TYPE_CHECKLIST
        checklistAdapter.reload()
        applyType()
        // Une note vide devient une liste vide: autant y ouvrir tout de suite la première ligne.
        if (checklistItems.isEmpty()) checklistAdapter.appendItem()
        scheduleSave()
    }

    /** Retour au texte: une ligne par élément, cases retirées et état de cochage perdu. */
    private fun convertToText() {
        bodyView.setText(Checklist.toText(checklistItems))
        bodyView.setSelection(bodyView.text?.length ?: 0)
        checklistItems.clear()
        noteType = Note.TYPE_TEXT
        checklistAdapter.reload()
        applyType()
        scheduleSave()
    }

    private fun deleteCheckedItems() {
        val removed = checklistAdapter.deleteCheckedItems()
        if (removed == 0) return
        refreshMenu()
        Snackbar.make(
            rootView,
            if (removed == 1) {
                getString(R.string.note_checked_deleted_one)
            } else {
                getString(R.string.note_checked_deleted_other, removed)
            },
            Snackbar.LENGTH_SHORT
        ).show()
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
        // De bord à bord, `statusBarColor` n'est plus honoré: c'est le fond de fenêtre qu'on voit
        // derrière les barres, une fois le contenu repoussé par les encarts. On le teinte donc lui
        // aussi, sans quoi l'écran garderait un bandeau gris clair en haut et en bas.
        // Toutes les teintes de la palette sont claires: les icônes des barres restent noires.
        window.setBackgroundDrawable(ColorDrawable(color))
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
        val type: String,
        val items: String,
        val color: String,
        val isEmpty: Boolean
    )

    /** Note relue en base, ses deux corps déjà convertis hors du fil principal. */
    private data class Loaded(
        val note: Note,
        val body: CharSequence,
        val items: List<ChecklistItem>
    )

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"
        private const val EXTRA_NOTE_TYPE = "note_type"
        private const val EXTRA_DELETE_REQUEST = "delete_request_id"
        private const val STATE_ID = "state_id"
        private const val STATE_COLOR = "state_color"
        private const val STATE_CREATED_AT = "state_created_at"
        private const val STATE_POSITION = "state_position"
        private const val STATE_PERSISTED = "state_persisted"
        private const val STATE_TYPE = "state_type"
        private const val AUTO_SAVE_DELAY_MS = 700L
        private const val EMPTY_AST = "[]"
        private const val EMPTY_ITEMS = "[]"

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

        /** Sauvegarde de sortie encore en vol; l'instance suivante l'attend avant de relire. */
        @Volatile
        private var pendingSave: Job? = null

        /**
         * [noteId] nul pour une nouvelle note; [type] ne vaut alors que pour celle-ci — une note
         * existante ouvre toujours dans le type sous lequel elle a été enregistrée.
         */
        fun intent(context: Context, noteId: String?, type: String = Note.TYPE_TEXT): Intent =
            Intent(context, NoteEditor::class.java).apply {
                noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
                putExtra(EXTRA_NOTE_TYPE, type)
            }

        /** Identifiant de la note dont l'éditeur demande la suppression, s'il y en a une. */
        fun deleteRequest(data: Intent?): String? = data?.getStringExtra(EXTRA_DELETE_REQUEST)
    }
}
