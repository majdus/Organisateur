package com.majdus.organisateur

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.majdus.organisateur.data.Event

/**
 * Palette des couleurs d'événement.
 *
 * Même principe que [NoteColors]: l'événement range une clé et non une valeur ARGB, donc la
 * teinte exacte reste décidée ici, et une clé inconnue retombe sur l'ambre de l'agenda plutôt que
 * de faire disparaître le bloc.
 */
object EventColors {

    data class Swatch(
        val key: String,
        @ColorRes val colorRes: Int,
        @StringRes val labelRes: Int
    )

    val PALETTE = listOf(
        Swatch(Event.DEFAULT_COLOR, R.color.event_default, R.string.event_color_default),
        Swatch("red", R.color.event_red, R.string.event_color_red),
        Swatch("orange", R.color.event_orange, R.string.event_color_orange),
        Swatch("green", R.color.event_green, R.string.event_color_green),
        Swatch("blue", R.color.event_blue, R.string.event_color_blue),
        Swatch("indigo", R.color.event_indigo, R.string.event_color_indigo),
        Swatch("purple", R.color.event_purple, R.string.event_color_purple),
        Swatch("pink", R.color.event_pink, R.string.event_color_pink),
        Swatch("grey", R.color.event_grey, R.string.event_color_grey)
    )

    fun swatch(key: String?): Swatch = PALETTE.firstOrNull { it.key == key } ?: PALETTE.first()

    fun color(context: Context, key: String?): Int =
        ContextCompat.getColor(context, swatch(key).colorRes)
}
