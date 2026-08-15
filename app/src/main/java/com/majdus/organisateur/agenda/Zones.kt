package com.majdus.organisateur.agenda

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Conversions entre instants et jours civils.
 *
 * Deux ancrages cohabitent en base, et c'est délibéré:
 * - un événement à l'heure porte un instant absolu, relu dans le fuseau de l'appareil;
 * - un événement de journée entière porte minuit **UTC** de sa date et n'est jamais converti,
 *   parce que « le 14 août » doit rester le 14 août où que l'on ouvre l'application. C'est la
 *   convention de `CalendarContract` et d'iCalendar (`DTSTART;VALUE=DATE`).
 *
 * Aucune fonction ne va chercher le fuseau d'elle-même: il est toujours passé en paramètre.
 * C'est ce qui rend ce paquet vérifiable hors d'Android, et une expansion de récurrence
 * reproductible d'une machine à l'autre.
 *
 * `java.time` plutôt que `java.util.Calendar`: la bibliothèque est native depuis l'API 26, bien
 * en deçà du minSdk 28 du projet, et c'est la seule qui sache ajouter *un jour civil* plutôt que
 * 86 400 000 millisecondes — la distinction décide de tout au passage à l'heure d'hiver.
 */
object Zones {

    /**
     * Fuseau de l'appareil.
     *
     * Relu à chaque appel: le retenir dans un `val` le figerait au chargement de la classe, et
     * un voyage ou un changement de réglage ne serait jamais vu.
     */
    fun current(): ZoneId = ZoneId.systemDefault()

    fun dayStart(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Minuit du lendemain: borne de fin **exclusive** de la journée. */
    fun dayEnd(date: LocalDate, zone: ZoneId): Long = dayStart(date.plusDays(1), zone)

    fun localDate(utc: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(utc).atZone(zone).toLocalDate()

    fun localDateTime(utc: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(utc).atZone(zone).toLocalDateTime()

    fun toUtc(dateTime: LocalDateTime, zone: ZoneId): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    /** Ancre d'une journée entière: minuit UTC, à ne jamais convertir. */
    fun allDayStart(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun allDayDate(utc: Long): LocalDate =
        Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()

    /** Jour civil d'un instant, selon l'ancrage de l'événement qui le porte. */
    fun dateOf(utc: Long, allDay: Boolean, zone: ZoneId): LocalDate =
        if (allDay) allDayDate(utc) else localDate(utc, zone)

    /**
     * Jours civils que l'événement touche, du premier au dernier.
     *
     * La fin étant exclusive, un événement qui s'arrête à minuit pile appartient à la veille et
     * ne doit pas marquer le lendemain: d'où le retrait d'une milliseconde avant de chercher le
     * dernier jour.
     */
    fun daysCovered(
        startUtc: Long,
        endUtc: Long,
        allDay: Boolean,
        zone: ZoneId
    ): List<LocalDate> {
        val first = dateOf(startUtc, allDay, zone)
        val last = dateOf((endUtc - 1).coerceAtLeast(startUtc), allDay, zone)
        if (!last.isAfter(first)) return listOf(first)

        val days = ArrayList<LocalDate>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            days.add(cursor)
            cursor = cursor.plusDays(1)
        }
        return days
    }
}
