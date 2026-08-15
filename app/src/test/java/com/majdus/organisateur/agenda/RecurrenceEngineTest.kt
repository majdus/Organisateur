package com.majdus.organisateur.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceEngineTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun at(date: String, hour: Int = 9, minute: Int = 0): LocalDateTime =
        LocalDate.parse(date).atTime(hour, minute)

    private fun expand(
        start: LocalDateTime,
        rrule: String,
        from: String,
        to: String,
        durationMs: Long = 0,
        limit: Int = 1_000
    ): List<String> {
        val rule = RecurrenceRule.parse(rrule)!!
        return RecurrenceEngine.expand(
            seriesStart = start,
            rule = rule,
            zone = paris,
            from = Zones.toUtc(LocalDate.parse(from).atStartOfDay(), paris),
            to = Zones.toUtc(LocalDate.parse(to).atStartOfDay(), paris),
            durationMs = durationMs,
            limit = limit
        ).map { it.toLocalDate().toString() }
    }

    @Test
    fun `tous les trois jours`() {
        assertEquals(
            listOf("2026-08-13", "2026-08-16", "2026-08-19", "2026-08-22"),
            expand(at("2026-08-13"), "FREQ=DAILY;INTERVAL=3", "2026-08-13", "2026-08-25")
        )
    }

    @Test
    fun `les lundis et mercredis`() {
        assertEquals(
            listOf("2026-08-10", "2026-08-12", "2026-08-17", "2026-08-19"),
            expand(at("2026-08-10"), "FREQ=WEEKLY;BYDAY=MO,WE", "2026-08-10", "2026-08-24")
        )
    }

    @Test
    fun `une semaine sur deux garde son ancrage`() {
        assertEquals(
            listOf("2026-08-10", "2026-08-24", "2026-09-07"),
            expand(at("2026-08-10"), "FREQ=WEEKLY;INTERVAL=2", "2026-08-10", "2026-09-08")
        )
    }

    @Test
    fun `le 31 saute les mois trop courts au lieu d'etre rogne`() {
        // La RFC 5545 demande de sauter: un rendez-vous du 31 n'a pas lieu en février, et
        // surtout pas le 28. `plusMonths` ferait exactement l'inverse.
        assertEquals(
            listOf("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31"),
            expand(at("2026-01-31"), "FREQ=MONTHLY;BYMONTHDAY=31", "2026-01-01", "2026-08-01")
        )
    }

    @Test
    fun `le dernier vendredi du mois`() {
        assertEquals(
            listOf("2026-08-28", "2026-09-25", "2026-10-30", "2026-11-27"),
            expand(at("2026-08-28"), "FREQ=MONTHLY;BYDAY=-1FR", "2026-08-01", "2026-12-01")
        )
    }

    @Test
    fun `le dernier jour du mois`() {
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31"),
            expand(at("2026-01-31"), "FREQ=MONTHLY;BYMONTHDAY=-1", "2026-01-01", "2026-04-01")
        )
    }

    @Test
    fun `le cinquieme jeudi saute les mois qui n'en ont pas`() {
        // `TemporalAdjusters.dayOfWeekInMonth` déborde silencieusement sur le mois suivant quand
        // la position n'existe pas: sans la vérification du mois, novembre rendrait le 3 décembre.
        assertEquals(
            listOf("2026-10-29", "2026-12-31"),
            expand(at("2026-10-29"), "FREQ=MONTHLY;BYDAY=5TH", "2026-10-01", "2027-04-01")
        )
    }

    @Test
    fun `le 29 fevrier ne revient qu'aux annees bissextiles`() {
        assertEquals(
            listOf("2024-02-29", "2028-02-29", "2032-02-29"),
            expand(at("2024-02-29"), "FREQ=YEARLY", "2024-01-01", "2033-01-01")
        )
    }

    @Test
    fun `UNTIL est inclusif`() {
        // 2026-08-15 09:00 à Paris (heure d'été) vaut 07:00 UTC: la borne tombe exactement sur
        // une occurrence, qui doit être gardée.
        assertEquals(
            listOf("2026-08-13", "2026-08-14", "2026-08-15"),
            expand(at("2026-08-13"), "FREQ=DAILY;UNTIL=20260815T070000Z", "2026-08-13", "2026-09-01")
        )
    }

    @Test
    fun `UNTIL une seconde trop tot coupe l'occurrence`() {
        assertEquals(
            listOf("2026-08-13", "2026-08-14"),
            expand(at("2026-08-13"), "FREQ=DAILY;UNTIL=20260815T065959Z", "2026-08-13", "2026-09-01")
        )
    }

    @Test
    fun `COUNT limite la serie`() {
        assertEquals(
            listOf("2026-08-13", "2026-08-14", "2026-08-15"),
            expand(at("2026-08-13"), "FREQ=DAILY;COUNT=3", "2026-08-13", "2026-09-01")
        )
    }

    @Test
    fun `le passage a l'heure d'hiver garde l'heure murale`() {
        // Le 25 octobre 2026, la France recule d'une heure. Un rendez-vous quotidien de 9 h doit
        // rester à 9 h: l'écart entre deux occurrences est alors de 25 heures, pas de 24.
        val rule = RecurrenceRule.parse("FREQ=DAILY")!!
        val starts = RecurrenceEngine.expand(
            seriesStart = at("2026-10-24"),
            rule = rule,
            zone = paris,
            from = Zones.toUtc(at("2026-10-24", 0), paris),
            to = Zones.toUtc(at("2026-10-27", 0), paris)
        )
        assertEquals(3, starts.size)
        starts.forEach { assertEquals(9, it.hour) }

        val veille = Zones.toUtc(starts[0], paris)
        val lendemain = Zones.toUtc(starts[1], paris)
        assertEquals(25 * 3_600_000L, lendemain - veille)
    }

    @Test
    fun `le passage a l'heure d'ete garde l'heure murale`() {
        // Le 29 mars 2026, la France avance d'une heure: 23 heures entre deux occurrences.
        val rule = RecurrenceRule.parse("FREQ=DAILY")!!
        val starts = RecurrenceEngine.expand(
            seriesStart = at("2026-03-28"),
            rule = rule,
            zone = paris,
            from = Zones.toUtc(at("2026-03-28", 0), paris),
            to = Zones.toUtc(at("2026-03-31", 0), paris)
        )
        assertEquals(3, starts.size)
        starts.forEach { assertEquals(9, it.hour) }
        assertEquals(23 * 3_600_000L, Zones.toUtc(starts[1], paris) - Zones.toUtc(starts[0], paris))
    }

    @Test
    fun `une occurrence tombant dans l'heure inexistante n'est pas perdue`() {
        // Le 29 mars 2026, 02:30 n'existe pas à Paris. L'occurrence est décalée en avant plutôt
        // que supprimée: on ne fait jamais disparaître un rendez-vous d'une série.
        val rule = RecurrenceRule.parse("FREQ=DAILY")!!
        val starts = RecurrenceEngine.expand(
            seriesStart = at("2026-03-28", 2, 30),
            rule = rule,
            zone = paris,
            from = Zones.toUtc(at("2026-03-28", 0), paris),
            to = Zones.toUtc(at("2026-03-31", 0), paris)
        )
        assertEquals(3, starts.size)
        val trou = Zones.localDateTime(Zones.toUtc(starts[1], paris), paris)
        assertEquals(LocalDate.parse("2026-03-29").atTime(3, 30), trou)
    }

    @Test
    fun `le saut de periodes donne le meme resultat que l'iteration complete`() {
        // Le test qui protège l'optimisation: une série quotidienne née en 2010 est parcourue de
        // bout en bout dans un cas, enjambée dans l'autre. Toute erreur d'arithmétique du saut
        // se voit ici, et nulle part ailleurs.
        val depuisLeDebut = expand(
            at("2010-01-01"), "FREQ=DAILY;INTERVAL=7",
            "2010-01-01", "2026-08-20", limit = 100_000
        ).filter { it >= "2026-08-01" }
        val avecSaut = expand(at("2010-01-01"), "FREQ=DAILY;INTERVAL=7", "2026-08-01", "2026-08-20")
        assertEquals(depuisLeDebut, avecSaut)
        assertEquals(listOf("2026-08-07", "2026-08-14"), avecSaut)
    }

    @Test
    fun `le saut mensuel donne le meme resultat que l'iteration complete`() {
        val depuisLeDebut = expand(
            at("2010-01-15"), "FREQ=MONTHLY;INTERVAL=5",
            "2010-01-01", "2027-01-01", limit = 100_000
        ).filter { it >= "2026-01-01" }
        val avecSaut = expand(at("2010-01-15"), "FREQ=MONTHLY;INTERVAL=5", "2026-01-01", "2027-01-01")
        assertEquals(depuisLeDebut, avecSaut)
    }

    @Test
    fun `une occurrence commencee avant la fenetre y est ramenee par sa duree`() {
        // Sans la durée, un rendez-vous de trois jours disparaîtrait de ses deux derniers.
        val troisJours = 3 * 24 * 3_600_000L
        assertEquals(
            listOf("2026-08-10", "2026-08-17"),
            expand(
                at("2026-08-10"), "FREQ=WEEKLY",
                from = "2026-08-12", to = "2026-08-18", durationMs = troisJours
            )
        )
    }

    @Test
    fun `une occurrence sans duree posee sur la borne de debut reste dans la fenetre`() {
        // Sans durée, l'étendue d'une occurrence est vide: le seul test de chevauchement
        // l'écarterait alors qu'elle commence pile au début de la fenêtre. C'est ce cas qui sert
        // à compter les occurrences d'une série jusqu'à une date, donc à répartir un COUNT lors
        // d'une scission — un décompte trop court y donnait une occurrence de trop à la queue.
        val starts = RecurrenceEngine.expand(
            seriesStart = at("2026-08-13"),
            rule = RecurrenceRule.parse("FREQ=DAILY")!!,
            zone = paris,
            from = Zones.toUtc(at("2026-08-13"), paris),
            to = Zones.toUtc(at("2026-08-15"), paris),
            durationMs = 0
        )
        assertEquals(listOf(at("2026-08-13"), at("2026-08-14")), starts)
    }

    @Test
    fun `la fenetre exclut ce qui commence a sa borne de fin`() {
        assertEquals(
            listOf("2026-08-13", "2026-08-14"),
            expand(at("2026-08-13"), "FREQ=DAILY", "2026-08-13", "2026-08-15")
        )
    }

    @Test
    fun `la prochaine occurrence est celle qui commence a l'instant demande ou apres`() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE")!!
        val start = at("2026-08-10")
        val depuisMardi = RecurrenceEngine.next(
            start, rule, paris, Zones.toUtc(at("2026-08-11", 0), paris)
        )
        assertEquals(at("2026-08-12"), depuisMardi)

        // Une borne posée exactement sur une occurrence retient cette occurrence.
        val surLOccurrence = RecurrenceEngine.next(
            start, rule, paris, Zones.toUtc(at("2026-08-12"), paris)
        )
        assertEquals(at("2026-08-12"), surLOccurrence)
    }

    @Test
    fun `une serie epuisee n'a pas de prochaine occurrence`() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=2")!!
        assertNull(
            RecurrenceEngine.next(
                at("2026-08-13"), rule, paris, Zones.toUtc(at("2026-08-20", 0), paris)
            )
        )
    }

    @Test
    fun `la fin de serie borne les series comptees et laisse les autres ouvertes`() {
        val uneHeure = 3_600_000L

        val comptee = RecurrenceEngine.seriesEnd(
            at("2026-08-13"), RecurrenceRule.parse("FREQ=DAILY;COUNT=3")!!, paris, uneHeure
        )
        assertEquals(Zones.toUtc(at("2026-08-15", 10), paris), comptee)

        // Sur un UNTIL, la valeur rendue est un majorant: la borne elle-même, prolongée d'une
        // durée. Un majorant ne peut produire qu'un candidat de trop, jamais un événement absent.
        val avecTerme = RecurrenceRule.parse("FREQ=DAILY;UNTIL=20260815T070000Z")!!
        val bornee = RecurrenceEngine.seriesEnd(at("2026-08-13"), avecTerme, paris, uneHeure)
        assertEquals(avecTerme.until + uneHeure, bornee)

        val sansFin = RecurrenceEngine.seriesEnd(
            at("2026-08-13"), RecurrenceRule.parse("FREQ=DAILY")!!, paris, uneHeure
        )
        assertEquals(Long.MAX_VALUE, sansFin)
    }
}
