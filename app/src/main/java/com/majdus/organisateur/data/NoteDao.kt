package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface NoteDao {
    /**
     * Les notes dans l'ordre voulu par l'utilisateur. Le départage par identifiant évite tout
     * ordre indéterminé si deux rangs venaient à se valoir.
     *
     * Le corps est volontairement tronqué: SQLite renvoie ses lignes par une fenêtre de 2 Mo,
     * et une seule note démesurée suffirait à faire échouer la requête — donc à rendre tout
     * l'écran inaccessible, y compris les autres notes. L'aperçu affiché est bien plus court
     * que cette borne de toute façon.
     */
    @Query(
        "SELECT id, title, substr(bodyAst, 1, 4000) AS bodyAst, color, createdAt, updatedAt, " +
                "position, type, substr(items, 1, 4000) AS items " +
                "FROM notes ORDER BY position ASC, id ASC"
    )
    suspend fun getAllNotesForList(): List<Note>

    /** Métadonnées seules: sûr quelle que soit la taille du corps. */
    @Query(
        "SELECT id, title, '' AS bodyAst, color, createdAt, updatedAt, position, " +
                "type, '' AS items FROM notes WHERE id = :id"
    )
    suspend fun getMetadata(id: String): Note?

    @Query("SELECT length(bodyAst) FROM notes WHERE id = :id")
    suspend fun bodyLength(id: String): Int?

    /** Tranche de corps, lue à part pour ne jamais dépasser la fenêtre du curseur. */
    @Query("SELECT substr(bodyAst, :start, :count) FROM notes WHERE id = :id")
    suspend fun bodySlice(id: String, start: Int, count: Int): String?

    @Query("SELECT length(items) FROM notes WHERE id = :id")
    suspend fun itemsLength(id: String): Int?

    /** Même précaution pour la liste: rien n'interdit une liste de courses interminable. */
    @Query("SELECT substr(items, :start, :count) FROM notes WHERE id = :id")
    suspend fun itemsSlice(id: String, start: Int, count: Int): String?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countNotes(): Int

    /** Rang le plus haut de la grille: une note créée se place juste avant, donc en tête. */
    @Query("SELECT MIN(position) FROM notes")
    suspend fun minPosition(): Int?

    /**
     * Écriture du seul rang, sans passer par l'entière entité: un `@Update` obligerait à charger
     * le corps en mémoire, qui peut peser plusieurs mégaoctets.
     */
    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    /**
     * Réindexation dense de la grille après un déplacement, en une seule transaction.
     *
     * Réécrire tous les rangs plutôt que le seul déplacé coûte quelques dizaines de mises à jour
     * d'entiers — négligeable à cette échelle — et garantit qu'aucune dérive ne peut s'installer
     * dans la numérotation au fil des déplacements.
     */
    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }

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
