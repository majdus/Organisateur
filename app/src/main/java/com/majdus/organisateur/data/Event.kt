package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var date: String, // Format: "yyyy-MM-dd"
    var title: String,
    var hour: Int = 0,
    var minute: Int = 0,
    var hasNotification: Boolean = false,
    var timestamp: Long = System.currentTimeMillis()
)
