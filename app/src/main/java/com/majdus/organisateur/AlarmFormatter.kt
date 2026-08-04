package com.majdus.organisateur

import android.content.Context
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

/** Mise en forme des libellés temporels de l'écran Rappels. */
object AlarmFormatter {

    /** "Demain à 07:05 · dans 8 h 20", ou "Désactivé" si le rappel est éteint. */
    fun nextTrigger(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): String {
        if (!alarm.enabled) return context.getString(R.string.alarm_disabled)
        val triggerAt = alarm.nextTriggerAt(now)
        return context.getString(
            R.string.alarm_next_trigger,
            dayLabel(context, triggerAt, now),
            alarm.timeText,
            countdown(context, triggerAt, now)
        )
    }

    /** "demain à 07:05" — utilisé en incise dans une phrase. */
    fun whenLabel(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): String {
        val triggerAt = alarm.nextTriggerAt(now)
        val day = dayLabel(context, triggerAt, now).lowercase(Locale.FRANCE)
        return "$day à ${alarm.timeText}"
    }

    /** "Sonnera demain à 07:05 · dans 8 h 20" — aperçu dans la feuille d'édition. */
    fun preview(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): String =
        context.getString(
            R.string.alarm_preview,
            whenLabel(context, alarm, now),
            countdown(context, alarm.nextTriggerAt(now), now)
        )

    /** Résumé affiché sous le titre de l'écran. */
    fun summary(context: Context, alarms: List<Alarm>, now: Long = System.currentTimeMillis()): String {
        if (alarms.isEmpty()) return context.getString(R.string.alarms_summary_none)
        val count = if (alarms.size == 1) {
            context.getString(R.string.alarms_count_one)
        } else {
            context.getString(R.string.alarms_count_other, alarms.size)
        }
        val next = alarms.filter { it.enabled }.minByOrNull { it.nextTriggerAt(now) }
            ?: return context.getString(R.string.alarms_summary_all_off, count)
        return context.getString(R.string.alarms_summary_next, count, whenLabel(context, next, now))
    }

    private fun dayLabel(context: Context, triggerAt: Long, now: Long): String =
        if (isSameDay(triggerAt, now)) {
            context.getString(R.string.alarm_today)
        } else {
            context.getString(R.string.alarm_tomorrow)
        }

    /** "dans 8 h 20" — arrondi à la minute supérieure pour ne jamais annoncer moins que le réel. */
    private fun countdown(context: Context, triggerAt: Long, now: Long): String {
        val minutes = ceil((triggerAt - now) / 60_000.0).toLong()
        return when {
            minutes <= 0L -> context.getString(R.string.alarm_in_less_than_a_minute)
            minutes < 60L -> context.getString(R.string.alarm_in_minutes, minutes.toInt())
            minutes % 60L == 0L -> context.getString(R.string.alarm_in_hours, (minutes / 60).toInt())
            else -> context.getString(
                R.string.alarm_in_hours_minutes,
                (minutes / 60).toInt(),
                (minutes % 60).toInt()
            )
        }
    }

    private fun isSameDay(first: Long, second: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = first }
        val b = Calendar.getInstance().apply { timeInMillis = second }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
