package com.majdus.organisateur.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Le test qui protège du retour au `BETWEEN`.
 *
 * L'ancien stockage interrogeait la seule date de début, ce qui faisait disparaître un événement
 * de plusieurs jours de tous ses jours sauf le premier. Le prédicat de chevauchement le corrige,
 * mais il n'est juste que si ses deux bornes restent strictes.
 */
class OccurrenceRangeTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun occurrence(startUtc: Long, endUtc: Long, allDay: Boolean = false) = Occurrence(
        eventId = "e",
        occurrenceStartUtc = startUtc,
        startUtc = startUtc,
        endUtc = endUtc,
        allDay = allDay,
        title = "",
        location = "",
        colorKey = "default",
        isRecurring = false,
        isDetached = false
    )

    private fun day(date: String): Pair<Long, Long> {
        val local = LocalDate.parse(date)
        return Zones.dayStart(local, paris) to Zones.dayEnd(local, paris)
    }

    @Test
    fun `un evenement de la journee la touche`() {
        val (start, end) = day("2026-08-13")
        assertTrue(occurrence(start + 9 * 3_600_000L, start + 10 * 3_600_000L).overlaps(start, end))
    }

    @Test
    fun `un evenement qui finit quand la plage commence ne la touche pas`() {
        val (start, end) = day("2026-08-13")
        val laVeille = occurrence(start - 3_600_000L, start)
        assertFalse(laVeille.overlaps(start, end))
    }

    @Test
    fun `un evenement qui commence quand la plage finit ne la touche pas`() {
        val (start, end) = day("2026-08-13")
        val leLendemain = occurrence(end, end + 3_600_000L)
        assertFalse(leLendemain.overlaps(start, end))
    }

    @Test
    fun `un evenement commence avant la plage et qui deborde dedans la touche`() {
        val (start, end) = day("2026-08-13")
        val aCheval = occurrence(start - 2 * 3_600_000L, start + 3_600_000L)
        assertTrue(aCheval.overlaps(start, end))
    }

    @Test
    fun `un evenement qui recouvre toute la plage la touche`() {
        val (start, end) = day("2026-08-13")
        val troisJours = occurrence(start - 24 * 3_600_000L, end + 24 * 3_600_000L)
        assertTrue(troisJours.overlaps(start, end))
    }

    @Test
    fun `un evenement de trois jours touche chacun de ses jours`() {
        val debut = Zones.dayStart(LocalDate.parse("2026-08-12"), paris)
        val fin = Zones.dayEnd(LocalDate.parse("2026-08-14"), paris)
        val sejour = occurrence(debut, fin)
        for (date in listOf("2026-08-12", "2026-08-13", "2026-08-14")) {
            val (start, end) = day(date)
            assertTrue("le $date devrait être couvert", sejour.overlaps(start, end))
        }
        val (veilleStart, veilleEnd) = day("2026-08-11")
        assertFalse(sejour.overlaps(veilleStart, veilleEnd))
        val (lendemainStart, lendemainEnd) = day("2026-08-15")
        assertFalse(sejour.overlaps(lendemainStart, lendemainEnd))
    }

    @Test
    fun `les jours couverts s'arretent a la veille quand la fin tombe sur minuit`() {
        // La fin est exclusive: un événement qui s'arrête à minuit appartient à la veille et ne
        // doit pas poser de pastille sur le lendemain.
        val debut = Zones.dayStart(LocalDate.parse("2026-08-12"), paris)
        val fin = Zones.dayEnd(LocalDate.parse("2026-08-13"), paris)
        assertEquals(
            listOf(LocalDate.parse("2026-08-12"), LocalDate.parse("2026-08-13")),
            Zones.daysCovered(debut, fin, allDay = false, zone = paris)
        )
    }

    @Test
    fun `une journee entiere se lit en UTC et ne glisse pas d'un jour`() {
        // Ancrée à minuit UTC, elle serait lue la veille à 22 h si on la convertissait en heure
        // de Paris — et changerait de jour selon l'endroit où l'application est ouverte.
        val date = LocalDate.parse("2026-08-14")
        val start = Zones.allDayStart(date)
        val end = Zones.allDayStart(date.plusDays(1))
        assertEquals(listOf(date), Zones.daysCovered(start, end, allDay = true, zone = paris))
    }

    @Test
    fun `un evenement d'une journee entiere sur trois jours couvre les trois`() {
        val debut = Zones.allDayStart(LocalDate.parse("2026-08-14"))
        val fin = Zones.allDayStart(LocalDate.parse("2026-08-17"))
        assertEquals(
            listOf("2026-08-14", "2026-08-15", "2026-08-16"),
            Zones.daysCovered(debut, fin, allDay = true, zone = paris).map { it.toString() }
        )
    }
}
