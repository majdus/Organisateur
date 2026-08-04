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
    private const val SCHEDULED_KEY = "alarms_scheduled"
    private const val LAST_FIRED_PREFIX = "alarm_last_fired_"

    /**
     * Marge de sécurité: le système peut livrer un réveil quelques millisecondes avant l'heure
     * demandée. Sans marge, la replanification retombe sur la même occurrence (la minute en cours)
     * et l'alarme sonne une deuxième fois. Toute occurrence à moins d'une minute est donc
     * considérée comme celle qui vient de sonner.
     */
    const val MIN_LEAD_MS = 60_000L

    /** Fenêtre pendant laquelle une nouvelle livraison de la même alarme est ignorée. */
    private const val DUPLICATE_WINDOW_MS = 2 * MIN_LEAD_MS

    /**
     * Planifie la prochaine occurrence de l'alarme, strictement après [notBefore].
     * Après un déclenchement, passer `System.currentTimeMillis() + MIN_LEAD_MS` pour garantir
     * que l'occurrence du jour ne soit pas reprogrammée.
     */
    fun schedule(context: Context, alarmText: String, notBefore: Long = System.currentTimeMillis()) {
        val (description, hour, minute) = parse(alarmText) ?: return

        val triggerAt = nextOccurrence(hour, minute, notBefore)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context, alarmText, description)
            )
        } catch (e: SecurityException) {
            // Permission d'alarme exacte non accordée
            e.printStackTrace()
            return
        }
        markScheduled(context, alarmText, true)
    }

    /** Prochaine occurrence quotidienne de [hour]:[minute] strictement après [notBefore]. */
    fun nextOccurrence(
        hour: Int,
        minute: Int,
        notBefore: Long = System.currentTimeMillis()
    ): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = notBefore
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= notBefore) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    fun cancel(context: Context, alarmText: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, alarmText, null))
        markScheduled(context, alarmText, false)
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
            remove(LAST_FIRED_PREFIX + alarmText.hashCode())
            apply()
        }
    }

    fun isEnabled(context: Context, alarmText: String): Boolean {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val disabled = sharedPreferences.getStringSet(DISABLED_KEY, HashSet<String>())!!
        return !disabled.contains(alarmText)
    }

    /**
     * Réarme toutes les alarmes actives et annule les réveils orphelins, c'est-à-dire ceux
     * programmés pour un texte d'alarme qui n'existe plus dans la liste (alarme supprimée ou
     * modifiée sans que le réveil correspondant ait été annulé).
     */
    fun rescheduleAll(context: Context) {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val alarms = sharedPreferences.getStringSet("alarms", HashSet<String>())!!
        val scheduled = HashSet(sharedPreferences.getStringSet(SCHEDULED_KEY, HashSet<String>())!!)

        for (orphan in scheduled - alarms) {
            cancel(context, orphan)
        }
        for (alarm in alarms) {
            if (isEnabled(context, alarm)) {
                schedule(context, alarm)
            }
        }
    }

    /**
     * Vrai si cette alarme a déjà sonné il y a moins de [DUPLICATE_WINDOW_MS]: la livraison
     * en cours est un doublon et ne doit pas resonner.
     */
    fun isDuplicateFiring(context: Context, alarmText: String): Boolean {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val lastFired = sharedPreferences.getLong(LAST_FIRED_PREFIX + alarmText.hashCode(), 0L)
        val elapsed = System.currentTimeMillis() - lastFired
        return elapsed in 0 until DUPLICATE_WINDOW_MS
    }

    fun markFired(context: Context, alarmText: String) {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putLong(LAST_FIRED_PREFIX + alarmText.hashCode(), System.currentTimeMillis())
            commit()
        }
    }

    private fun markScheduled(context: Context, alarmText: String, scheduled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        val all = HashSet(sharedPreferences.getStringSet(SCHEDULED_KEY, HashSet<String>())!!)
        if (scheduled) all.add(alarmText) else all.remove(alarmText)
        with(sharedPreferences.edit()) {
            putStringSet(SCHEDULED_KEY, all)
            apply()
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
