package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Une occurrence retirée d'une série (l'EXDATE d'iCalendar).
 *
 * Une occurrence *modifiée* produit elle aussi une ligne ici, en plus de sa ligne détachée dans
 * `events`. Le doublon est volontaire: l'expansion n'a alors qu'une seule source à consulter pour
 * savoir quoi retirer, et surtout supprimer l'occurrence détachée ne la fait pas réapparaître
 * depuis la série mère.
 */
@Entity(
    tableName = "event_exceptions",
    primaryKeys = ["eventId", "originalStartUtc"],
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EventException(
    /** Série mère, jamais l'occurrence détachée. */
    val eventId: String,
    /** Début qu'aurait eu l'occurrence dans la série: c'est son identité. */
    val originalStartUtc: Long
)
