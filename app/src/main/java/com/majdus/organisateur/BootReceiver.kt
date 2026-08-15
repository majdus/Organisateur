package com.majdus.organisateur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Remise en place des alarmes après un événement système.
 *
 * Un redémarrage vide la table des alarmes du système, mais un changement de fuseau ou de date
 * aussi les invalide: l'instant d'un rappel se calcule depuis une heure locale, qui vient de
 * changer de sens.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        AlarmScheduler.rescheduleAll(context)

        // Les rappels d'événement passent par la base, donc par une lecture asynchrone.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EventAlarmScheduler.rearm(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
