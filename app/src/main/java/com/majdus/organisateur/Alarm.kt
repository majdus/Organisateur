package com.majdus.organisateur

import java.util.Locale

/**
 * Un rappel quotidien.
 *
 * [storageKey] est la forme persistée (`"intitulé\nHH:mm"`). C'est aussi la clé d'identité
 * utilisée par [AlarmScheduler] pour l'état activé/désactivé et pour le `PendingIntent`:
 * deux rappels sont le même dès lors qu'ils ont le même intitulé et la même heure.
 */
data class Alarm(
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
) {
    val timeText: String
        get() = String.format(Locale.FRANCE, "%02d:%02d", hour, minute)

    val storageKey: String
        get() = "$label\n$timeText"

    /** Instant du prochain déclenchement, que le rappel soit actif ou non. */
    fun nextTriggerAt(from: Long = System.currentTimeMillis()): Long =
        AlarmScheduler.nextOccurrence(hour, minute, from)

    companion object {
        const val MAX_LABEL_LENGTH = 60

        /** Nettoie une saisie utilisateur: pas de retour à la ligne (séparateur de la forme persistée). */
        fun sanitizeLabel(raw: String): String =
            raw.replace('\n', ' ').replace('\r', ' ').trim().take(MAX_LABEL_LENGTH)

        /**
         * Relit une entrée stockée. Tolère le format hérité non normalisé (`"Réveil\n7:5"`).
         * Retourne `null` si l'entrée est inexploitable.
         */
        fun fromStorage(storedText: String, enabled: Boolean): Alarm? {
            val (label, hour, minute) = AlarmScheduler.parse(storedText) ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            val cleanLabel = sanitizeLabel(label)
            if (cleanLabel.isEmpty()) return null
            return Alarm(cleanLabel, hour, minute, enabled)
        }
    }
}
