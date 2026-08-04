package com.majdus.organisateur

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmText = intent.getStringExtra(AlarmScheduler.EXTRA_TEXT) ?: return
        val description = intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION) ?: "Alarme"

        // Une même alarme livrée deux fois de suite (à la minute près) ne sonne qu'une fois
        if (!AlarmScheduler.isDuplicateFiring(context, alarmText)) {
            AlarmScheduler.markFired(context, alarmText)
            showNotification(context, alarmText, description)
        }

        // Alarme quotidienne: replanifie la prochaine occurrence si toujours activée.
        // La marge évite de retomber sur l'occurrence qui vient de sonner.
        if (AlarmScheduler.isEnabled(context, alarmText)) {
            AlarmScheduler.schedule(
                context,
                alarmText,
                System.currentTimeMillis() + AlarmScheduler.MIN_LEAD_MS
            )
        }
    }

    private fun showNotification(context: Context, alarmText: String, description: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val contentIntent = PendingIntent.getActivity(
            context,
            alarmText.hashCode(),
            Intent(context, Alarms::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val time = AlarmScheduler.parse(alarmText)?.let { (_, hour, minute) ->
            String.format(Locale.FRANCE, "%02d:%02d", hour, minute)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent_indigo))
            .setContentTitle(description)
            .setContentText(
                if (time != null) context.getString(R.string.alarm_notification_text, time)
                else context.getString(R.string.alarms_title)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        notificationManager.notify(alarmText.hashCode(), notification)
    }

    private fun createChannel(notificationManager: NotificationManager) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarmes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "alarms"
    }
}
