package com.majdus.organisateur

import android.content.Context

/**
 * Libellés et choix de rappels.
 *
 * Les valeurs sont des minutes avant le début, telles que la base les range. L'ordre de la liste
 * est celui du menu d'ajout: du plus proche au plus lointain, parce que c'est l'ordre dans lequel
 * on y pense.
 */
object ReminderLabels {

    /** Choix proposés à l'ajout, en minutes avant le début. */
    val CHOICES = listOf(0, 5, 10, 15, 30, 60, 120, 24 * 60, 2 * 24 * 60)

    fun label(context: Context, minutesBefore: Int): String = when {
        minutesBefore <= 0 -> context.getString(R.string.reminder_at_start)
        minutesBefore < 60 -> context.getString(R.string.reminder_minutes, minutesBefore)
        minutesBefore < 24 * 60 -> context.getString(R.string.reminder_hours, minutesBefore / 60)
        else -> context.getString(R.string.reminder_days, minutesBefore / (24 * 60))
    }
}
