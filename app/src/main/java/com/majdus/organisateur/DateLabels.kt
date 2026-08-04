package com.majdus.organisateur

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Libellés de dates partagés par les écrans Tâches et Calendrier: "aujourd'hui", "hier",
 * ou "le 3 août" au-delà. Une date proche se lit mieux relativement qu'en chiffres.
 */
object DateLabels {

    private val dayMonth = SimpleDateFormat("d MMMM", Locale.FRANCE)
    private val dayMonthYear = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
    private val weekdayDayMonth = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
    private val monthYear = SimpleDateFormat("LLLL yyyy", Locale.FRANCE)
    private val storageKey = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)

    /** "aujourd'hui", "hier", "demain", sinon "le 3 août" (avec l'année si elle diffère). */
    fun relativeDay(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String =
        when (daysBetween(timestamp, now)) {
            0L -> context.getString(R.string.date_today)
            1L -> context.getString(R.string.date_yesterday)
            -1L -> context.getString(R.string.date_tomorrow)
            else -> context.getString(R.string.date_on, absoluteDay(timestamp, now))
        }

    /** "3 août", ou "3 août 2024" si la date sort de l'année en cours. */
    fun absoluteDay(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val date = Date(timestamp)
        return if (yearOf(timestamp) == yearOf(now)) {
            dayMonth.format(date)
        } else {
            dayMonthYear.format(date)
        }
    }

    /** "mardi 4 août" — en-tête d'une journée. */
    fun weekday(timestamp: Long): String = weekdayDayMonth.format(Date(timestamp))

    /** "août 2026" — en-tête du calendrier. */
    fun month(timestamp: Long): String = monthYear.format(Date(timestamp))
        .replaceFirstChar { it.titlecase(Locale.FRANCE) }

    /** Clé de stockage d'une journée, format partagé avec la base ("yyyy-MM-dd"). */
    fun key(timestamp: Long): String = storageKey.format(Date(timestamp))

    fun isToday(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean =
        daysBetween(timestamp, now) == 0L

    /** Nombre de jours calendaires écoulés: positif si [timestamp] est dans le passé. */
    private fun daysBetween(timestamp: Long, now: Long): Long {
        val a = startOfDay(now)
        val b = startOfDay(timestamp)
        return Math.round((a - b) / 86_400_000.0)
    }

    private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun yearOf(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
}
