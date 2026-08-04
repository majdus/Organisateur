package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TaskDao {
    /** Les tâches restantes d'abord: une liste se lit par ce qu'il reste à faire. */
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, timestamp ASC")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    suspend fun countPending(): Int

    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
