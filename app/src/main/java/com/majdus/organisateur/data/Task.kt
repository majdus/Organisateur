package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String,
    var isCompleted: Boolean = false,
    var timestamp: Long = System.currentTimeMillis()
)
