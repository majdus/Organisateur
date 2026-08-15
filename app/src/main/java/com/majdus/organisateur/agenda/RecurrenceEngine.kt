package com.majdus.organisateur.agenda

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Dépliage d'une série en occurrences.
 *
 * Tout se calcule en **heure murale locale**, et la conversion en instant n'a lieu qu'à la toute
 * fin. C'est la décision qui gouverne ce fichier: « tous les jours à 9 h » ajoute *un jour civil*
 * et non 86 400 000 millisecondes — à la nuit du changement d'heure, la seconde formule ferait
 * glisser le rendez-vous à 8 h.
 *
 * Aucun accès au fuseau de la machine: il est toujours passé en paramètre, donc une expansion
 * donne le même résultat partout et se vérifie hors d'Android.
 */
object RecurrenceEngine {

    /** Garde-fou: aucune expansion ne peut tourner indéfiniment sur le fil principal. */
    private const val MAX_EMPTY_PERIODS = 500

    /**
     * Débuts des occurrences dont l'étendue `[début, début + durationMs)` touche `[from, to)`.
     *
     * [durationMs] permet de retrouver une occurrence commencée avant la fenêtre et qui déborde
     * dedans — sans lui, un rendez-vous de trois jours disparaîtrait de ses deux derniers.
     */
    fun expand(
        seriesStart: LocalDateTime,
        rule: RecurrenceRule,
        zone: ZoneId,
        from: Long,
        to: Long,
        durationMs: Long = 0,
        limit: Int = 1_000
    ): List<LocalDateTime> {
        if (to <= from) return emptyList()
        val earliestStart = from - durationMs
        val found = ArrayList<LocalDateTime>()
        for (start in starts(seriesStart, rule, zone, earliestStart)) {
            val instant = Zones.toUtc(start, zone)
            if (instant >= to) break
            // `instant >= from` n'est pas redondant: une occurrence de durée nulle a une étendue
            // vide, que le seul test de chevauchement écarterait même posée sur la borne. C'est
            // ce cas qui sert à compter les occurrences d'une série jusqu'à une date.
            if (instant >= from || instant + durationMs > from) {
                found.add(start)
                if (found.size >= limit) break
            }
        }
        return found
    }

    /** Première occurrence commençant à [notBefore] ou après; `null` si la série est épuisée. */
    fun next(
        seriesStart: LocalDateTime,
        rule: RecurrenceRule,
        zone: ZoneId,
        notBefore: Long
    ): LocalDateTime? = starts(seriesStart, rule, zone, notBefore)
        .firstOrNull { Zones.toUtc(it, zone) >= notBefore }

    /**
     * Fin de la dernière occurrence, ou [Long.MAX_VALUE] pour une série sans terme.
     *
     * Sur un `UNTIL`, la valeur rendue est un majorant plutôt que la fin exacte: c'est ce que
     * consomme le filtre grossier du DAO, et un majorant ne peut produire qu'un candidat de trop,
     * jamais un événement manquant.
     */
    fun seriesEnd(
        seriesStart: LocalDateTime,
        rule: RecurrenceRule,
        zone: ZoneId,
        durationMs: Long
    ): Long = when {
        rule.until != 0L -> rule.until + durationMs
        rule.count != 0 -> {
            val last = starts(seriesStart, rule, zone, null).lastOrNull() ?: seriesStart
            Zones.toUtc(last, zone) + durationMs
        }
        else -> Long.MAX_VALUE
    }

    /**
     * Débuts locaux successifs de la série, dans l'ordre.
     *
     * [fastForwardTo] permet de sauter les périodes entières antérieures sans les parcourir: une
     * série quotidienne née il y a quinze ans se lit en quelques itérations au lieu de plusieurs
     * milliers. Le saut est refusé dès qu'un `COUNT` est en jeu — il faudrait alors savoir
     * combien d'occurrences ont été enjambées, et une erreur de compte décalerait toute la fin de
     * la série. Une série à `COUNT` est de toute façon bornée, donc l'itération complète l'est
     * aussi.
     */
    private fun starts(
        seriesStart: LocalDateTime,
        rule: RecurrenceRule,
        zone: ZoneId,
        fastForwardTo: Long?
    ): Sequence<LocalDateTime> = sequence {
        val time = seriesStart.toLocalTime()
        val firstDate = seriesStart.toLocalDate()
        var period = periodStart(firstDate, rule.frequency)

        if (fastForwardTo != null && rule.count == 0) {
            val target = periodStart(Zones.localDate(fastForwardTo, zone), rule.frequency)
            val elapsed = periodsBetween(period, target, rule.frequency)
            // Une période de marge: le saut vise la période *précédant* la cible, ce qui met à
            // l'abri des effets de bord aux jointures de semaine ou de mois pour une itération.
            val skipped = (elapsed / rule.interval - 1) * rule.interval
            if (skipped > 0) period = plusPeriods(period, rule.frequency, skipped)
        }

        var produced = 0
        var emptyPeriods = 0
        while (true) {
            val dates = datesIn(period, rule, firstDate)
            if (dates.isEmpty()) {
                // Un motif peut sauter des périodes entières — « le 5e jeudi » n'existe pas tous
                // les mois. Mais un motif qui n'en produirait jamais boucler sans fin.
                if (++emptyPeriods > MAX_EMPTY_PERIODS) return@sequence
            } else {
                emptyPeriods = 0
            }

            for (date in dates) {
                if (date < firstDate) continue
                val start = date.atTime(time)
                if (rule.until != 0L && Zones.toUtc(start, zone) > rule.until) return@sequence
                yield(start)
                produced++
                if (rule.count != 0 && produced >= rule.count) return@sequence
            }
            period = plusPeriods(period, rule.frequency, rule.interval.toLong())
        }
    }

