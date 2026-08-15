package com.majdus.organisateur.data

import androidx.room.withTransaction
import com.majdus.organisateur.agenda.EventDraft
import com.majdus.organisateur.agenda.Occurrence
import com.majdus.organisateur.agenda.RecurrenceEngine
import com.majdus.organisateur.agenda.RecurrenceRule
import com.majdus.organisateur.agenda.SeriesEditor
import com.majdus.organisateur.agenda.Zones
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Ce qu'une modification touche d'une série. */
enum class EditScope {
    /** Cette occurrence seulement: elle se détache de la série. */
    THIS_ONE,

    /** Cette occurrence et les suivantes: la série est scindée en deux. */
    THIS_AND_FOLLOWING,

    /** Toute la série, passé compris. */
    WHOLE_SERIES
}

/**
 * Lecture et écriture de l'agenda.
 *
 * Sort le savoir calendaire de l'écran, qui doit rester un orchestrateur: c'est ici que les
 * séries se déplient, que les exceptions s'appliquent et que les portées d'édition se traduisent
 * en écritures. Rien d'autre n'est introduit — méthodes `suspend`, appelées depuis
 * `lifecycleScope`, exactement comme les DAO l'étaient.
 *
 * Les quatre vues de l'agenda ne diffèrent que par la plage qu'elles demandent à [occurrences].
 */
class EventRepository(private val db: AppDatabase) {

    /**
     * Occurrences touchant `[startUtc, endUtc)`, séries dépliées et exceptions retirées.
     *
     * Deux requêtes, quelle que soit la taille de la plage: les candidats, puis la table des
     * exceptions — qui se compte en dizaines de lignes et coûte moins cher lue d'un bloc qu'en
     * jointure par journée affichée.
     */
    suspend fun occurrences(
        startUtc: Long,
        endUtc: Long,
        zone: ZoneId = Zones.current()
    ): List<Occurrence> {
        // Fenêtre élargie d'un jour de chaque côté: les journées entières sont ancrées en UTC et
        // les autres dans le fuseau local, donc les deux familles ne se comparent pas au millième
        // près en SQL. Le tri fin se fait plus bas, une fois l'ancrage résolu.
        val candidates = db.eventDao().candidates(startUtc - DAY_MS, endUtc + DAY_MS)
        if (candidates.isEmpty()) return emptyList()

        val exceptions = db.eventExceptionDao().all()
            .groupBy({ it.eventId }, { it.originalStartUtc })
            .mapValues { (_, starts) -> starts.toHashSet() }

        val found = ArrayList<Occurrence>()
        for (event in candidates) {
            val rule = RecurrenceRule.parse(event.rrule)
            if (rule == null) {
                // Événement unique, occurrence détachée, ou série dont la règle n'est plus
                // lisible: dans les trois cas, une seule occurrence, à ses propres instants.
                found.add(occurrenceOf(event, event.startUtc, zone))
                continue
            }
            // Une série de journées entières se compte en jours UTC, comme son ancrage.
            val ruleZone = if (event.allDay) ZoneOffset.UTC else zone
            val skipped = exceptions[event.id].orEmpty()
            val starts = RecurrenceEngine.expand(
                seriesStart = Zones.localDateTime(event.startUtc, ruleZone),
                rule = rule,
                zone = ruleZone,
                from = startUtc - DAY_MS,
                to = endUtc + DAY_MS,
                durationMs = event.endUtc - event.startUtc
            )
            for (start in starts) {
                val at = Zones.toUtc(start, ruleZone)
                if (at in skipped) continue
                found.add(occurrenceOf(event, at, zone))
            }
        }

        return found
            .filter { it.overlaps(startUtc, endUtc) }
            // Les journées entières en tête: c'est le bandeau, au-dessus de la grille horaire.
            .sortedWith(
                compareByDescending<Occurrence> { it.allDay }
                    .thenBy { it.startUtc }
                    .thenBy { it.title }
            )
    }

