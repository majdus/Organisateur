package com.majdus.organisateur

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.majdus.organisateur.data.Event
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object EventScheduler {
    const val EXTRA_EVENT_ID = "com.majdus.organisateur.EVENT_ID"
    const val EXTRA_EVENT_TITLE = "com.majdus.organisateur.EVENT_TITLE"

    private fun pendingIntent(context: Context, event: Event): PendingIntent {
        val intent = Intent(context, EventReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra(EXTRA_EVENT_TITLE, event.title)
        }
        return PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, event: Event) {
        if (!event.hasNotification) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        val date = dateFormat.parse(event.date) ?: return
        
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, event.hour)
            set(Calendar.MINUTE, event.minute)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            return // Don't schedule in the past
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent(context, event)
            )
        } catch (e: SecurityException) {
            // Permission not granted, cannot schedule exact alarm
            e.printStackTrace()
        }
    }

    fun cancel(context: Context, event: Event) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, event))
    }
}
