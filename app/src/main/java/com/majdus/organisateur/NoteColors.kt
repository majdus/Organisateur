package com.majdus.organisateur

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * Palette des couleurs de note.
 *
 * Les notes stockent une clé ("yellow") et non une valeur ARGB: la teinte exacte reste décidée
 * ici, et une clé inconnue — note écrite par une version ultérieure, donnée corrompue — retombe
 * sur le blanc au lieu de faire disparaître la carte.
 */
object NoteColors {

    const val DEFAULT = "default"

    data class Swatch(
        val key: String,
        @ColorRes val colorRes: Int,
        @StringRes val labelRes: Int
    )

    val PALETTE = listOf(
        Swatch(DEFAULT, R.color.note_default, R.string.note_color_default),
        Swatch("yellow", R.color.note_yellow, R.string.note_color_yellow),
        Swatch("green", R.color.note_green, R.string.note_color_green),
        Swatch("blue", R.color.note_blue, R.string.note_color_blue),
        Swatch("pink", R.color.note_pink, R.string.note_color_pink),
        Swatch("purple", R.color.note_purple, R.string.note_color_purple)
    )

    fun swatch(key: String?): Swatch =
        PALETTE.firstOrNull { it.key == key } ?: PALETTE.first()

    /** Couleur de fond résolue pour une clé de note. */
    fun color(context: Context, key: String?): Int =
        ContextCompat.getColor(context, swatch(key).colorRes)
}
