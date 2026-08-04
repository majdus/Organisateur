package com.majdus.organisateur

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.majdus.organisateur.data.Event
import java.util.Locale

object EventScheduler {
    const val EXTRA_EVENT_ID = "com.majdus.organisateur.EVENT_ID"
    const val EXTRA_EVENT_TITLE = "com.majdus.organisateur.EVENT_TITLE"
    const val EXTRA_EVENT_TIME = "com.majdus.organisateur.EVENT_TIME"

    private fun pendingIntent(context: Context, event: Event): PendingIntent {
        val intent = Intent(context, EventReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, event.id)
            putExtra(EXTRA_EVENT_TITLE, event.title)
            putExtra(
                EXTRA_EVENT_TIME,
                String.format(Locale.FRANCE, "%02d:%02d", event.hour, event.minute)
            )
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

        val triggerAt = EventTimes.at(event)
        // Une heure déjà passée ne se programme pas: la feuille d'édition en avertit
        // l'utilisateur au moment de la saisie.
        if (triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context, event)
            )
        } catch (e: SecurityException) {
            // L'autorisation d'alarme exacte n'est pas accordée: rien à programmer.
            e.printStackTrace()
        }
    }

    fun cancel(context: Context, event: Event) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, event))
    }
}
