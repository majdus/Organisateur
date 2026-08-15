package com.majdus.organisateur

import android.content.Context
import com.majdus.organisateur.agenda.Frequency
import com.majdus.organisateur.agenda.RecurrenceRule
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Mise en mots d'une règle de répétition.
 *
 * Le résumé tient sur une ligne, à droite du libellé « Répétition »: c'est ce qu'on lit avant
 * d'ouvrir le sélecteur, et souvent la seule chose qu'on ait besoin d'en savoir.
 */
object RecurrenceLabels {

    private val untilDate = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)

    /** "Jamais", "Toutes les semaines", "Toutes les 2 semaines, lundi, mercredi"… */
    fun summary(context: Context, rrule: String): String {
        val rule = RecurrenceRule.parse(rrule) ?: return context.getString(R.string.recurrence_never)

        var text = base(context, rule)
        if (rule.frequency == Frequency.WEEKLY && rule.byDay.isNotEmpty()) {
            val days = rule.byDay.joinToString(", ") { dayName(it.day) }
            text = context.getString(R.string.recurrence_summary_days, text, days)
        }
        return when {
            rule.until != 0L -> context.getString(
                R.string.recurrence_summary_until, text, untilDate.format(Date(rule.until))
            )
            rule.count != 0 ->
                context.getString(R.string.recurrence_summary_count, text, rule.count)
            else -> text
        }
    }

    private fun base(context: Context, rule: RecurrenceRule): String =
        if (rule.interval == 1) {
            context.getString(
                when (rule.frequency) {
                    Frequency.DAILY -> R.string.recurrence_summary_daily
                    Frequency.WEEKLY -> R.string.recurrence_summary_weekly
                    Frequency.MONTHLY -> R.string.recurrence_summary_monthly
                    Frequency.YEARLY -> R.string.recurrence_summary_yearly
                }
            )
        } else {
            context.getString(
                when (rule.frequency) {
                    Frequency.DAILY -> R.string.recurrence_summary_every_days
                    Frequency.WEEKLY -> R.string.recurrence_summary_every_weeks
                    Frequency.MONTHLY -> R.string.recurrence_summary_every_months
                    Frequency.YEARLY -> R.string.recurrence_summary_every_years
                },
                rule.interval
            )
        }

    /** "lundi" — nom complet, en minuscules, comme le reste des libellés de dates. */
    fun dayName(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, Locale.FRANCE).lowercase(Locale.FRANCE)

    /** "L" — initiale pour les puces du sélecteur hebdomadaire. */
    fun dayInitial(day: DayOfWeek): String =
        dayName(day).take(1).uppercase(Locale.FRANCE)

    /** "2e jeudi", "dernier vendredi" — motif mensuel par jour de semaine. */
    fun weekdayPosition(context: Context, position: Int, day: DayOfWeek): String {
        val rank = if (position < 0) {
            context.getString(R.string.recurrence_weekday_position_last)
        } else {
            context.getString(R.string.recurrence_weekday_position, position)
        }
        return "$rank ${dayName(day)}"
    }
}
