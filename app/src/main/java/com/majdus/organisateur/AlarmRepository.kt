package com.majdus.organisateur

import android.content.Context
import java.util.Locale

/**
 * Source de vérité unique des rappels. Encapsule le stockage (SharedPreferences) et garde
 * la planification système en cohérence avec la liste affichée.
 */
class AlarmRepository(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Rappels triés par heure, puis par intitulé — l'ordre d'un `Set` n'est pas stable. */
    fun load(): List<Alarm> = storedKeys()
        .mapNotNull { Alarm.fromStorage(it, AlarmScheduler.isEnabled(context, it)) }
        .sortedWith(compareBy({ it.hour }, { it.minute }, { it.label.lowercase(Locale.FRANCE) }))

    fun exists(alarm: Alarm): Boolean = storedKeys().contains(alarm.storageKey)

    /** Retourne `false` si un rappel identique (même intitulé, même heure) existe déjà. */
    fun add(alarm: Alarm): Boolean {
        if (exists(alarm)) return false
        save(storedKeys() + alarm.storageKey)
        AlarmScheduler.setEnabled(context, alarm.storageKey, alarm.enabled)
        return true
    }

    /**
     * Remplace [previous] par [updated]. L'ancien réveil est annulé explicitement: changer
     * l'intitulé ou l'heure change la clé, donc le `PendingIntent`, et un réveil non annulé
     * continuerait à sonner à l'ancienne heure.
     */
    fun update(previous: Alarm, updated: Alarm) {
        if (previous.storageKey == updated.storageKey) {
            setEnabled(updated, updated.enabled)
            return
        }
        AlarmScheduler.forget(context, previous.storageKey)
        save(storedKeys() - previous.storageKey + updated.storageKey)
        AlarmScheduler.setEnabled(context, updated.storageKey, updated.enabled)
    }

    fun delete(alarm: Alarm) {
        AlarmScheduler.forget(context, alarm.storageKey)
        save(storedKeys() - alarm.storageKey)
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        AlarmScheduler.setEnabled(context, alarm.storageKey, enabled)
    }

    /**
     * Remet l'existant d'aplomb:
     * - normalise les entrées héritées (`"Réveil\n7:5"` → `"Réveil\n07:05"`),
     * - supprime les entrées inexploitables (sans intitulé, heure invalide) qui restaient
     *   invisibles dans la liste tout en continuant à sonner.
     *
     * Dans les deux cas le réveil de l'ancienne clé est annulé explicitement: sans cela il
     * continuerait de sonner à l'ancienne heure, sans aucun moyen de l'éteindre depuis l'app.
     * Idempotent: sans entrée à corriger, ne touche à rien.
     */
    fun migrateLegacyEntries() {
        val stored = storedKeys()
        val renamed = LinkedHashMap<String, Alarm>()
        val dropped = HashSet<String>()

        for (key in stored) {
            val alarm = Alarm.fromStorage(key, AlarmScheduler.isEnabled(context, key))
            when {
                alarm == null -> dropped.add(key)
                alarm.storageKey != key -> renamed[key] = alarm
            }
        }
        if (renamed.isEmpty() && dropped.isEmpty()) return

        val migrated = HashSet(stored)
        for (key in dropped) {
            AlarmScheduler.forget(context, key)
            migrated.remove(key)
        }
        for ((legacyKey, alarm) in renamed) {
            AlarmScheduler.forget(context, legacyKey)
            migrated.remove(legacyKey)
            migrated.add(alarm.storageKey)
        }
        save(migrated)
        for (alarm in renamed.values) {
            AlarmScheduler.setEnabled(context, alarm.storageKey, alarm.enabled)
        }
    }

    private fun storedKeys(): Set<String> =
        HashSet(preferences.getStringSet(KEY_ALARMS, emptySet()) ?: emptySet())

    private fun save(keys: Set<String>) {
        // Toujours écrire une nouvelle instance: muter le Set retourné par getStringSet()
        // a un comportement non défini et la valeur peut ne jamais être persistée.
        preferences.edit().putStringSet(KEY_ALARMS, HashSet(keys)).apply()
    }

    companion object {
        private const val PREFS_NAME = "organisateur"
        private const val KEY_ALARMS = "alarms"
    }
}
