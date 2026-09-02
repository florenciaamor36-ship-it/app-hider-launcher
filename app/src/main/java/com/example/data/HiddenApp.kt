package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_apps")
data class HiddenApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)
