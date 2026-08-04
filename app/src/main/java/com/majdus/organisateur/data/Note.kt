package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Une note.
 *
 * [bodyAst] est le corps sérialisé au format AST de `RichTextParser` — le même format que celui
 * déjà utilisé par l'ancienne note unique, ce qui permet de la reprendre telle quelle.
 * [color] est une clé de palette ("default", "yellow", …), pas une valeur ARGB: la teinte exacte
 * reste du ressort du thème et peut être ajustée sans toucher aux données.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var bodyAst: String = "[]",
    var color: String = "default",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)
