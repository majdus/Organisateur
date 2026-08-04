package com.majdus.organisateur

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.appbar.MaterialToolbar

/**
 * Bloc-notes en texte enrichi.
 *
 * La note est enregistrée toute seule peu après la dernière frappe, et l'état de cette
 * sauvegarde est affiché en permanence sous le titre: aucun bouton « Enregistrer » à penser
 * à appuyer, aucune saisie perdue.
 */
class Notes : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editText: EditText
    private lateinit var statusView: TextView

    private val autoSave = Handler(Looper.getMainLooper())
    private val autoSaveTask = Runnable { saveText() }

    private var isBoldActive = false
    private var isItalicActive = false
    private var activeColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        editText = findViewById(R.id.note)
        statusView = findViewById(R.id.textStatus)

        setupFormatToolbar()
        loadText()

        editText.addTextChangedListener {
            renderStatus(saved = false)
            scheduleSave()
        }
    }

    override fun onPause() {
        super.onPause()
        // Ne pas attendre le délai d'inactivité quand l'écran passe en arrière-plan.
        autoSave.removeCallbacks(autoSaveTask)
        saveText()
    }

    private fun scheduleSave() {
        autoSave.removeCallbacks(autoSaveTask)
        autoSave.postDelayed(autoSaveTask, AUTO_SAVE_DELAY_MS)
    }

    /** "Enregistré · 128 mots" — l'état de la note en une ligne. */
    private fun renderStatus(saved: Boolean) {
        val text = editText.text?.toString().orEmpty()
        if (text.isBlank()) {
            statusView.setText(R.string.notes_status_empty)
            return
        }
        val words = text.trim().split(WORD_SEPARATORS).size
        val wordsLabel = when (words) {
            1 -> getString(R.string.notes_words_one)
            else -> getString(R.string.notes_words_other, words)
        }
        statusView.text = getString(
            if (saved) R.string.notes_status_saved else R.string.notes_status_editing,
            wordsLabel
        )
    }

    private fun setupFormatToolbar() {
        val boldButton = findViewById<View>(R.id.btn_bold)
        val italicButton = findViewById<View>(R.id.btn_italic)

        boldButton.setOnClickListener {
            isBoldActive = !isBoldActive
            boldButton.isSelected = isBoldActive
            toggleStyleForTyping(Typeface.BOLD, isBoldActive)
            onFormattingChanged()
        }
        italicButton.setOnClickListener {
            isItalicActive = !isItalicActive
            italicButton.isSelected = isItalicActive
            toggleStyleForTyping(Typeface.ITALIC, isItalicActive)
            onFormattingChanged()
        }

        for ((id, hex) in COLORS) {
            findViewById<View>(id).setOnClickListener {
                toggleColor(it, Color.parseColor(hex))
            }
        }
    }

    /**
     * Poser un span ne modifie pas le texte: les observateurs de saisie ne se déclenchent
     * pas, il faut donc redéclencher l'enregistrement à la main.
     */
    private fun onFormattingChanged() {
        renderStatus(saved = false)
        scheduleSave()
    }

    private fun toggleColor(view: View, color: Int) {
        if (activeColor == color) {
            activeColor = null
            view.isSelected = false
            view.setBackgroundResource(0)
            toggleColorForTyping(color, false)
        } else {
            // Une seule couleur active à la fois: l'anneau de sélection quitte les autres.
            for (id in COLORS.keys) {
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
        onFormattingChanged()
    }

    private fun toggleStyleForTyping(style: Int, activate: Boolean) {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start < 0 || end < 0) return
        val spannable = editText.text as? Spannable ?: return

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
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start < 0 || end < 0) return
        val spannable = editText.text as? Spannable ?: return

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

    private fun loadText() {
        val astJson = sharedPreferences.getString(KEY_NOTE_AST, null)
        if (astJson.isNullOrEmpty()) {
            editText.setText(sharedPreferences.getString(KEY_NOTE, ""))
        } else {
            editText.setText(RichTextParser.parseAstToSpannable(astJson))
        }
        renderStatus(saved = true)
    }

    private fun saveText() {
        with(sharedPreferences.edit()) {
            putString(KEY_NOTE, editText.text.toString())
            putString(KEY_NOTE_AST, RichTextParser.generateAstJsonFromSpannable(editText.text))
            apply()
        }
        renderStatus(saved = true)
    }

    private companion object {
        const val PREFS_NAME = "organisateur"
        const val KEY_NOTE = "note"
        const val KEY_NOTE_AST = "note_ast"
        const val AUTO_SAVE_DELAY_MS = 700L
        val WORD_SEPARATORS = Regex("\\s+")

        /** Palette de mise en forme, alignée sur les pastilles de la barre d'outils. */
        val COLORS = mapOf(
            R.id.btn_color_black to "#0F172A",
            R.id.btn_color_red to "#EF4444",
            R.id.btn_color_blue to "#3B82F6",
            R.id.btn_color_green to "#10B981",
            R.id.btn_color_orange to "#F59E0B",
            R.id.btn_color_purple to "#8B5CF6"
        )
    }
}
