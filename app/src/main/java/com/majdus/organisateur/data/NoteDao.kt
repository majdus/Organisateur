package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {
    /** La note touchée le plus récemment en tête, comme dans un carnet qu'on reprend. */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): Note?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countNotes(): Int

    @Insert
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}
