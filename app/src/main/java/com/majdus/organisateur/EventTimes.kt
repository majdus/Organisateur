package com.majdus.organisateur

import com.majdus.organisateur.data.Event
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Conversion entre la date d'un événement (stockée en "yyyy-MM-dd") et un instant.
 * Un seul endroit connaît le format: la feuille d'édition, la liste et le planificateur
 * calculent forcément la même heure de déclenchement.
 */
object EventTimes {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)

    /** Minuit du jour de l'événement; l'instant courant si la date est illisible. */
    fun dayStart(date: String): Long = parse(date)?.timeInMillis ?: System.currentTimeMillis()

    fun at(date: String, hour: Int, minute: Int): Long {
        val calendar = parse(date) ?: return System.currentTimeMillis()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }

    fun at(event: Event): Long = at(event.date, event.hour, event.minute)

    private fun parse(date: String): Calendar? {
        val parsed = runCatching { dateFormat.parse(date) }.getOrNull() ?: return null
        return Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
