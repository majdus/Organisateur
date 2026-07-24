package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String,
    var timestamp: Long = System.currentTimeMillis()
)
