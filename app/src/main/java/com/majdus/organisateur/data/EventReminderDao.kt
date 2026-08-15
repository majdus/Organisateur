package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventReminderDao {
    @Query("SELECT * FROM event_reminders WHERE eventId = :eventId ORDER BY minutesBefore ASC")
    suspend fun forEvent(eventId: String): List<EventReminder>

    /**
     * Tous les rappels, sans filtre.
     *
     * La planification les croise avec les occurrences d'une fenêtre de sept jours: les filtrer
     * en SQL demanderait de connaître d'avance les séries qui y tombent, ce que seule l'expansion
     * sait dire.
     */
    @Query("SELECT * FROM event_reminders")
    suspend fun all(): List<EventReminder>

    /**
     * Identifiants des événements portant au moins un rappel.
     *
     * La table est minuscule — un rappel ou deux par événement — donc la lire d'un bloc coûte
     * moins qu'une jointure par journée affichée.
     */
    @Query("SELECT DISTINCT eventId FROM event_reminders")
    suspend fun eventIdsWithReminder(): List<String>

    /** Le remplacement évite d'avoir à vérifier l'existence: la clé composite dédoublonne. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<EventReminder>)

    @Query("DELETE FROM event_reminders WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
