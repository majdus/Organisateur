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
 * [position] donne l'ordre voulu par l'utilisateur dans la grille.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var bodyAst: String = "[]",
    var color: String = "default",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    /**
     * Rang relatif, pas absolu: seul l'ordre croissant compte. Entre deux réorganisations, les
     * trous et les valeurs négatives sont normaux — une note créée prend le rang du minimum moins
     * un pour se poser en tête, sans avoir à réécrire toutes les autres lignes.
     */
    var position: Int = 0
)
