package com.majdus.organisateur

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

object AlarmScheduler {
    const val EXTRA_DESCRIPTION = "alarm_description"
    const val EXTRA_TEXT = "alarm_text"
    private const val DISABLED_KEY = "alarms_disabled"

    fun schedule(context: Context, alarmText: String) {
        val (description, hour, minute) = parse(alarmText) ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent(context, alarmText, description)
        )
    }

    fun cancel(context: Context, alarmText: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, alarmText, null))
    }

    fun setEnabled(context: Context, alarmText: String, enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val disabled = HashSet(sharedPreferences.getStringSet(DISABLED_KEY, HashSet<String>())!!)
        if (enabled) disabled.remove(alarmText) else disabled.add(alarmText)
        with(sharedPreferences.edit()) {
            putStringSet(DISABLED_KEY, disabled)
            apply()
        }
        if (enabled) schedule(context, alarmText) else cancel(context, alarmText)
    }

    fun forget(context: Context, alarmText: String) {
        cancel(context, alarmText)
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val disabled = HashSet(sharedPreferences.getStringSet(DISABLED_KEY, HashSet<String>())!!)
        disabled.remove(alarmText)
        with(sharedPreferences.edit()) {
            putStringSet(DISABLED_KEY, disabled)
            apply()
        }
    }

    fun isEnabled(context: Context, alarmText: String): Boolean {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val disabled = sharedPreferences.getStringSet(DISABLED_KEY, HashSet<String>())!!
        return !disabled.contains(alarmText)
    }

    fun rescheduleAll(context: Context) {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val alarms = sharedPreferences.getStringSet("alarms", HashSet<String>())!!
        for (alarm in alarms) {
            if (isEnabled(context, alarm)) {
                schedule(context, alarm)
            }
        }
    }

    fun parse(alarmText: String): Triple<String, Int, Int>? {
        val lines = alarmText.split("\n")
        if (lines.size < 2) return null
        val time = lines.last().split(":")
        val hour = time.firstOrNull()?.trim()?.toIntOrNull() ?: return null
        val minute = time.lastOrNull()?.trim()?.toIntOrNull() ?: return null
        val description = alarmText.removeSuffix(lines.last()).trim()
        return Triple(description, hour, minute)
    }

    private fun pendingIntent(context: Context, alarmText: String, description: String?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TEXT, alarmText)
            putExtra(EXTRA_DESCRIPTION, description)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmText.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
