package com.majdus.organisateur.agenda

import com.majdus.organisateur.data.Event

/**
 * Ce qu'un éditeur produit: les champs modifiables d'un événement, sans identité ni métadonnées.
 *
 * Séparer le brouillon de l'entité évite d'avoir à fabriquer un identifiant ou une date de
 * création pour une saisie qui peut encore être abandonnée, et laisse le dépôt seul juge de ce
 * qu'il faut écrire selon la portée demandée.
 */
data class EventDraft(
    val title: String,
    val startUtc: Long,
    val endUtc: Long,
    val allDay: Boolean = false,
    val description: String = "",
    val location: String = "",
    val colorKey: String = Event.DEFAULT_COLOR,
    /** Règle de répétition RRULE, "" pour un événement unique. */
    val rrule: String = "",
    /** Rappels, en minutes avant le début. */
    val reminders: List<Int> = emptyList()
) {
    val durationMs: Long get() = endUtc - startUtc

    companion object {
        /** Brouillon reprenant un événement existant, pour rouvrir l'éditeur dessus. */
        fun of(event: Event, reminders: List<Int>): EventDraft = EventDraft(
            title = event.title,
            startUtc = event.startUtc,
            endUtc = event.endUtc,
            allDay = event.allDay,
            description = event.description,
            location = event.location,
            colorKey = event.colorKey,
            rrule = event.rrule,
            reminders = reminders
        )
    }
}
