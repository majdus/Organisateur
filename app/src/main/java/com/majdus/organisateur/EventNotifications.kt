package com.majdus.organisateur

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Notification d'un rappel d'événement.
 *
 * Sortie du receiver: le planificateur en émet plusieurs d'affilée quand des rappels coïncident,
 * et le receiver n'a plus à connaître que le déclenchement.
 */
object EventNotifications {

    private const val CHANNEL_ID = "calendar_events"

    fun show(context: Context, reminder: DueReminder) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(context, manager)

        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.notificationId,
            Intent(context, Agenda::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Le titre de la notification est l'intitulé de l'événement: c'est ce que l'utilisateur
        // cherche des yeux, pas le nom de la fonctionnalité.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_event_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent_calendar))
            .setContentTitle(reminder.title.ifEmpty { context.getString(R.string.event_new_title) })
            .setContentText(subtitle(context, reminder))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        manager.notify(reminder.notificationId, notification)
    }

    /**
     * « Événement de 14:30 » à l'heure dite, « 10 min avant · 14:30 » sinon.
     *
     * Avec plusieurs rappels possibles, dire seulement l'heure ne suffit plus: deux notifications
     * pour le même rendez-vous seraient indiscernables.
     */
    private fun subtitle(context: Context, reminder: DueReminder): String {
        val time = DateLabels.time(reminder.occurrenceStartUtc)
        return if (reminder.minutesBefore <= 0) {
            context.getString(R.string.event_notification_text, time)
        } else {
            "${ReminderLabels.label(context, reminder.minutesBefore)} · $time"
        }
    }

    private fun createChannel(context: Context, manager: NotificationManager) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.event_channel_name),
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
        manager.createNotificationChannel(channel)
    }
}
