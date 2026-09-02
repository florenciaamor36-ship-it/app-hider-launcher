package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HiddenApp::class, LauncherSetting::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherDao(): LauncherDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        fun getDatabase(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "launcher_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
