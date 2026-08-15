package com.majdus.organisateur

import com.majdus.organisateur.agenda.Zones
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Pont entre la clé de journée affichée par l'écran ("yyyy-MM-dd") et les instants du stockage.
 *
 * La clé n'est plus un format de base — les événements portent des instants — mais elle reste la
 * façon la plus simple de désigner la journée qu'on regarde. Un seul endroit sait la lire, donc
 * la grille du mois, la feuille d'édition et le planificateur tombent forcément sur les mêmes
 * bornes.
 */
object EventTimes {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    /** Durée d'un événement dont on ne connaît que l'heure de début. */
    const val DEFAULT_DURATION_MS = 3_600_000L

    /** Minuit du jour désigné; l'instant courant si la clé est illisible. */
    fun dayStart(date: String): Long {
        val day = parse(date) ?: return System.currentTimeMillis()
        return Zones.dayStart(day, Zones.current())
    }

    /** Bornes `[début, fin)` de la journée désignée; celles du jour même si la clé est illisible. */
    fun dayRange(date: String): Pair<Long, Long> {
        val zone = Zones.current()
        val day = parse(date) ?: LocalDate.now(zone)
        return Zones.dayStart(day, zone) to Zones.dayEnd(day, zone)
    }

    /** Bornes `[début, fin)` de la journée contenant [timestamp]. */
    fun dayRange(timestamp: Long): Pair<Long, Long> {
        val zone = Zones.current()
        val day = Zones.localDate(timestamp, zone)
        return Zones.dayStart(day, zone) to Zones.dayEnd(day, zone)
    }

    /** Instant de [date] à [hour]:[minute]; l'instant courant si la clé est illisible. */
    fun at(date: String, hour: Int, minute: Int): Long {
        val day = parse(date) ?: return System.currentTimeMillis()
        return Zones.toUtc(day.atTime(LocalTime.of(hour, minute)), Zones.current())
    }

    /**
     * Fin d'un événement commencé à [startUtc], sans jamais déborder sur le lendemain.
     *
     * Une durée par défaut qui dépasse minuit ferait apparaître un rendez-vous de fin de soirée
     * sur deux journées, ce que personne n'a demandé en le saisissant.
     */
    fun defaultEnd(startUtc: Long): Long {
        val zone = Zones.current()
        val dayEnd = Zones.dayEnd(Zones.localDate(startUtc, zone), zone)
        return minOf(startUtc + DEFAULT_DURATION_MS, dayEnd)
    }

    private fun parse(date: String): LocalDate? =
        runCatching { LocalDate.parse(date, dateFormat) }.getOrNull()
}
