package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "launcher_settings")
data class LauncherSetting(
    @PrimaryKey val key: String,
    val value: String
)
