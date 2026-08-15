package com.majdus.organisateur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Réveil de l'unique alarme des rappels d'événement.
 *
 * Le receiver ne porte plus l'événement dans ses extras: il interroge la base au déclenchement.
 * C'est ce qui permet à une seule alarme de servir toutes les occurrences, y compris celles d'une
 * série qui n'existaient pas encore quand elle a été posée.
 */
class EventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Lire la base et réarmer dépasse le temps accordé à `onReceive`: `goAsync` maintient le
        // processus en vie le temps du travail.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EventAlarmScheduler.fireDue(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
