package com.majdus.organisateur

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View

class Notes : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editText: EditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)
        
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        sharedPreferences = getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        editText = findViewById(R.id.note)
        
        setupFormatToolbar()
        loadText()
    }

    private var isBoldActive = false
    private var isItalicActive = false
    private var activeColor: Int? = null

    private fun setupFormatToolbar() {
        findViewById<View>(R.id.btn_bold).setOnClickListener { 
            isBoldActive = !isBoldActive
            updateButtonVisualState(it, isBoldActive)
            toggleStyleForTyping(Typeface.BOLD, isBoldActive) 
        }
        findViewById<View>(R.id.btn_italic).setOnClickListener { 
            isItalicActive = !isItalicActive
            updateButtonVisualState(it, isItalicActive)
            toggleStyleForTyping(Typeface.ITALIC, isItalicActive) 
        }
        
        val colors = mapOf(
            R.id.btn_color_black to "#000000",
            R.id.btn_color_red to "#EF4444",
            R.id.btn_color_blue to "#3B82F6",
            R.id.btn_color_green to "#10B981",
            R.id.btn_color_orange to "#F59E0B",
            R.id.btn_color_purple to "#8B5CF6"
        )
        
        colors.forEach { (id, hex) ->
            findViewById<View>(id).setOnClickListener { toggleColor(it, Color.parseColor(hex), colors.keys) }
        }
    }

    private fun toggleColor(view: View, color: Int, allColorButtonIds: Set<Int>) {
        if (activeColor == color) {
            activeColor = null
            updateColorButtonVisualState(view, false)
            toggleColorForTyping(color, false)
        } else {
            // Réinitialise tous les boutons
            allColorButtonIds.forEach { id ->
                updateColorButtonVisualState(findViewById(id), false)
            }
            
            activeColor = color
            updateColorButtonVisualState(view, true)
            toggleColorForTyping(color, true)
        }
    }

    private fun updateColorButtonVisualState(view: View, isActive: Boolean) {
        // Fix du bug gris : on modifie l'échelle et l'alpha au lieu de casser le background
        if (isActive) {
            view.scaleX = 0.75f
            view.scaleY = 0.75f
            view.alpha = 0.5f
        } else {
            view.scaleX = 1.0f
            view.scaleY = 1.0f
            view.alpha = 1.0f
        }
    }

    private fun updateButtonVisualState(view: View, isActive: Boolean) {
        if (isActive) {
            view.alpha = 0.5f 
            view.setBackgroundColor(Color.parseColor("#E2E8F0"))
        } else {
            view.alpha = 1.0f
            view.setBackgroundColor(Color.TRANSPARENT)
        }
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
                // L'utilisateur veut retirer le style sur la sélection. 
                // Il faut trouver les spans existants et les couper (spliter) s'ils débordent de la sélection.
                val existingSpans = spannable.getSpans(start, end, StyleSpan::class.java).filter { it.style == style }
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
                if (span.style == style && spannable.getSpanFlags(span) == Spannable.SPAN_INCLUSIVE_INCLUSIVE) {
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
                val existingSpans = spannable.getSpans(start, end, ForegroundColorSpan::class.java).filter { it.foregroundColor == color }
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
                if (span.foregroundColor == color && spannable.getSpanFlags(span) == Spannable.SPAN_INCLUSIVE_INCLUSIVE) {
                    val spanStart = spannable.getSpanStart(span)
                    spannable.removeSpan(span)
                    if (spanStart < start) {
                        spannable.setSpan(span, spanStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveText()
    }

    private fun loadText() {
        val astJson = sharedPreferences.getString("note_ast", null)
        if (astJson.isNullOrEmpty()) {
            val oldNote = sharedPreferences.getString("note", "")
            editText.setText(oldNote)
        } else {
            val spannable = RichTextParser.parseAstToSpannable(astJson)
            editText.setText(spannable)
        }
    }

    private fun saveText() {
        with(sharedPreferences.edit()) {
            putString("note", editText.text.toString())
            putString("note_ast", RichTextParser.generateAstJsonFromSpannable(editText.text))
            apply()
        }
    }
}