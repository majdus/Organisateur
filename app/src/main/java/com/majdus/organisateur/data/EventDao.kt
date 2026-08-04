package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface EventDao {
    /** Ordre chronologique dans la journée, pas ordre de saisie. */
    @Query("SELECT * FROM events WHERE date = :selectedDate ORDER BY hour ASC, minute ASC, timestamp ASC")
    suspend fun getEventsByDate(selectedDate: String): List<Event>

    @Query("SELECT COUNT(*) FROM events WHERE date = :selectedDate")
    suspend fun countByDate(selectedDate: String): Int

    /** Dates portant au moins un événement, pour les pastilles du calendrier. */
    @Query("SELECT DISTINCT date FROM events WHERE date BETWEEN :start AND :end")
    suspend fun getDatesBetween(start: String, end: String): List<String>

    @Query("SELECT COUNT(*) FROM events WHERE date BETWEEN :start AND :end")
    suspend fun countBetween(start: String, end: String): Int

    @Insert
    suspend fun insert(event: Event)

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)
}
