package com.majdus.organisateur

import com.majdus.organisateur.data.Note
import com.majdus.organisateur.data.NoteDao

/**
 * Lecture d'une note entière, quelle que soit la taille de son corps.
 *
 * SQLite rend ses lignes par une fenêtre de 2 Mo: au-delà, `SELECT *` lève
 * `SQLiteBlobTooBigException`. Le corps est donc relu par tranches, chacune très en deçà de
 * cette borne, puis recollé. Une note d'un million de caractères se relit ainsi sans incident.
 */
suspend fun NoteDao.loadFullNote(id: String): Note? {
    val note = getMetadata(id) ?: return null
    val length = bodyLength(id) ?: 0
    if (length == 0) return note

    val body = StringBuilder(length)
    var offset = 1 // substr() compte à partir de 1
    while (offset <= length) {
        body.append(bodySlice(id, offset, SLICE_LENGTH) ?: break)
        offset += SLICE_LENGTH
    }
    return note.copy(bodyAst = body.toString())
}

/** 200 000 caractères, soit environ 400 Ko: cinq fois sous la limite de la fenêtre. */
private const val SLICE_LENGTH = 200_000