    /** Dates candidates de la période, triées; vide quand le motif n'y tombe pas. */
    private fun datesIn(
        period: LocalDate,
        rule: RecurrenceRule,
        seriesDate: LocalDate
    ): List<LocalDate> = when (rule.frequency) {
        Frequency.DAILY ->
            if (rule.byDay.isEmpty() || rule.byDay.any { it.day == period.dayOfWeek }) {
                listOf(period)
            } else {
                emptyList()
            }

        // `period` est le lundi de la semaine: les jours listés s'y comptent en clair.
        Frequency.WEEKLY ->
            if (rule.byDay.isEmpty()) {
                listOf(period.plusDays((seriesDate.dayOfWeek.value - 1).toLong()))
            } else {
                rule.byDay
                    .map { period.plusDays((it.day.value - 1).toLong()) }
                    .distinct()
                    .sorted()
            }

        Frequency.MONTHLY -> when {
            rule.byMonthDay.isNotEmpty() ->
                rule.byMonthDay.mapNotNull { monthDay(period, it) }.distinct().sorted()
            rule.byDay.isNotEmpty() ->
                rule.byDay.flatMap { weekdaysInMonth(period, it) }.distinct().sorted()
            // Sans motif, le quantième de la série. La RFC demande de *sauter* les mois trop
            // courts et non de rogner: un rendez-vous du 31 n'a pas lieu en février.
            else -> listOfNotNull(monthDay(period, seriesDate.dayOfMonth))
        }

        // Même mois et même quantième que la série. Un 29 février saute les années communes,
        // pour la même raison qu'un 31 saute février.
        Frequency.YEARLY -> listOfNotNull(
            runCatching {
                LocalDate.of(period.year, seriesDate.month, seriesDate.dayOfMonth)
            }.getOrNull()
        )
    }

    /** Quantième du mois, négatif compté depuis la fin; `null` si le mois est trop court. */
    private fun monthDay(period: LocalDate, day: Int): LocalDate? {
        val length = period.lengthOfMonth()
        val resolved = if (day > 0) day else length + day + 1
        return if (resolved in 1..length) period.withDayOfMonth(resolved) else null
    }

    private fun weekdaysInMonth(period: LocalDate, weekday: WeekdayNum): List<LocalDate> {
        if (weekday.position == 0) {
            val first = period.with(TemporalAdjusters.firstInMonth(weekday.day))
            return generateSequence(first) { it.plusWeeks(1) }
                .takeWhile { it.month == period.month }
                .toList()
        }
        // `dayOfWeekInMonth` déborde silencieusement sur le mois suivant quand la position
        // demandée n'existe pas — un 5e jeudi, par exemple. D'où la vérification du mois.
        val candidate = period.with(TemporalAdjusters.dayOfWeekInMonth(weekday.position, weekday.day))
        return if (candidate.month == period.month && candidate.year == period.year) {
            listOf(candidate)
        } else {
            emptyList()
        }
    }

    private fun periodStart(date: LocalDate, frequency: Frequency): LocalDate = when (frequency) {
        Frequency.DAILY -> date
        Frequency.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        Frequency.MONTHLY -> date.withDayOfMonth(1)
        Frequency.YEARLY -> date.withDayOfYear(1)
    }

    private fun plusPeriods(date: LocalDate, frequency: Frequency, periods: Long): LocalDate =
        when (frequency) {
            Frequency.DAILY -> date.plusDays(periods)
            Frequency.WEEKLY -> date.plusWeeks(periods)
            Frequency.MONTHLY -> date.plusMonths(periods)
            Frequency.YEARLY -> date.plusYears(periods)
        }

    private fun periodsBetween(from: LocalDate, to: LocalDate, frequency: Frequency): Long =
        when (frequency) {
            Frequency.DAILY -> ChronoUnit.DAYS.between(from, to)
            Frequency.WEEKLY -> ChronoUnit.WEEKS.between(from, to)
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(from, to)
            Frequency.YEARLY -> ChronoUnit.YEARS.between(from, to)
        }
}
