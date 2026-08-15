package com.majdus.organisateur.agenda

import com.majdus.organisateur.data.Event
import com.majdus.organisateur.data.EventException
import com.majdus.organisateur.data.EventReminder
import java.time.ZoneId
import java.util.UUID

/**
 * Ce qu'il faut écrire pour scinder une série en deux.
 *
 * Le dépôt se contente d'appliquer ce résultat dans une transaction: aucune décision ne reste de
 * son côté. C'est ce découpage qui rend le chemin d'écriture le plus délicat de l'agenda
 * vérifiable sans base ni Android.
 */
data class SeriesSplit(
    /**
     * Série mère bornée juste avant la scission, ou `null` quand la scission tombe sur sa
     * première occurrence — il ne reste alors rien à conserver, et la mère est à supprimer.
     */
    val parent: Event?,
    /** Nouvelle série, reprenant la scission et tout ce qui la suit. */
    val tail: Event,
    val tailReminders: List<EventReminder>,
    /** Exceptions passées à la queue; leur ligne d'origine est à supprimer. */
    val movedExceptions: List<EventException>,
    /** Occurrences détachées à rattacher à la queue. */
    val movedChildren: List<String>
)

/**
 * Modification d'une série « à partir d'ici ».
 *
 * L'opération est une scission: la série d'origine est bornée à la veille de l'occurrence
 * choisie, et une nouvelle série reprend à partir d'elle. C'est ce que font tous les agendas, et
 * c'est la seule façon de ne pas réécrire le passé — un rendez-vous déjà tenu ne doit pas changer
 * d'heure parce qu'on a déplacé les suivants.
 *
 * Fonction pure: rien n'est lu ni écrit ici, et l'identifiant comme l'horloge sont fournis par
 * l'appelant pour que deux exécutions donnent le même résultat.
 */
object SeriesEditor {

    /**
     * Marge séparant la fin de la mère du début de la queue.
     *
     * `UNTIL` étant inclusif, il doit tomber strictement avant l'occurrence de scission, sans
     * quoi celle-ci appartiendrait aux deux séries et s'afficherait en double.
     */
    private const val UNTIL_MARGIN_MS = 1_000L

    fun split(
        series: Event,
        atStartUtc: Long,
        edited: EventDraft,
        exceptions: List<EventException>,
        children: List<Event>,
        zone: ZoneId,
        newId: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis()
    ): SeriesSplit {
        val rule = RecurrenceRule.parse(series.rrule)

        // Null quand la scission tombe sur la première occurrence: remplacer une série depuis son
        // début, c'est la remplacer tout court — il n'y a pas de passé à préserver.
        val parent = boundedBefore(series, atStartUtc, zone)

        val tailRule = tailRule(series, rule, edited, atStartUtc, zone)
        val tail = Event(
            id = newId,
            title = edited.title,
            startUtc = edited.startUtc,
            endUtc = edited.endUtc,
            allDay = edited.allDay,
            description = edited.description,
            location = edited.location,
            colorKey = edited.colorKey,
            rrule = tailRule?.encode().orEmpty(),
            seriesEndUtc = seriesEnd(edited, tailRule, zone),
            // La date de création se transmet: la queue prolonge la série, elle ne la remplace
            // pas. Seul l'ordre d'affichage s'appuierait dessus, et il doit rester stable.
            createdAt = series.createdAt,
            updatedAt = now
        )

        return SeriesSplit(
            parent = parent,
            tail = tail,
            tailReminders = edited.reminders.distinct().sorted().map { EventReminder(newId, it) },
            movedExceptions = exceptions
                .filter { it.eventId == series.id && it.originalStartUtc >= atStartUtc }
                .map { EventException(newId, it.originalStartUtc) },
            movedChildren = children
                .filter { it.parentId == series.id && it.originalStartUtc >= atStartUtc }
                .map { it.id }
        )
    }

    /**
     * La série arrêtée juste avant [atStartUtc], ou `null` s'il n'en reste rien.
     *
     * Sert aussi bien à la scission qu'à la suppression « à partir d'ici »: dans les deux cas, ce
     * qui précède doit rester intact et ce qui suit disparaître de cette série.
     */
    fun boundedBefore(series: Event, atStartUtc: Long, zone: ZoneId): Event? {
        val rule = RecurrenceRule.parse(series.rrule) ?: return null
        if (atStartUtc <= series.startUtc) return null

        // Un COUNT conservé changerait silencieusement le nombre d'occurrences restantes: il
        // devient un UNTIL, qui exprime la même limite sans dépendre de ce qui suit.
        val bounded = rule.copy(count = 0, until = atStartUtc - UNTIL_MARGIN_MS)
        val duration = series.endUtc - series.startUtc
        val seriesStart = Zones.localDateTime(series.startUtc, zone)
        return series.copy(
            rrule = bounded.encode(),
            seriesEndUtc = RecurrenceEngine.seriesEnd(seriesStart, bounded, zone, duration)
        )
    }

    /**
     * Règle de la queue.
     *
     * Quand la série gardait la même règle et qu'elle était comptée, la queue hérite du solde:
     * une série de dix occurrences scindée à la quatrième en laisse sept devant. Si l'utilisateur
     * a changé la règle, c'est la sienne qui fait foi, sans report.
     */
    private fun tailRule(
        series: Event,
        rule: RecurrenceRule?,
        edited: EventDraft,
        atStartUtc: Long,
        zone: ZoneId
    ): RecurrenceRule? {
        val editedRule = RecurrenceRule.parse(edited.rrule) ?: return null
        if (rule == null || edited.rrule != series.rrule || rule.count == 0) return editedRule

        val consumed = RecurrenceEngine.expand(
            seriesStart = Zones.localDateTime(series.startUtc, zone),
            rule = rule,
            zone = zone,
            from = series.startUtc,
            to = atStartUtc,
            limit = rule.count
        ).size
        return editedRule.copy(count = (rule.count - consumed).coerceAtLeast(1))
    }

    private fun seriesEnd(edited: EventDraft, rule: RecurrenceRule?, zone: ZoneId): Long =
        if (rule == null) {
            edited.endUtc
        } else {
            RecurrenceEngine.seriesEnd(
                Zones.localDateTime(edited.startUtc, zone), rule, zone, edited.durationMs
            )
        }
}
