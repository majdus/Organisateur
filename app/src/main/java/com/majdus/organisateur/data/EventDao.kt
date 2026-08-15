package com.majdus.organisateur.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface EventDao {
    /**
     * Tout ce qui peut toucher la plage `[startUtc, endUtc)`, séries comprises.
     *
     * Prédicat de chevauchement et non `BETWEEN` sur le début: un événement commencé avant la
     * fenêtre et qui déborde dedans doit remonter, sinon un rendez-vous de trois jours disparaît
     * de ses deux derniers jours. Les bornes sont volontairement strictes des deux côtés — un
     * événement qui s'arrête à l'instant où la plage commence ne la touche pas.
     *
     * Une série est retenue sur la fin de sa *dernière* occurrence: c'est un filtre grossier, à
     * charge du moteur de récurrence de déplier ce qui tombe vraiment dans la plage.
     */
    @Query(
        "SELECT * FROM events WHERE startUtc < :endUtc AND seriesEndUtc > :startUtc " +
                "ORDER BY startUtc ASC, endUtc DESC"
    )
    suspend fun candidates(startUtc: Long, endUtc: Long): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: String): Event?

    /**
     * Lignes dont l'intitulé, le lieu ou les notes contiennent [pattern] (déjà encadré de `%`).
     *
     * `LIKE` est insensible à la casse sur l'ASCII seulement: « reunion » ne trouvera pas
     * « Réunion ». C'est le compromis retenu, une recherche accent-insensible imposant une table
     * FTS et une seconde copie des textes.
     */
    @Query(
        "SELECT * FROM events WHERE title LIKE :pattern OR location LIKE :pattern " +
                "OR description LIKE :pattern"
    )
    suspend fun matching(pattern: String): List<Event>

    /** Occurrences détachées d'une série. `parentId` n'est pas une clé étrangère: rattacher une
     *  occurrence à une autre série ne doit pas dépendre de l'ordre des écritures. */
    @Query("SELECT * FROM events WHERE parentId = :parentId")
    suspend fun childrenOf(parentId: String): List<Event>

    @Query("UPDATE events SET parentId = :newParentId WHERE id = :id")
    suspend fun reparent(id: String, newParentId: String)

    @Insert
    suspend fun insert(event: Event)

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)
}
