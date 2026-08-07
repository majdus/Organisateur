package com.majdus.organisateur

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * Le champ d'une ligne de liste à cocher.
 *
 * Deux touches y prennent un sens particulier, comme dans Keep:
 * — Entrée coupe la ligne au curseur et en ouvre une nouvelle en dessous;
 * — Retour arrière en début de ligne recolle la ligne à la précédente.
 *
 * Le retour arrière ne peut pas se guetter avec un simple `OnKeyListener`: les claviers virtuels
 * n'envoient pas tous un événement de touche, beaucoup passent par `deleteSurroundingText` sur la
 * connexion de saisie. Les deux chemins sont donc interceptés.
 *
 * Le champ reste de type « une ligne » — c'est ce qui donne une touche d'action à la place du
 * saut de ligne sur le clavier — mais le repli est réactivé à la main pour qu'un élément long
 * s'affiche sur plusieurs lignes au lieu de défiler latéralement.
 */
class ChecklistEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** Renvoie vrai si la coupure a été prise en charge. */
    var onEnter: (() -> Boolean)? = null

    /** Renvoie vrai si la fusion avec la ligne précédente a été prise en charge. */
    var onBackspaceAtStart: (() -> Boolean)? = null

    init {
        setHorizontallyScrolling(false)
        maxLines = MAX_LINES
        setOnEditorActionListener { _, actionId, event ->
            val enterPressed = actionId == EditorInfo.IME_ACTION_NEXT ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (!enterPressed) return@setOnEditorActionListener false
            // Une même pression arrive deux fois — à l'appui puis au relâchement — et couperait
            // donc la ligne deux fois. Seul l'appui agit; le relâchement est consommé sans rien
            // faire, sinon il irait chercher le champ suivant de lui-même.
            if (event == null || event.action == KeyEvent.ACTION_DOWN) onEnter?.invoke()
            true
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (atStart() && handledBackspace()) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN &&
                    event.keyCode == KeyEvent.KEYCODE_DEL &&
                    atStart() && handledBackspace()
                ) {
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    /**
     * Troisième chemin du retour arrière: la touche d'un clavier physique, qui arrive directement
     * à la vue sans passer par la connexion de saisie.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && atStart() && handledBackspace()) return true
        return super.onKeyDown(keyCode, event)
    }

    /** Curseur collé au début, sans rien de sélectionné: il n'y a plus rien à effacer ici. */
    private fun atStart(): Boolean = selectionStart == 0 && selectionEnd == 0

    private fun handledBackspace(): Boolean = onBackspaceAtStart?.invoke() == true

    private companion object {
        /** Borne haute pour ne pas laisser un élément démesuré manger l'écran entier. */
        const val MAX_LINES = 12
    }
}
