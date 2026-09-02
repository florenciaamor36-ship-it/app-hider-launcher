package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LauncherRepository(private val launcherDao: LauncherDao) {

    val hiddenAppsFlow: Flow<List<HiddenApp>> = launcherDao.getHiddenAppsFlow()
    val settingsFlow: Flow<Map<String, String>> = launcherDao.getSettingsFlow().map { list ->
        list.associate { it.key to it.value }
    }

    suspend fun getHiddenPackageNames(): List<String> = launcherDao.getHiddenPackageNames()

    suspend fun hideApp(packageName: String, appName: String) {
        launcherDao.insertHiddenApp(HiddenApp(packageName, appName))
    }

    suspend fun showApp(packageName: String) {
        launcherDao.deleteHiddenAppByPackage(packageName)
    }

    suspend fun getSetting(key: String, defaultValue: String): String {
        return launcherDao.getSetting(key) ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        launcherDao.saveSetting(LauncherSetting(key, value))
    }
}
