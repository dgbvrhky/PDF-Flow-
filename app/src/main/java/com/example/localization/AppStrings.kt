package com.example.localization

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean = false
) {
    ENGLISH("en", "English", "English", false),
    BENGALI("bn", "বাংলা", "Bengali", false),
    HINDI("hi", "हिन्दी", "Hindi", false),
    ARABIC("ar", "العربية", "Arabic", true),
    SPANISH("es", "Español", "Spanish", false),
    FRENCH("fr", "Français", "French", false),
    PORTUGUESE("pt", "Português", "Portuguese", false),
    URDU("ur", "اردو", "Urdu", true),
    CHINESE("zh", "简体中文", "Chinese (Simplified)", false),
    JAPANESE("ja", "日本語", "Japanese", false);

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class AppStrings(
    val appName: String,
    val tagline: String,
    val selectLanguageTitle: String,
    val selectLanguageSubtitle: String,
    val continueAction: String,
    val skipAction: String,
    val nextAction: String,
    val getStartedAction: String,
    val backAction: String,
    val cancelAction: String,
    val saveAction: String,
    val deleteAction: String,
    val shareAction: String,
    val openAction: String,
    val retryAction: String,
    val searchPlaceholder: String,
    // Onboarding
    val onboardReadTitle: String,
    val onboardReadDesc: String,
    val onboardEditTitle: String,
    val onboardEditDesc: String,
    val onboardConvertTitle: String,
    val onboardConvertDesc: String,
    val onboardScanTitle: String,
    val onboardScanDesc: String,
    val onboardManageTitle: String,
    val onboardManageDesc: String,
    val onboardSecureTitle: String,
    val onboardSecureDesc: String,
    // Auth & Guest
    val authWelcomeTitle: String,
    val authWelcomeSubtitle: String,
    val continueWithGoogle: String,
    val continueWithPhone: String,
    val continueAsGuest: String,
    val guestWarning: String,
    val phoneNumberLabel: String,
    val sendOtpAction: String,
    val enterOtpLabel: String,
    val verifyOtpAction: String,
    val resendOtpAction: String,
    val countryCodeLabel: String,
    // Navigation
    val navHome: String,
    val navTools: String,
    val navFiles: String,
    val navFavorites: String,
    val navSettings: String,
    // Home Dashboard
    val welcomeMorning: String,
    val recentDocuments: String,
    val quickAccess: String,
    val featuredTools: String,
    val favoriteDocuments: String,
    val storageUsage: String,
    val recentlyUsedTools: String,
    val premiumCardTitle: String,
    val premiumCardSubtitle: String,
    val upgradeNow: String,
    val customizeQuickAccess: String,
    // Core Tools
    val toolReader: String,
    val toolCreatePdf: String,
    val toolScanDoc: String,
    val toolImportPdf: String,
    val toolMergePdf: String,
    val toolSplitPdf: String,
    val toolCompressPdf: String,
    val toolConvertPdf: String,
    val toolEditPdf: String,
    val toolImageToPdf: String,
    val toolPdfToImage: String,
    val toolProtectPdf: String,
    val toolUnlockPdf: String,
    val toolOcr: String,
    val toolAiAssistant: String,
    val toolComparePdf: String,
    val toolMetadata: String,
    val toolRepairPdf: String,
    val toolExtractImages: String,
    val toolBatchProcess: String,
    val toolWatermark: String,
    val toolPageNumbers: String,
    val toolDownloadManager: String,
    // Files & Downloads
    val allFiles: String,
    val browseDeviceStorage: String,
    val downloadsFolder: String,
    val customFolders: String,
    val recentlyDeleted: String,
    val sortByName: String,
    val sortByDate: String,
    val sortBySize: String,
    val emptyFilesTitle: String,
    val emptyFilesSubtitle: String,
    val emptyFavoritesTitle: String,
    val emptyFavoritesSubtitle: String,
    val saveAndDownload: String,
    val downloadPdf: String,
    val saveToDownloads: String,
    val chooseFolder: String,
    val activeDownloads: String,
    val completedDownloads: String,
    val failedDownloads: String,
    // Cloud Backup & Migration
    val cloudBackup: String,
    val manualBackup: String,
    val restoreBackup: String,
    val migrateGuestData: String,
    val backupHistory: String,
    // Settings
    val settingsLanguage: String,
    val settingsTheme: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val settingsAccount: String,
    val settingsSignOut: String,
    val settingsAppLock: String,
    val settingsBiometric: String,
    val settingsDefaultViewer: String,
    val settingsDefaultLayout: String,
    val settingsDownloadLocation: String,
    val settingsNotifications: String,
    val settingsClearCache: String,
    val settingsPrivacyPolicy: String,
    val settingsTerms: String,
    val settingsHelp: String,
    val settingsAbout: String,
    val settingsVersion: String,
    // Permissions & AI
    val permissionExplainTitle: String,
    val permissionExplainDesc: String,
    val grantPermissions: String,
    val aiSummary: String,
    val aiAskQuestions: String,
    val aiExtractInfo: String,
    val aiTranslateText: String,
    val aiGenerateNotes: String
)

val LocalAppStrings = staticCompositionLocalOf { englishStrings() }

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.ENGLISH -> englishStrings()
    AppLanguage.BENGALI -> bengaliStrings()
    AppLanguage.HINDI -> hindiStrings()
    AppLanguage.ARABIC -> arabicStrings()
    AppLanguage.SPANISH -> spanishStrings()
    AppLanguage.FRENCH -> frenchStrings()
    AppLanguage.PORTUGUESE -> portugueseStrings()
    AppLanguage.URDU -> urduStrings()
    AppLanguage.CHINESE -> chineseStrings()
    AppLanguage.JAPANESE -> japaneseStrings()
}
