package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {
    /**
     * La note touchée le plus récemment en tête, comme dans un carnet qu'on reprend.
     *
     * Le corps est volontairement tronqué: SQLite renvoie ses lignes par une fenêtre de 2 Mo,
     * et une seule note démesurée suffirait à faire échouer la requête — donc à rendre tout
     * l'écran inaccessible, y compris les autres notes. L'aperçu affiché est bien plus court
     * que cette borne de toute façon.
     */
    @Query(
        "SELECT id, title, substr(bodyAst, 1, 4000) AS bodyAst, color, createdAt, updatedAt " +
                "FROM notes ORDER BY updatedAt DESC"
    )
    suspend fun getAllNotesForList(): List<Note>

    /** Métadonnées seules: sûr quelle que soit la taille du corps. */
    @Query("SELECT id, title, '' AS bodyAst, color, createdAt, updatedAt FROM notes WHERE id = :id")
    suspend fun getMetadata(id: String): Note?

    @Query("SELECT length(bodyAst) FROM notes WHERE id = :id")
    suspend fun bodyLength(id: String): Int?

    /** Tranche de corps, lue à part pour ne jamais dépasser la fenêtre du curseur. */
    @Query("SELECT substr(bodyAst, :start, :count) FROM notes WHERE id = :id")
    suspend fun bodySlice(id: String, start: Int, count: Int): String?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countNotes(): Int

    @Insert
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    /** Suppression sans relire la ligne: le corps peut peser plusieurs mégaoctets. */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
