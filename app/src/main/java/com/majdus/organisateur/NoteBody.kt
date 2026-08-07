package com.majdus.organisateur

import com.majdus.organisateur.data.Note
import com.majdus.organisateur.data.NoteDao

/**
 * Lecture d'une note entière, quelle que soit la taille de son corps.
 *
 * SQLite rend ses lignes par une fenêtre de 2 Mo: au-delà, `SELECT *` lève
 * `SQLiteBlobTooBigException`. Le corps est donc relu par tranches, chacune très en deçà de
 * cette borne, puis recollé. Une note d'un million de caractères se relit ainsi sans incident.
 *
 * La liste à cocher passe par le même chemin: elle est bien plus courte en pratique, mais rien
 * n'oblige l'utilisateur à s'en tenir là, et une note illisible est une note perdue.
 */
suspend fun NoteDao.loadFullNote(id: String): Note? {
    val note = getMetadata(id) ?: return null
    return note.copy(
        bodyAst = readInSlices(bodyLength(id)) { start, count -> bodySlice(id, start, count) },
        items = readInSlices(itemsLength(id)) { start, count -> itemsSlice(id, start, count) }
    )
}

/**
 * Recolle une colonne de texte tranche par tranche. Renvoie une chaîne vide pour une colonne
 * vide: les deux lecteurs de corps la traitent comme un contenu absent.
 */
private suspend fun readInSlices(length: Int?, slice: suspend (Int, Int) -> String?): String {
    val total = length ?: 0
    if (total == 0) return ""

    val builder = StringBuilder(total)
    var offset = 1 // substr() compte à partir de 1
    while (offset <= total) {
        builder.append(slice(offset, SLICE_LENGTH) ?: break)
        offset += SLICE_LENGTH
    }
    return builder.toString()
}

/** 200 000 caractères, soit environ 400 Ko: cinq fois sous la limite de la fenêtre. */
private const val SLICE_LENGTH = 200_000