    /**
     * Couleurs des occurrences de chaque jour, en clés "yyyy-MM-dd", pour les pastilles du mois.
     *
     * Les couleurs plutôt qu'un simple booléen: sur une grille compacte, c'est ce qui distingue
     * une journée de travail d'une journée personnelle sans avoir à l'ouvrir. Elles gardent
     * l'ordre chronologique de la journée.
     */
    suspend fun dayColors(
        startUtc: Long,
        endUtc: Long,
        zone: ZoneId = Zones.current()
    ): Map<String, List<String>> {
        val byDay = HashMap<String, MutableList<String>>()
        for (occurrence in occurrences(startUtc, endUtc, zone)) {
            // Les instants d'une occurrence sont déjà ramenés en local, journées entières
            // comprises.
            val days = Zones.daysCovered(
                occurrence.startUtc, occurrence.endUtc, allDay = false, zone = zone
            )
            for (day in days) {
                byDay.getOrPut(day.toString()) { ArrayList() }.add(occurrence.colorKey)
            }
        }
        return byDay
    }

    /** Occurrences dont l'intitulé, le lieu ou les notes contiennent [query]. */
    suspend fun search(
        query: String,
        startUtc: Long,
        endUtc: Long,
        zone: ZoneId = Zones.current()
    ): List<Occurrence> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        // La recherche porte sur les occurrences et non sur les lignes: une série trouvée doit
        // rendre ses dates, pas son seul point de départ.
        val matching = db.eventDao().matching("%$needle%").mapTo(HashSet()) { it.id }
        if (matching.isEmpty()) return emptyList()
        return occurrences(startUtc, endUtc, zone).filter { it.eventId in matching }
    }

    suspend fun countIn(startUtc: Long, endUtc: Long, zone: ZoneId = Zones.current()): Int =
        occurrences(startUtc, endUtc, zone).size

    suspend fun remindersOf(eventId: String): List<Int> =
        db.eventReminderDao().forEvent(eventId).map { it.minutesBefore }

    suspend fun eventsWithReminder(): Set<String> =
        db.eventReminderDao().eventIdsWithReminder().toHashSet()

    suspend fun byId(id: String): Event? = db.eventDao().byId(id)

    /** Rend l'événement écrit: l'appelant en a besoin pour programmer son rappel. */
    suspend fun create(draft: EventDraft, zone: ZoneId = Zones.current()): Event {
        val event = newEvent(draft, UUID.randomUUID().toString(), zone)
        db.withTransaction {
            db.eventDao().insert(event)
            writeReminders(event.id, draft.reminders)
        }
        return event
    }

    /**
     * Remet un événement tel qu'il était: c'est le « Annuler » du bandeau.
     *
     * Insertion ou mise à jour selon que la ligne a disparu ou seulement été bornée — supprimer
     * « à partir d'ici » laisse la série en place, raccourcie.
     */
    suspend fun restore(event: Event, reminders: List<Int>) {
        db.withTransaction {
            if (db.eventDao().byId(event.id) == null) {
                db.eventDao().insert(event)
            } else {
                db.eventDao().update(event)
            }
            writeReminders(event.id, reminders)
        }
    }

    /**
     * Rend la ligne portant désormais l'occurrence modifiée, ou `null` si la cible a disparu.
     *
     * L'occurrence est désignée par sa série et son début d'origine: c'est son identité, et la
     * seule chose dont l'écriture ait besoin.
     */
    suspend fun update(
        eventId: String,
        occurrenceStartUtc: Long,
        draft: EventDraft,
        scope: EditScope,
        zone: ZoneId = Zones.current()
    ): Event? {
        val series = db.eventDao().byId(eventId) ?: return null
        val isSeries = RecurrenceRule.parse(series.rrule) != null

        return when {
            // Un événement unique — ou une occurrence déjà détachée — n'a pas de portée: il n'y a
            // qu'une chose à modifier.
            !isSeries || scope == EditScope.WHOLE_SERIES -> updateWhole(series, draft, zone)
            scope == EditScope.THIS_ONE -> detach(series, occurrenceStartUtc, draft, zone)
            else -> splitSeries(series, occurrenceStartUtc, draft, zone)
        }
    }

    /**
     * Rend `true` quand la suppression s'est faite en posant une exception.
     *
     * L'annulation en dépend: une exception se retire, tandis qu'une ligne effacée ou raccourcie
     * se réécrit. L'appelant ne peut pas le déduire de la portée seule — supprimer « cette
     * occurrence » d'un événement unique le supprime tout entier.
     */
    suspend fun delete(
        eventId: String,
        occurrenceStartUtc: Long,
        scope: EditScope,
        zone: ZoneId = Zones.current()
    ): Boolean {
        val series = db.eventDao().byId(eventId) ?: return false
        val isSeries = RecurrenceRule.parse(series.rrule) != null

        when {
            !isSeries || scope == EditScope.WHOLE_SERIES -> db.eventDao().delete(series)

            scope == EditScope.THIS_ONE -> {
                db.eventExceptionDao().insert(EventException(series.id, occurrenceStartUtc))
                return true
            }

            else -> {
                val bounded = SeriesEditor.boundedBefore(series, occurrenceStartUtc, zone)
                if (bounded == null) db.eventDao().delete(series) else db.eventDao().update(bounded)
            }
        }
        return false
    }

    /** Remet une occurrence retirée dans sa série. */
    suspend fun restoreOccurrence(eventId: String, occurrenceStartUtc: Long) {
        db.eventExceptionDao().delete(EventException(eventId, occurrenceStartUtc))
    }

    private suspend fun updateWhole(series: Event, draft: EventDraft, zone: ZoneId): Event {
        val rule = RecurrenceRule.parse(draft.rrule)
        val start = rebasedStart(series, draft, zone)
        val end = start + draft.durationMs
        val updated = series.copy(
            title = draft.title,
            startUtc = start,
            endUtc = end,
            allDay = draft.allDay,
            description = draft.description,
            location = draft.location,
            colorKey = draft.colorKey,
            rrule = rule?.encode().orEmpty(),
            seriesEndUtc = seriesEnd(start, end, draft.allDay, rule, zone),
            updatedAt = System.currentTimeMillis()
        )
        db.withTransaction {
            db.eventDao().update(updated)
            writeReminders(updated.id, draft.reminders)
        }
        return updated
    }

    /**
     * Début à retenir quand la modification vaut pour toute la série.
     *
     * Modifier l'occurrence du 20 pour toute la série ne déplace pas le début de la série au 20:
     * seule l'heure du jour est reportée. Changer le jour d'une série reviendrait à en écrire une
     * autre, ce que la portée « à partir d'ici » exprime déjà.
     */
    private fun rebasedStart(series: Event, draft: EventDraft, zone: ZoneId): Long = when {
        series.rrule.isEmpty() -> draft.startUtc
        // Une journée entière n'a pas d'heure à reporter.
        draft.allDay -> series.startUtc
        else -> {
            val time = Zones.localDateTime(draft.startUtc, zone).toLocalTime()
            Zones.toUtc(Zones.localDate(series.startUtc, zone).atTime(time), zone)
        }
    }

    private suspend fun detach(
        series: Event,
        occurrenceStartUtc: Long,
        draft: EventDraft,
        zone: ZoneId
    ): Event {
        val child = newEvent(
            draft.copy(rrule = ""),
            UUID.randomUUID().toString(),
            zone
        ).copy(
            parentId = series.id,
            originalStartUtc = occurrenceStartUtc,
            createdAt = series.createdAt
        )
        db.withTransaction {
            // Les deux écritures vont ensemble: l'exception seule perdrait l'occurrence, la ligne
            // détachée seule la ferait apparaître en double.
            db.eventExceptionDao().insert(EventException(series.id, occurrenceStartUtc))
            db.eventDao().insert(child)
            writeReminders(child.id, draft.reminders)
        }
        return child
    }

    private suspend fun splitSeries(
        series: Event,
        occurrenceStartUtc: Long,
        draft: EventDraft,
        zone: ZoneId
    ): Event {
        val split = SeriesEditor.split(
            series = series,
            atStartUtc = occurrenceStartUtc,
            edited = draft,
            exceptions = db.eventExceptionDao().all(),
            children = db.eventDao().childrenOf(series.id),
            zone = zone
        )
        db.withTransaction {
            db.eventDao().insert(split.tail)
            db.eventReminderDao().insertAll(split.tailReminders)
            for (moved in split.movedExceptions) {
                db.eventExceptionDao().delete(EventException(series.id, moved.originalStartUtc))
                db.eventExceptionDao().insert(moved)
            }
            for (childId in split.movedChildren) {
                db.eventDao().reparent(childId, split.tail.id)
            }
            // En dernier: supprimer la mère emporte en cascade ce qui lui reste attaché, et ce
            // qui devait survivre a déjà été déplacé.
            if (split.parent == null) {
                db.eventDao().delete(series)
            } else {
                db.eventDao().update(split.parent)
            }
        }
        return split.tail
    }

    private fun newEvent(draft: EventDraft, id: String, zone: ZoneId): Event {
        val rule = RecurrenceRule.parse(draft.rrule)
        return Event(
            id = id,
            title = draft.title,
            startUtc = draft.startUtc,
            endUtc = draft.endUtc,
            allDay = draft.allDay,
            description = draft.description,
            location = draft.location,
            colorKey = draft.colorKey,
            rrule = rule?.encode().orEmpty(),
            seriesEndUtc = seriesEnd(draft.startUtc, draft.endUtc, draft.allDay, rule, zone)
        )
    }

    private fun seriesEnd(
        startUtc: Long,
        endUtc: Long,
        allDay: Boolean,
        rule: RecurrenceRule?,
        zone: ZoneId
    ): Long {
        if (rule == null) return endUtc
        val ruleZone = if (allDay) ZoneOffset.UTC else zone
        return RecurrenceEngine.seriesEnd(
            Zones.localDateTime(startUtc, ruleZone), rule, ruleZone, endUtc - startUtc
        )
    }

    private suspend fun writeReminders(eventId: String, minutes: List<Int>) {
        db.eventReminderDao().deleteForEvent(eventId)
        if (minutes.isNotEmpty()) {
            db.eventReminderDao()
                .insertAll(minutes.distinct().sorted().map { EventReminder(eventId, it) })
        }
    }

    /** Occurrence d'affichage: l'ancrage est résolu ici, les vues n'ont plus à le connaître. */
    private fun occurrenceOf(event: Event, occurrenceStartUtc: Long, zone: ZoneId): Occurrence {
        val duration = event.endUtc - event.startUtc
        val start: Long
        val end: Long
        if (event.allDay) {
            // Ramenée à minuit local: une journée entière occupe la journée qu'on regarde, pas
            // les heures qu'un décalage UTC lui donnerait.
            val first = Zones.allDayDate(occurrenceStartUtc)
            val days = (duration / DAY_MS).coerceAtLeast(1)
            start = Zones.dayStart(first, zone)
            end = Zones.dayStart(first.plusDays(days), zone)
        } else {
            start = occurrenceStartUtc
            end = occurrenceStartUtc + duration
        }
        return Occurrence(
            eventId = event.id,
            occurrenceStartUtc = occurrenceStartUtc,
            startUtc = start,
            endUtc = end,
            allDay = event.allDay,
            title = event.title,
            location = event.location,
            colorKey = event.colorKey,
            isRecurring = event.rrule.isNotEmpty(),
            isDetached = event.parentId.isNotEmpty()
        )
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
