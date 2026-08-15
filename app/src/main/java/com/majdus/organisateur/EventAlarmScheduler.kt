package com.majdus.organisateur

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.majdus.organisateur.agenda.Occurrence
import com.majdus.organisateur.agenda.Zones
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.EventRepository

/** Un rappel dû à un instant précis, pour une occurrence précise. */
data class DueReminder(
    val eventId: String,
    val occurrenceStartUtc: Long,
    val minutesBefore: Int,
    val triggerAt: Long,
    val title: String
) {
    /** Identité d'un déclenchement: deux occurrences d'une même série n'en partagent pas. */
    val key: String get() = "$eventId|$occurrenceStartUtc|$minutesBefore"

    val notificationId: Int get() = "$eventId|$occurrenceStartUtc".hashCode()
}

/**
 * Programmation des rappels d'événement.
 *
 * **Une seule alarme est vivante à la fois**, réarmée après chaque déclenchement. Poser une
 * alarme par rappel serait impossible: une série sans terme a une infinité d'occurrences, et
 * Android plafonne les alarmes exactes à 500 par application depuis l'API 34.
 *
 * Le prochain rappel est cherché dans une fenêtre glissante de sept jours. Quand elle est vide,
 * une alarme « chien de garde » est quand même posée à sa fin ou au prochain minuit: c'est ce qui
 * permet à une série sans fin de ne jamais réclamer plus d'une alarme, tout en finissant par voir
 * arriver ses occurrences lointaines.
 *
 * Le nombre d'alarmes devient ainsi indépendant des données — donc jamais de plafond atteint, et
 * plus de `PendingIntent` orphelin à traquer, contrairement aux rappels quotidiens qui en posent
 * un par entrée.
 */
object EventAlarmScheduler {

    /** Profondeur de recherche du prochain rappel. */
    private const val WINDOW_MS = 7 * 86_400_000L

    /** Anticipation maximale d'un rappel, alignée sur le plus lointain de [ReminderLabels]. */
    private const val MAX_LEAD_MS = 2 * 86_400_000L

    /**
     * Tolérance autour de l'heure prévue.
     *
     * Doze peut livrer une alarme en retard: on ramasse tout ce qui était dû dans cette fenêtre
     * plutôt que de laisser passer un rappel parce qu'il a manqué son instant de quelques
     * secondes.
     */
    private const val TOLERANCE_MS = 60_000L

    /** Fenêtre pendant laquelle une seconde livraison du même rappel est ignorée. */
    private const val DUPLICATE_WINDOW_MS = 2 * TOLERANCE_MS

    /**
     * Heure à laquelle un rappel de journée entière se compte.
     *
     * Sans ancre, « la veille à 9 h » serait inexprimable: des minutes comptées depuis minuit ne
     * savent dire que « la veille à minuit ». C'est la convention de Google Agenda.
     */
    private const val ALL_DAY_HOUR = 9

    private const val REQUEST_NEXT = 1
    private const val PREFS_NAME = "organisateur"
    private const val FIRED_KEY = "event_reminders_fired"

    /** Recalcule et repose l'unique alarme. Idempotent, appelable de partout. */
    suspend fun rearm(context: Context) {
        val now = System.currentTimeMillis()
        val windowEnd = now + WINDOW_MS
        val next = dueBetween(context, now + 1, windowEnd).minByOrNull { it.triggerAt }
        setAlarm(context, next?.triggerAt ?: minOf(windowEnd, nextMidnight(now)))
    }

    /** Notifie tout ce qui est dû maintenant, puis réarme. Appelé depuis le receiver. */
    suspend fun fireDue(context: Context) {
        val now = System.currentTimeMillis()
        val due = dueBetween(context, now - TOLERANCE_MS, now + TOLERANCE_MS)
        val alreadyFired = firedKeys(context, now)

        for (reminder in due) {
            if (reminder.key in alreadyFired) continue
            EventNotifications.show(context, reminder)
        }
        markFired(context, due.map { it.key }, now)
        rearm(context)
    }

    /** Rappels dus dans `[from, to]`, bornes comprises. */
    private suspend fun dueBetween(context: Context, from: Long, to: Long): List<DueReminder> {
        val db = AppDatabase.getDatabase(context)
        val reminders = db.eventReminderDao().all()
        if (reminders.isEmpty()) return emptyList()

        val byEvent = reminders.groupBy({ it.eventId }, { it.minutesBefore })
        // La fenêtre d'occurrences déborde de l'anticipation maximale: un rappel « deux jours
        // avant » est dû bien avant l'occurrence qui le porte.
        val occurrences = EventRepository(db).occurrences(from, to + MAX_LEAD_MS)

        val due = ArrayList<DueReminder>()
        for (occurrence in occurrences) {
            val minutes = byEvent[occurrence.eventId] ?: continue
            for (minutesBefore in minutes) {
                val triggerAt = anchorOf(occurrence) - minutesBefore * 60_000L
                if (triggerAt in from..to) {
                    due.add(
                        DueReminder(
                            eventId = occurrence.eventId,
                            occurrenceStartUtc = occurrence.occurrenceStartUtc,
                            minutesBefore = minutesBefore,
                            triggerAt = triggerAt,
                            title = occurrence.title
                        )
                    )
                }
            }
        }
        return due
    }

    private fun anchorOf(occurrence: Occurrence): Long = if (occurrence.allDay) {
        val zone = Zones.current()
        Zones.toUtc(Zones.localDate(occurrence.startUtc, zone).atTime(ALL_DAY_HOUR, 0), zone)
    } else {
        occurrence.startUtc
    }

    private fun setAlarm(context: Context, triggerAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Sur API 31+, poser sans vérifier lève une SecurityException que l'on ne peut
        // qu'avaler: la vérifier permet au moins de ne pas prétendre avoir programmé.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context)
            )
        } catch (e: SecurityException) {
            // L'autorisation a pu être retirée entre la vérification et la pose.
            e.printStackTrace()
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_NEXT,
        Intent(context, EventReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextMidnight(now: Long): Long {
        val zone = Zones.current()
        return Zones.dayEnd(Zones.localDate(now, zone), zone)
    }

    /**
     * Clés déjà notifiées récemment.
     *
     * Les entrées expirées sont purgées à la lecture: sans cela, la préférence grossirait à
     * chaque rappel et ne serait jamais nettoyée.
     */
    private fun firedKeys(context: Context, now: Long): Set<String> =
        readFired(context).filterValues { now - it in 0 until DUPLICATE_WINDOW_MS }.keys

    private fun readFired(context: Context): Map<String, Long> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(FIRED_KEY, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('@')
                val instant = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                entry.substring(0, separator) to instant
            }
            .toMap()

    private fun markFired(context: Context, keys: List<String>, now: Long) {
        if (keys.isEmpty()) return
        val kept = readFired(context)
            .filterValues { now - it in 0 until DUPLICATE_WINDOW_MS }
            .toMutableMap()
        for (key in keys) kept[key] = now

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(FIRED_KEY, kept.map { (key, instant) -> "$key@$instant" }.toSet())
            // `commit` et non `apply`: on est dans un receiver, le processus peut être arrêté
            // avant qu'une écriture différée n'atteigne le disque.
            .commit()
    }
}
