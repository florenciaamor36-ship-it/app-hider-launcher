package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {
    // Hidden Apps
    @Query("SELECT * FROM hidden_apps ORDER BY timestamp DESC")
    fun getHiddenAppsFlow(): Flow<List<HiddenApp>>

    @Query("SELECT packageName FROM hidden_apps")
    suspend fun getHiddenPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenApp(app: HiddenApp)

    @Query("DELETE FROM hidden_apps WHERE packageName = :packageName")
    suspend fun deleteHiddenAppByPackage(packageName: String)

    // General Settings
    @Query("SELECT value FROM launcher_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Query("SELECT * FROM launcher_settings")
    fun getSettingsFlow(): Flow<List<LauncherSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: LauncherSetting)
}
