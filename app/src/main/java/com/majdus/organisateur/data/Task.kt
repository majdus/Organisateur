package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Une tâche.
 *
 * [position] donne l'ordre voulu par l'utilisateur, par importance. C'est un rang relatif et non
 * absolu: seul l'ordre croissant compte, les trous sont normaux entre deux réorganisations. Il ne
 * décide pas seul de la place dans la liste — [isCompleted] passe avant, les tâches terminées
 * restant groupées en bas quel que soit leur rang.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String,
    var isCompleted: Boolean = false,
    var timestamp: Long = System.currentTimeMillis(),
    var position: Int = 0
)
