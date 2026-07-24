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

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmText = intent.getStringExtra(AlarmScheduler.EXTRA_TEXT) ?: return
        val description = intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION) ?: "Alarme"

        showNotification(context, alarmText, description)

        // Alarme quotidienne: replanifie la prochaine occurrence si toujours activée
        if (AlarmScheduler.isEnabled(context, alarmText)) {
            AlarmScheduler.schedule(context, alarmText)
        }
    }

    private fun showNotification(context: Context, alarmText: String, description: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, Alarms::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_add_black)
            .setContentTitle("Alarme")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
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
