package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Un rappel d'événement, exprimé en minutes avant le début.
 *
 * Clé primaire composite plutôt qu'un identifiant propre: deux rappels identiques sur le même
 * événement n'existent pas, et la déduplication devient gratuite. La suppression en cascade
 * évite d'avoir à penser au ménage à chaque suppression d'événement.
 */
@Entity(
    tableName = "event_reminders",
    primaryKeys = ["eventId", "minutesBefore"],
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EventReminder(
    val eventId: String,
    /** 0 = à l'heure dite. Pour une journée entière, compté depuis 9 h le jour du début. */
    val minutesBefore: Int
) {
    companion object {
        /** À l'heure dite: le seul rappel que connaissait la version précédente. */
        const val AT_START = 0
    }
}
