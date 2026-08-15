package com.majdus.organisateur.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlapLayoutTest {

    private val heure = 3_600_000L

    /** Un rendez-vous de [startHour] à [endHour], en heures depuis une origine quelconque. */
    private fun occurrence(title: String, startHour: Double, endHour: Double): Occurrence {
        val start = (startHour * heure).toLong()
        val end = (endHour * heure).toLong()
        return Occurrence(
            eventId = title,
            occurrenceStartUtc = start,
            startUtc = start,
            endUtc = end,
            allDay = false,
            title = title,
            location = "",
            colorKey = "default",
            isRecurring = false,
            isDetached = false
        )
    }

    /** Le placement rendu, indexé par titre: "colonne/total". */
    private fun place(vararg occurrences: Occurrence): Map<String, String> =
        OverlapLayout.place(occurrences.toList())
            .associate { it.occurrence.title to "${it.column}/${it.columns}" }

    @Test
    fun `des evenements disjoints occupent chacun toute la largeur`() {
        val placed = place(
            occurrence("A", 9.0, 10.0),
            occurrence("B", 10.0, 11.0),
            occurrence("C", 14.0, 15.0)
        )
        assertEquals(mapOf("A" to "0/1", "B" to "0/1", "C" to "0/1"), placed)
    }

    @Test
    fun `deux evenements qui se recouvrent se partagent la largeur`() {
        val placed = place(
            occurrence("A", 9.0, 10.5),
            occurrence("B", 10.0, 11.0)
        )
        assertEquals(mapOf("A" to "0/2", "B" to "1/2"), placed)
    }

    @Test
    fun `un evenement contenu dans un autre se pose a sa droite`() {
        // Le plus long ouvre la colonne de gauche: on lit d'abord le cadre, puis son contenu.
        val placed = place(
            occurrence("Journee", 9.0, 18.0),
            occurrence("Point", 11.0, 12.0)
        )
        assertEquals(mapOf("Journee" to "0/2", "Point" to "1/2"), placed)
    }

    @Test
    fun `un escalier ne mobilise que deux colonnes`() {
        // A et C ne se touchent pas: C réutilise la colonne libérée par A.
        val placed = place(
            occurrence("A", 9.0, 10.0),
            occurrence("B", 9.5, 11.0),
            occurrence("C", 10.0, 11.5)
        )
        assertEquals(mapOf("A" to "0/2", "B" to "1/2", "C" to "0/2"), placed)
    }

    @Test
    fun `un groupe impose sa largeur a tous ses membres`() {
        // C ne chevauche que B, mais appartient au même groupe que A, qu'il touche par son
        // intermédiaire: les trois blocs doivent avoir la même largeur, sinon la colonne paraît
        // se déformer en cours de journée. Deux colonnes suffisent — C reprend celle qu'A libère.
        val placed = place(
            occurrence("A", 9.0, 10.0),
            occurrence("B", 9.5, 12.0),
            occurrence("C", 10.5, 11.0)
        )
        assertEquals(mapOf("A" to "0/2", "B" to "1/2", "C" to "0/2"), placed)
    }

    @Test
    fun `des horaires identiques prennent chacun leur colonne`() {
        val placed = place(
            occurrence("A", 9.0, 10.0),
            occurrence("B", 9.0, 10.0),
            occurrence("C", 9.0, 10.0)
        )
        assertEquals(setOf("0/3", "1/3", "2/3"), placed.values.toSet())
    }

    @Test
    fun `un evenement de duree nulle garde sa colonne`() {
        // Sans plancher de durée, les deux se retrouveraient dans la même colonne et le second
        // recouvrirait le premier au pixel près.
        val placed = place(
            occurrence("Instant", 9.0, 9.0),
            occurrence("Suivant", 9.1, 10.0)
        )
        assertEquals(mapOf("Instant" to "0/2", "Suivant" to "1/2"), placed)
    }

    @Test
    fun `un evenement qui commence quand un autre finit ne le chevauche pas`() {
        val placed = place(
            occurrence("A", 9.0, 10.0),
            occurrence("B", 10.0, 11.0)
        )
        assertEquals(mapOf("A" to "0/1", "B" to "0/1"), placed)
    }

    @Test
    fun `une liste vide ne place rien`() {
        assertEquals(emptyList<Slot>(), OverlapLayout.place(emptyList()))
    }
}
