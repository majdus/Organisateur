package com.majdus.organisateur.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class RecurrenceRuleTest {

    @Test
    fun `une regle relue puis reecrite ne change pas`() {
        val rules = listOf(
            "FREQ=DAILY",
            "FREQ=DAILY;INTERVAL=3",
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE",
            "FREQ=MONTHLY;BYDAY=-1FR",
            "FREQ=MONTHLY;BYMONTHDAY=1,15",
            "FREQ=MONTHLY;BYMONTHDAY=-1",
            "FREQ=YEARLY",
            "FREQ=DAILY;COUNT=5",
            "FREQ=DAILY;UNTIL=20261231T235959Z"
        )
        for (rule in rules) {
            assertEquals(rule, RecurrenceRule.parse(rule)?.encode())
        }
    }

    @Test
    fun `les champs sont lus tels quels`() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")!!
        assertEquals(Frequency.WEEKLY, rule.frequency)
        assertEquals(2, rule.interval)
        assertEquals(
            listOf(WeekdayNum(0, DayOfWeek.MONDAY), WeekdayNum(0, DayOfWeek.WEDNESDAY)),
            rule.byDay
        )
    }

    @Test
    fun `une position de jour est lue avec son signe`() {
        assertEquals(
            listOf(WeekdayNum(-1, DayOfWeek.FRIDAY)),
            RecurrenceRule.parse("FREQ=MONTHLY;BYDAY=-1FR")!!.byDay
        )
        assertEquals(
            listOf(WeekdayNum(3, DayOfWeek.THURSDAY)),
            RecurrenceRule.parse("FREQ=MONTHLY;BYDAY=3TH")!!.byDay
        )
    }

    @Test
    fun `UNTIL est lu comme un instant UTC`() {
        // 20261231T235959Z = 1798761599000 ms depuis l'epoch.
        assertEquals(
            1_798_761_599_000L,
            RecurrenceRule.parse("FREQ=DAILY;UNTIL=20261231T235959Z")!!.until
        )
    }

    @Test
    fun `une partie hors du sous-ensemble fait echouer la lecture`() {
        // Ignorer BYSETPOS afficherait une série fausse sans le dire: mieux vaut la refuser et
        // retomber sur un événement unique.
        assertNull(RecurrenceRule.parse("FREQ=MONTHLY;BYSETPOS=1;BYDAY=MO"))
        assertNull(RecurrenceRule.parse("FREQ=MONTHLY;BYMONTH=3"))
        assertNull(RecurrenceRule.parse("FREQ=WEEKLY;WKST=SU"))
    }

    @Test
    fun `COUNT et UNTIL ensemble sont refuses`() {
        assertNull(RecurrenceRule.parse("FREQ=DAILY;COUNT=3;UNTIL=20261231T235959Z"))
    }

    @Test
    fun `les combinaisons sans signification sont refusees`() {
        assertNull(RecurrenceRule.parse("FREQ=WEEKLY;BYMONTHDAY=15"))
        assertNull(RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=3TH"))
        assertNull(RecurrenceRule.parse("FREQ=YEARLY;BYDAY=MO"))
    }

    @Test
    fun `une chaine malformee rend null sans lever`() {
        val malformed = listOf(
            "",
            "   ",
            "DAILY",
            "FREQ=",
            "FREQ=HOURLY",
            "FREQ=DAILY;INTERVAL=0",
            "FREQ=DAILY;INTERVAL=abc",
            "FREQ=DAILY;COUNT=0",
            "FREQ=MONTHLY;BYMONTHDAY=0",
            "FREQ=MONTHLY;BYMONTHDAY=32",
            "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=DAILY;UNTIL=2026-12-31",
            "=DAILY"
        )
        for (rule in malformed) {
            assertNull("« $rule » devrait être refusée", RecurrenceRule.parse(rule))
        }
    }
}
