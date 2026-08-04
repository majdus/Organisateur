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

class EventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EventScheduler.EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EventScheduler.EXTRA_EVENT_TITLE).orEmpty()
        val time = intent.getStringExtra(EventScheduler.EXTRA_EVENT_TIME).orEmpty()

        showNotification(context, id, title, time)
    }

    private fun showNotification(context: Context, id: String, title: String, time: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val contentIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, Calendrier::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Le titre de la notification est l'intitulé de l'événement: c'est ce que
        // l'utilisateur cherche des yeux, pas le nom de la fonctionnalité.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_event_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent_calendar))
            .setContentTitle(title.ifEmpty { context.getString(R.string.event_new_title) })
            .setContentText(
                if (time.isNotEmpty()) context.getString(R.string.event_notification_text, time)
                else context.getString(R.string.calendar_title)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        notificationManager.notify(id.hashCode(), notification)
    }

    private fun createChannel(notificationManager: NotificationManager) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Événements du calendrier",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "calendar_events"
    }
}
