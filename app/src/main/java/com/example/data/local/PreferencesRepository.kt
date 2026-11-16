package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pdfFlowDataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_flow_prefs")

data class AppPreferences(
    val languageSelected: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val guestModeActive: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val defaultViewerMode: String = "continuous",
    val defaultPageLayout: String = "fit_width",
    val appLockEnabled: Boolean = false,
    val appLockPin: String = "",
    val biometricEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val downloadSubfolder: String = "PDF Flow",
    val quickAccessToolIds: List<String> = listOf("create", "scan", "merge", "compress", "convert", "edit"),
    val recentToolIds: List<String> = emptyList(),
    val planTier: String = "free"
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE_SELECTED = booleanPreferencesKey("language_selected")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val GUEST_MODE_ACTIVE = booleanPreferencesKey("guest_mode_active")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_VIEWER_MODE = stringPreferencesKey("default_viewer_mode")
        val DEFAULT_PAGE_LAYOUT = stringPreferencesKey("default_page_layout")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val DOWNLOAD_SUBFOLDER = stringPreferencesKey("download_subfolder")
        val QUICK_ACCESS_TOOLS = stringPreferencesKey("quick_access_tools")
        val RECENT_TOOLS = stringPreferencesKey("recent_tools")
        val PLAN_TIER = stringPreferencesKey("plan_tier")
    }

    val preferences: Flow<AppPreferences> = context.pdfFlowDataStore.data.map { prefs ->
        val quickRaw = prefs[Keys.QUICK_ACCESS_TOOLS]
        val recentRaw = prefs[Keys.RECENT_TOOLS]
        AppPreferences(
            languageSelected = prefs[Keys.LANGUAGE_SELECTED] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            guestModeActive = prefs[Keys.GUEST_MODE_ACTIVE] ?: false,
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE_CODE]),
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.storageKey),
            defaultViewerMode = prefs[Keys.DEFAULT_VIEWER_MODE] ?: "continuous",
            defaultPageLayout = prefs[Keys.DEFAULT_PAGE_LAYOUT] ?: "fit_width",
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            appLockPin = prefs[Keys.APP_LOCK_PIN] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            downloadSubfolder = prefs[Keys.DOWNLOAD_SUBFOLDER] ?: "PDF Flow",
            quickAccessToolIds = quickRaw?.split(",")?.filter { it.isNotBlank() }
                ?: listOf("create", "scan", "merge", "compress", "convert", "edit"),
            recentToolIds = recentRaw?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            planTier = prefs[Keys.PLAN_TIER] ?: "free"
        )
    }

    suspend fun setLanguage(language: AppLanguage, markSelected: Boolean = true) {
        context.pdfFlowDataStore.edit {
            it[Keys.LANGUAGE_CODE] = language.code
            if (markSelected) it[Keys.LANGUAGE_SELECTED] = true
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.pdfFlowDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setGuestMode(active: Boolean) {
        context.pdfFlowDataStore.edit { it[Keys.GUEST_MODE_ACTIVE] = active }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.pdfFlowDataStore.edit { it[Keys.THEME_MODE] = mode.storageKey }
    }

    suspend fun setDefaultViewerMode(mode: String) {
        context.pdfFlowDataStore.edit { it[Keys.DEFAULT_VIEWER_MODE] = mode }
    }

    suspend fun setDefaultPageLayout(layout: String) {
        context.pdfFlowDataStore.edit { it[Keys.DEFAULT_PAGE_LAYOUT] = layout }
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        context.pdfFlowDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = enabled
            if (pin != null) it[Keys.APP_LOCK_PIN] = pin
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.pdfFlowDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.pdfFlowDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.pdfFlowDataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setDownloadSubfolder(subfolder: String) {
        val sanitized = subfolder.trim().replace(Regex("[/\\\\:*?\"<>|]"), "").ifBlank { "PDF Flow" }
        context.pdfFlowDataStore.edit { it[Keys.DOWNLOAD_SUBFOLDER] = sanitized }
    }

    suspend fun updateQuickAccessTools(ids: List<String>) {
        context.pdfFlowDataStore.edit { it[Keys.QUICK_ACCESS_TOOLS] = ids.distinct().take(8).joinToString(",") }
    }

    suspend fun recordToolUsage(toolId: String) {
        context.pdfFlowDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_TOOLS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(toolId) + current.filter { it != toolId }).take(6)
            prefs[Keys.RECENT_TOOLS] = updated.joinToString(",")
        }
    }

    suspend fun setPlanTier(tier: String) {
        context.pdfFlowDataStore.edit { it[Keys.PLAN_TIER] = tier }
    }
}
