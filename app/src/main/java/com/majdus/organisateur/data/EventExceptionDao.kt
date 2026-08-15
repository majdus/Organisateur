package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventExceptionDao {
    /**
     * Toutes les exceptions, sans filtre de plage.
     *
     * Elles se comptent en dizaines: les charger d'un bloc et les indexer en mémoire évite une
     * requête par série à déplier.
     */
    @Query("SELECT * FROM event_exceptions")
    suspend fun all(): List<EventException>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exception: EventException)

    @Delete
    suspend fun delete(exception: EventException)
}
