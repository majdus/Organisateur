package com.majdus.organisateur.agenda

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Unité de répétition d'une série. */
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Un jour de la semaine, éventuellement situé dans le mois.
 *
 * [position] vaut 0 pour « tous les jeudis », 3 pour « le troisième jeudi », -1 pour « le
 * dernier vendredi ». C'est le `3TH` / `-1FR` d'iCalendar.
 */
data class WeekdayNum(val position: Int, val day: DayOfWeek)

/**
 * Règle de répétition, sous-ensemble de la RRULE d'iCalendar (RFC 5545).
 *
 * Le format de stockage est la chaîne RRULE elle-même, et non une structure maison: c'est
 * exactement ce que `CalendarContract` expose dans `Events.RRULE`, donc le jour où l'agenda lira
 * les calendriers du téléphone, ce sera une copie et pas une traduction.
 *
 * Sont pris en charge: `FREQ`, `INTERVAL`, `BYDAY`, `BYMONTHDAY`, `COUNT`, `UNTIL`, et `WKST=MO`
 * qui est figé. Tout le reste — `BYSETPOS`, `BYMONTH`, `RDATE`… — fait échouer la lecture plutôt
 * que d'être ignoré en silence: mieux vaut afficher un événement unique qu'une série fausse.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    /** Nombre de périodes entre deux occurrences, au moins 1. */
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    /** Quantièmes, négatifs comptés depuis la fin du mois. Jamais 0. */
    val byMonthDay: List<Int> = emptyList(),
    /** Nombre total d'occurrences, 0 pour illimité. Exclusif avec [until]. */
    val count: Int = 0,
    /** Dernier instant admis, **inclusif**; 0 quand la série n'a pas de terme. */
    val until: Long = 0
) {

    /** Chaîne RRULE canonique, relisible par [parse]. */
    fun encode(): String = buildString {
        append("FREQ=").append(frequency.name)
        if (interval != 1) append(";INTERVAL=").append(interval)
        if (byDay.isNotEmpty()) {
            append(";BYDAY=").append(byDay.joinToString(",") { encodeWeekday(it) })
        }
        if (byMonthDay.isNotEmpty()) {
            append(";BYMONTHDAY=").append(byMonthDay.joinToString(","))
        }
        if (count != 0) append(";COUNT=").append(count)
        if (until != 0L) append(";UNTIL=").append(encodeUntil(until))
    }

    companion object {
        private val UNTIL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        private val DAY_CODES = mapOf(
            "MO" to DayOfWeek.MONDAY,
            "TU" to DayOfWeek.TUESDAY,
            "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY,
            "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY
        )
        private val DAY_NAMES = DAY_CODES.entries.associate { (code, day) -> day to code }

        private val BYDAY_PATTERN = Regex("^([+-]?\\d{1,2})?([A-Z]{2})$")

        /**
         * Lit une chaîne RRULE, ou rend `null` si elle est vide, malformée, ou emploie une partie
         * hors du sous-ensemble retenu.
         *
         * L'appelant traite alors l'événement comme unique. C'est la même dégradation que pour
         * une couleur de note inconnue: la ligne reste visible, elle perd seulement ce qu'on ne
         * sait pas relire.
         */
        fun parse(rrule: String): RecurrenceRule? {
            if (rrule.isBlank()) return null

            var frequency: Frequency? = null
            var interval = 1
            var byDay = emptyList<WeekdayNum>()
            var byMonthDay = emptyList<Int>()
            var count = 0
            var until = 0L

            for (part in rrule.trim().split(';')) {
                if (part.isEmpty()) continue
                val separator = part.indexOf('=')
                if (separator <= 0) return null
                val name = part.substring(0, separator).uppercase()
                val value = part.substring(separator + 1)
                if (value.isEmpty()) return null

                when (name) {
                    "FREQ" -> frequency =
                        runCatching { Frequency.valueOf(value.uppercase()) }.getOrNull()
                            ?: return null
                    "INTERVAL" -> interval = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null
                    "COUNT" -> count = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null
                    "UNTIL" -> until = parseUntil(value) ?: return null
                    "BYDAY" -> byDay = parseByDay(value) ?: return null
                    "BYMONTHDAY" -> byMonthDay = parseByMonthDay(value) ?: return null
                    // Figé: tout le rendu suppose une semaine qui commence le lundi.
                    "WKST" -> if (value.uppercase() != "MO") return null
                    else -> return null
                }
            }

            val freq = frequency ?: return null
            // La RFC les déclare exclusifs, et une série qui porterait les deux n'aurait pas de
            // fin univoque.
            if (count != 0 && until != 0L) return null
            // Les motifs annuels autres que « même jour, même mois » demanderaient BYMONTH pour
            // être exprimés sans ambiguïté: hors périmètre, donc refusés plutôt que devinés.
            if (freq == Frequency.YEARLY && (byDay.isNotEmpty() || byMonthDay.isNotEmpty())) {
                return null
            }
            // Un quantième n'a de sens que dans un mois.
            if (byMonthDay.isNotEmpty() && freq != Frequency.MONTHLY) return null
            // Une position ne se compte que dans un mois: « le 3e jeudi » d'une semaine ne veut
            // rien dire.
            if (freq != Frequency.MONTHLY && byDay.any { it.position != 0 }) return null

            return RecurrenceRule(freq, interval, byDay, byMonthDay, count, until)
        }

        private fun parseByDay(value: String): List<WeekdayNum>? {
            val days = value.split(',').map { entry ->
                val match = BYDAY_PATTERN.matchEntire(entry.trim().uppercase()) ?: return null
                val position = match.groupValues[1].let {
                    if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null
                }
                if (position !in -53..53) return null
                val day = DAY_CODES[match.groupValues[2]] ?: return null
                WeekdayNum(position, day)
            }
            return days.ifEmpty { null }
        }

        private fun parseByMonthDay(value: String): List<Int>? {
            val days = value.split(',').map { entry ->
                val day = entry.trim().toIntOrNull() ?: return null
                if (day == 0 || day < -31 || day > 31) return null
                day
            }
            return days.ifEmpty { null }
        }

        private fun parseUntil(value: String): Long? = runCatching {
            java.time.LocalDateTime.parse(value.uppercase(), UNTIL_FORMAT)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()

        private fun encodeUntil(until: Long): String =
            UNTIL_FORMAT.format(Instant.ofEpochMilli(until).atZone(ZoneOffset.UTC))

        private fun encodeWeekday(weekday: WeekdayNum): String {
            val code = DAY_NAMES[weekday.day].orEmpty()
            return if (weekday.position == 0) code else "${weekday.position}$code"
        }
    }
}
