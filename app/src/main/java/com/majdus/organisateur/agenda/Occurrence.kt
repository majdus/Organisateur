package com.majdus.organisateur.agenda

/**
 * Un événement à une date précise: récurrence déjà dépliée, ancrage déjà résolu.
 *
 * C'est ce que les vues consomment, jamais l'entité. [startUtc] et [endUtc] sont des instants
 * d'affichage — pour une journée entière, la date flottante a déjà été ramenée à minuit local —
 * de sorte qu'une grille horaire n'ait jamais à connaître [allDay] pour calculer une position,
 * seulement pour choisir où poser le bloc.
 */
data class Occurrence(
    val eventId: String,
    /**
     * Début de l'occurrence dans sa série.
     *
     * C'est son identité: la clé sous laquelle une exception la retire, et sous laquelle une
     * occurrence détachée la remplace. Égal à [startUtc] tant que rien n'a été déplacé.
     */
    val occurrenceStartUtc: Long,
    val startUtc: Long,
    /** Fin **exclusive**. */
    val endUtc: Long,
    val allDay: Boolean,
    val title: String,
    val location: String,
    val colorKey: String,
    val isRecurring: Boolean,
    val isDetached: Boolean
) {
    val durationMs: Long get() = endUtc - startUtc

    /**
     * Vrai si l'occurrence touche la plage `[rangeStart, rangeEnd)`.
     *
     * Les deux bornes sont strictes: un événement qui s'arrête à l'instant où la plage commence
     * ne la touche pas, et un événement qui commence à l'instant où elle finit non plus. C'est ce
     * qui empêche un rendez-vous terminé à minuit d'apparaître aussi le lendemain.
     */
    fun overlaps(rangeStart: Long, rangeEnd: Long): Boolean =
        startUtc < rangeEnd && endUtc > rangeStart
}
