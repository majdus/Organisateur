package com.majdus.organisateur.agenda

import com.majdus.organisateur.data.Event
import com.majdus.organisateur.data.EventException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * La scission « à partir d'ici ».
 *
 * Vérifiable sans base ni Android uniquement parce que [SeriesEditor.split] est une fonction
 * pure — c'est la raison d'être de ce découpage.
 */
class SeriesEditorTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val uneHeure = 3_600_000L

    private fun at(date: String, hour: Int = 9): LocalDateTime =
        LocalDate.parse(date).atTime(hour, 0)

    private fun utc(date: String, hour: Int = 9): Long = Zones.toUtc(at(date, hour), paris)

    private fun serie(
        start: String = "2026-08-03",
        rrule: String = "FREQ=WEEKLY"
    ) = Event(
        id = "serie",
        title = "Réunion",
        startUtc = utc(start),
        endUtc = utc(start) + uneHeure,
        rrule = rrule,
        seriesEndUtc = Event.SERIES_FOREVER,
        createdAt = 1_000L
    )

    private fun brouillon(
        start: String,
        title: String = "Réunion",
        rrule: String = "FREQ=WEEKLY",
        reminders: List<Int> = emptyList()
    ) = EventDraft(
        title = title,
        startUtc = utc(start),
        endUtc = utc(start) + uneHeure,
        rrule = rrule,
        reminders = reminders
    )

    private fun split(
        series: Event,
        at: String,
        draft: EventDraft,
        exceptions: List<EventException> = emptyList(),
        children: List<Event> = emptyList()
    ) = SeriesEditor.split(
        series = series,
        atStartUtc = utc(at),
        edited = draft,
        exceptions = exceptions,
        children = children,
        zone = paris,
        newId = "queue",
        now = 5_000L
    )

    @Test
    fun `la mere s'arrete juste avant la scission`() {
        val split = split(serie(), "2026-08-24", brouillon("2026-08-24", title = "Réunion longue"))

        val parent = split.parent!!
        val rule = RecurrenceRule.parse(parent.rrule)!!
        assertTrue("la mère doit être bornée", rule.until != 0L)
        assertTrue("la borne doit précéder la scission", rule.until < utc("2026-08-24"))

        // Le passé n'a pas bougé: c'est tout l'intérêt de scinder plutôt que de réécrire.
        assertEquals(serie().startUtc, parent.startUtc)
        assertEquals("Réunion", parent.title)
    }

    @Test
    fun `l'occurrence de scission appartient a la queue et non a la mere`() {
        val split = split(serie(), "2026-08-24", brouillon("2026-08-24"))
        val parentRule = RecurrenceRule.parse(split.parent!!.rrule)!!

        val restantes = RecurrenceEngine.expand(
            seriesStart = Zones.localDateTime(split.parent!!.startUtc, paris),
            rule = parentRule,
            zone = paris,
            from = utc("2026-08-03", 0),
            to = utc("2026-12-31", 0)
        ).map { it.toLocalDate().toString() }

        assertEquals(listOf("2026-08-03", "2026-08-10", "2026-08-17"), restantes)
        assertEquals(utc("2026-08-24"), split.tail.startUtc)
    }

    @Test
    fun `scinder a la premiere occurrence remplace la serie entiere`() {
        // Il n'y a pas de passé à préserver: la mère est à supprimer, pas à borner.
        val split = split(serie(), "2026-08-03", brouillon("2026-08-03", title = "Autre"))
        assertNull(split.parent)
        assertEquals("Autre", split.tail.title)
    }

    @Test
    fun `un COUNT devient un UNTIL sur la mere`() {
        // Conserver le COUNT laisserait la mère produire ses dix occurrences malgré la borne.
        val split = split(serie(rrule = "FREQ=WEEKLY;COUNT=10"), "2026-08-24", brouillon("2026-08-24"))
        val parentRule = RecurrenceRule.parse(split.parent!!.rrule)!!
        assertEquals(0, parentRule.count)
        assertTrue(parentRule.until != 0L)
    }

    @Test
    fun `la queue herite du solde d'occurrences`() {
        // Dix occurrences, scindées à la quatrième: trois derrière, sept devant.
        val split = split(
            serie(rrule = "FREQ=WEEKLY;COUNT=10"),
            "2026-08-24",
            brouillon("2026-08-24", rrule = "FREQ=WEEKLY;COUNT=10")
        )
        assertEquals(7, RecurrenceRule.parse(split.tail.rrule)!!.count)
    }

    @Test
    fun `une regle changee par l'utilisateur fait foi sans report`() {
        val split = split(
            serie(rrule = "FREQ=WEEKLY;COUNT=10"),
            "2026-08-24",
            brouillon("2026-08-24", rrule = "FREQ=DAILY;COUNT=3")
        )
        val tailRule = RecurrenceRule.parse(split.tail.rrule)!!
        assertEquals(Frequency.DAILY, tailRule.frequency)
        assertEquals(3, tailRule.count)
    }

    @Test
    fun `une queue sans repetition devient un evenement unique`() {
        val split = split(serie(), "2026-08-24", brouillon("2026-08-24", rrule = ""))
        assertEquals("", split.tail.rrule)
        assertEquals(split.tail.endUtc, split.tail.seriesEndUtc)
    }

    @Test
    fun `les exceptions se repartissent de part et d'autre de la scission`() {
        val exceptions = listOf(
            EventException("serie", utc("2026-08-10")),
            EventException("serie", utc("2026-08-31")),
            // Celle d'une autre série ne doit pas être emportée.
            EventException("autre", utc("2026-08-31"))
        )
        val split = split(serie(), "2026-08-24", brouillon("2026-08-24"), exceptions = exceptions)

        assertEquals(
            listOf(EventException("queue", utc("2026-08-31"))),
            split.movedExceptions
        )
    }

    @Test
    fun `les occurrences detachees suivantes sont rattachees a la queue`() {
        val children = listOf(
            Event(
                id = "avant", title = "Décalée", startUtc = utc("2026-08-10", 11),
                endUtc = utc("2026-08-10", 12), parentId = "serie",
                originalStartUtc = utc("2026-08-10")
            ),
            Event(
                id = "apres", title = "Décalée", startUtc = utc("2026-08-31", 11),
                endUtc = utc("2026-08-31", 12), parentId = "serie",
                originalStartUtc = utc("2026-08-31")
            )
        )
        val split = split(serie(), "2026-08-24", brouillon("2026-08-24"), children = children)
        assertEquals(listOf("apres"), split.movedChildren)
    }

    @Test
    fun `les rappels du brouillon sont poses sur la queue`() {
        val split = split(
            serie(), "2026-08-24",
            brouillon("2026-08-24", reminders = listOf(10, 0, 10))
        )
        assertEquals(listOf(0, 10), split.tailReminders.map { it.minutesBefore })
        assertTrue(split.tailReminders.all { it.eventId == "queue" })
    }

    @Test
    fun `la queue garde la date de creation de la serie`() {
        // La queue prolonge la série, elle ne la remplace pas: l'ordre d'affichage reste stable.
        val split = split(serie(), "2026-08-24", brouillon("2026-08-24"))
        assertEquals(1_000L, split.tail.createdAt)
        assertEquals(5_000L, split.tail.updatedAt)
    }

    @Test
    fun `la serie bornee ne produit plus rien au-dela de la coupure`() {
        val bornee = SeriesEditor.boundedBefore(serie(), utc("2026-08-24"), paris)!!

        // La série cesse d'être candidate à toutes les plages: c'est ce que le filtre SQL lit.
        assertTrue(bornee.seriesEndUtc < Event.SERIES_FOREVER)

        val apres = RecurrenceEngine.expand(
            seriesStart = Zones.localDateTime(bornee.startUtc, paris),
            rule = RecurrenceRule.parse(bornee.rrule)!!,
            zone = paris,
            from = utc("2026-08-24", 0),
            to = utc("2026-12-31", 0)
        )
        assertEquals(emptyList<LocalDateTime>(), apres)
    }

    @Test
    fun `un evenement sans repetition n'a rien a borner`() {
        val unique = serie(rrule = "")
        assertNull(SeriesEditor.boundedBefore(unique, utc("2026-08-24"), paris))
    }
}
