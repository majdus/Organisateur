package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface TaskDao {
    /**
     * Les tâches restantes d'abord: une liste se lit par ce qu'il reste à faire. À l'intérieur
     * de chaque groupe, l'ordre est celui que l'utilisateur a donné en déplaçant ses cartes, le
     * départage par identifiant évitant tout ordre indéterminé si deux rangs se valaient.
     */
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, position ASC, id ASC")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    suspend fun countPending(): Int

    /** Dernier rang de la liste: une tâche créée se pose juste après, donc en fin de groupe. */
    @Query("SELECT MAX(position) FROM tasks")
    suspend fun maxPosition(): Int?

    /**
     * Premier rang de la liste: une tâche créée se pose juste avant quand le réglage veut la voir
     * en tête. Le rang obtenu est négatif dès la deuxième création, ce que l'ordre relatif admet
     * sans rien réindexer.
     */
    @Query("SELECT MIN(position) FROM tasks")
    suspend fun minPosition(): Int?

    @Query("UPDATE tasks SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    /**
     * Réindexation dense de la liste après un déplacement, en une seule transaction. Réécrire
     * tous les rangs plutôt que le seul déplacé coûte quelques mises à jour d'entiers et évite
     * qu'une dérive s'installe dans la numérotation au fil des déplacements.
     */
    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
