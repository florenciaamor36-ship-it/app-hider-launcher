package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppInfo
import com.example.data.LauncherDatabase
import com.example.data.LauncherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LauncherRepository

    init {
        val database = LauncherDatabase.getDatabase(application)
        repository = LauncherRepository(database.launcherDao())
    }

    // List of ALL installed launcher apps on the phone
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Loading status of the application list
    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    // Package names of currently hidden apps (from database)
    val hiddenApps = repository.hiddenAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // General user configurations
    val settings = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // UI States
    val isHideModeActive: StateFlow<Boolean> = settings.map {
        it["is_hide_active"]?.toBoolean() ?: true // Default is hiding active
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val securityPin: StateFlow<String> = settings.map {
        it["security_pin"] ?: "0000" // Default PIN
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0000")

    val authMethod: StateFlow<String> = settings.map {
        it["auth_method"] ?: "PIN" // Default method
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "PIN")

    val securityPassword: StateFlow<String> = settings.map {
        it["security_password"] ?: "admin123" // Default password
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "admin123")

    val decoyPin: StateFlow<String> = settings.map {
        it["decoy_pin"] ?: "9999" // Default decoy PIN
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "9999")

    val decoyPassword: StateFlow<String> = settings.map {
        it["decoy_password"] ?: "decoy123" // Default decoy password
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "decoy123")

    // Temporary reactive state to detect if the user entered via Decoy mode
    private val _isDecoyLogged = MutableStateFlow(false)
    val isDecoyLogged: StateFlow<Boolean> = _isDecoyLogged.asStateFlow()

    val disguiseName: StateFlow<String> = settings.map {
        it["disguise_name"] ?: "App Hider" // Default camouflage app name
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "App Hider")

    val syncEmail: StateFlow<String> = settings.map {
        it["sync_email"] ?: "" // Synced cloud account email, empty if logged out
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val onboardingCompleted: StateFlow<Boolean> = settings.map {
        it["onboarding_completed"]?.toBoolean() ?: false // Default false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val intruderLogs: StateFlow<List<String>> = settings.map {
        val logsStr = it["intruder_logs"] ?: ""
        if (logsStr.isBlank()) emptyList() else logsStr.split("##")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val triggerTaps: StateFlow<Int> = settings.map {
        it["trigger_taps"]?.toIntOrNull() ?: 5 // Default taps required to unlock
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    // Filtered visible apps to display on the launcher home screen
    val visibleApps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        hiddenApps,
        isHideModeActive
    ) { installed, hidden, hideActive ->
        if (hideActive) {
            val hiddenPkgSet = hidden.map { it.packageName }.toSet()
            // Exclude our own app from the list (to prevent infinite loops or redundant launcher icons on home)
            val ourPackage = getApplication<Application>().packageName
            installed.filter { it.packageName != ourPackage && !hiddenPkgSet.contains(it.packageName) }
        } else {
            val ourPackage = getApplication<Application>().packageName
            installed.filter { it.packageName != ourPackage }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val pm = getApplication<Application>().packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val apps = resolveInfos.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    val appName = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.activityInfo.loadIcon(pm)
                    val className = resolveInfo.activityInfo.name
                    AppInfo(packageName, appName, icon, className)
                }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }

                _installedApps.value = apps
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun toggleAppHiddenState(packageName: String, appName: String, shouldHide: Boolean) {
        viewModelScope.launch {
            if (shouldHide) {
                repository.hideApp(packageName, appName)
            } else {
                repository.showApp(packageName)
            }
        }
    }

    fun setHideModeActive(active: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("is_hide_active", active.toString())
        }
    }

    fun updateSecurityPin(newPin: String) {
        viewModelScope.launch {
            repository.saveSetting("security_pin", newPin)
        }
    }

    fun updateAuthMethod(method: String) {
        viewModelScope.launch {
            repository.saveSetting("auth_method", method)
        }
    }

    fun updateSecurityPassword(password: String) {
        viewModelScope.launch {
            repository.saveSetting("security_password", password)
        }
    }

    fun updateTriggerTaps(taps: Int) {
        viewModelScope.launch {
            repository.saveSetting("trigger_taps", taps.toString())
        }
    }

    fun updateDecoyPin(pin: String) {
        viewModelScope.launch {
            repository.saveSetting("decoy_pin", pin)
        }
    }

    fun updateDecoyPassword(password: String) {
        viewModelScope.launch {
            repository.saveSetting("decoy_password", password)
        }
    }

    fun setDecoyLogged(active: Boolean) {
        _isDecoyLogged.value = active
    }

    fun updateDisguiseName(name: String) {
        viewModelScope.launch {
            repository.saveSetting("disguise_name", name)
        }
    }

    fun updateSyncEmail(email: String) {
        viewModelScope.launch {
            repository.saveSetting("sync_email", email)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("onboarding_completed", completed.toString())
        }
    }

    fun addIntruderLog(detail: String) {
        viewModelScope.launch {
            val currentLogsStr = settings.value["intruder_logs"] ?: ""
            val newLog = "${System.currentTimeMillis()}::$detail"
            val updatedLogsStr = if (currentLogsStr.isBlank()) {
                newLog
            } else {
                "$newLog##$currentLogsStr" // Prepend to show latest first
            }
            // Limit to last 20 logs to save storage space
            val limitedLogs = updatedLogsStr.split("##").take(20).joinToString("##")
            repository.saveSetting("intruder_logs", limitedLogs)
        }
    }

    fun clearIntruderLogs() {
        viewModelScope.launch {
            repository.saveSetting("intruder_logs", "")
        }
    }
}
