package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthManager
import com.example.auth.authStateFlow
import com.example.data.local.AppPreferences
import com.example.data.local.PreferencesRepository
import com.example.firebase.configureAppCheck
import com.example.localization.LocalAppStrings
import com.example.localization.stringsFor
import com.example.ui.components.PdfFlowTopBar
import com.example.ui.navigation.Routes
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CloudBackupScreen
import com.example.ui.screens.CompressorScreen
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.DocumentScannerScreen
import com.example.ui.screens.DownloadManagerScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.GuestMigrationScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.InfoPolicyScreen
import com.example.ui.screens.LanguageSelectionScreen
import com.example.ui.screens.MergeSplitScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfOrganizerScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.PermissionExplanationScreen
import com.example.ui.screens.PremiumPlansScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.screens.UtilitiesScreen
import com.example.ui.theme.PdfFlowTheme
import com.example.viewmodel.PdfFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : FragmentActivity() {
    private var incomingPdfUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        handleViewIntent(intent)

        setContent {
            val context = LocalContext.current
            val prefsRepo = remember { PreferencesRepository(context.applicationContext) }
            val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = AppPreferences())
            val currentUser by remember { Firebase.auth.authStateFlow() }.collectAsStateWithLifecycle(
                initialValue = Firebase.auth.currentUser
            )

            val strings = remember(prefs.language) { stringsFor(prefs.language) }
            val layoutDirection = if (prefs.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalAppStrings provides strings,
                LocalLayoutDirection provides layoutDirection
            ) {
                PdfFlowTheme(themeMode = prefs.themeMode) {
                    val uid = currentUser?.uid
                    key(uid) {
                        val vm: PdfFlowViewModel = viewModel(
                            key = "pf_vm_${uid ?: "guest"}",
                            factory = PdfFlowViewModel.factory(context, currentUser)
                        )
                        PdfFlowRoot(
                            viewModel = vm,
                            currentUser = currentUser,
                            incomingPdfUri = incomingPdfUriState.value,
                            onIncomingPdfConsumed = { incomingPdfUriState.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            incomingPdfUriState.value = intent.data
        }
    }
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun PdfFlowRoot(
    viewModel: PdfFlowViewModel,
    currentUser: FirebaseUser?,
    incomingPdfUri: Uri?,
    onIncomingPdfConsumed: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var permissionStepShown by rememberSaveable { mutableStateOf(false) }
    var unlockedSession by rememberSaveable { mutableStateOf(false) }

    if (!splashDone) {
        SplashScreen(onFinished = { splashDone = true })
        return
    }

    if (!prefs.languageSelected) {
        LanguageSelectionScreen(
            selectedLanguage = prefs.language,
            onSelectLanguage = { viewModel.setLanguage(it, markSelected = false) },
            onContinue = { viewModel.setLanguage(prefs.language, markSelected = true) }
        )
        return
    }

    if (!prefs.onboardingCompleted) {
        OnboardingScreen(onFinish = { viewModel.completeOnboarding() })
        return
    }

    if (currentUser == null && !prefs.guestModeActive) {
        if (!permissionStepShown) {
            PermissionExplanationScreen(onContinue = { permissionStepShown = true })
            return
        }
        AuthScreen(
            onAuthenticated = { viewModel.exitGuestMode() },
            onContinueAsGuest = { viewModel.enterGuestMode() }
        )
        return
    }

    if (prefs.appLockEnabled && !unlockedSession) {
        AppLockScreen(
            expectedPin = prefs.appLockPin,
            biometricEnabled = prefs.biometricEnabled,
            onUnlocked = { unlockedSession = true }
        )
        return
    }

    MainAppShell(
        viewModel = viewModel,
        currentUser = currentUser,
        isGuest = currentUser == null && prefs.guestModeActive,
        incomingPdfUri = incomingPdfUri,
        onIncomingPdfConsumed = onIncomingPdfConsumed
    )
}

@Composable
private fun MainAppShell(
    viewModel: PdfFlowViewModel,
    currentUser: FirebaseUser?,
    isGuest: Boolean,
    incomingPdfUri: Uri?,
    onIncomingPdfConsumed: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val downloads by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val activeDownloadCount = remember(downloads) { downloads.count { it.status == "ACTIVE" } }
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMsg) {
        statusMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(incomingPdfUri) {
        if (incomingPdfUri != null) {
            viewModel.importDocumentUri(incomingPdfUri) { imported ->
                navController.navigate(Routes.reader(imported.id))
            }
            onIncomingPdfConsumed()
        }
    }

    val topLevelTabs = remember(strings) {
        listOf(
            BottomNavTab(Routes.HOME, strings.navHome, Icons.Default.Home),
            BottomNavTab(Routes.TOOLS, strings.navTools, Icons.Default.Build),
            BottomNavTab(Routes.FILES, strings.navFiles, Icons.Default.Folder),
            BottomNavTab(Routes.FAVORITES, strings.navFavorites, Icons.Default.Star),
            BottomNavTab(Routes.SETTINGS, strings.navSettings, Icons.Default.Settings)
        )
    }

    val showMainChrome = currentRoute in topLevelTabs.map { it.route }

    fun switchMainTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = showMainChrome && maxWidth >= 840.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showMainChrome) {
                    PdfFlowTopBar(
                        activeDownloadCount = activeDownloadCount,
                        isGuest = isGuest,
                        onSearchClick = { switchMainTab(Routes.FILES) },
                        onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) },
                        onProfileClick = { switchMainTab(Routes.SETTINGS) }
                    )
                }
            },
            bottomBar = {
                if (showMainChrome && !useNavigationRail) {
                    NavigationBar {
                        topLevelTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { switchMainTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("bottom_nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        topLevelTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { switchMainTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME
                    ) {
                        composable(Routes.HOME) {
                            HomeDashboardScreen(
                                viewModel = viewModel,
                                userDisplayName = currentUser?.displayName ?: currentUser?.phoneNumber,
                                isGuest = isGuest,
                                onNavigate = { navController.navigate(it) },
                                onSwitchTab = ::switchMainTab
                            )
                        }
                        composable(Routes.TOOLS) {
                            ToolsHubScreen(viewModel = viewModel, onNavigate = { navController.navigate(it) })
                        }
                        composable(Routes.FILES) {
                            FilesScreen(viewModel = viewModel, onNavigate = { navController.navigate(it) })
                        }
                        composable(Routes.FAVORITES) {
                            FavoritesScreen(viewModel = viewModel, onNavigate = { navController.navigate(it) })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                viewModel = viewModel,
                                currentUser = currentUser,
                                isGuest = isGuest,
                                onNavigate = { navController.navigate(it) },
                                onSignOutClick = {
                                    AuthManager.signOut(credentialManager, scope) {
                                        viewModel.exitGuestMode()
                                    }
                                },
                                onSwitchToAccountLogin = { viewModel.exitGuestMode() }
                            )
                        }
                        composable(Routes.DOWNLOADS) {
                            DownloadManagerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.READER,
                            arguments = listOf(navArgument("docId") { type = NavType.StringType })
                        ) { entry ->
                            val docId = entry.arguments?.getString("docId").orEmpty()
                            PdfReaderScreen(
                                docId = docId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigate = { navController.navigate(it) }
                            )
                        }
                        composable(
                            route = Routes.EDITOR,
                            arguments = listOf(navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            PdfEditorScreen(
                                initialDocId = entry.arguments?.getString("docId"),
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Routes.ORGANIZER,
                            arguments = listOf(navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            PdfOrganizerScreen(
                                initialDocId = entry.arguments?.getString("docId"),
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Routes.MERGE_SPLIT,
                            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "merge" })
                        ) { entry ->
                            MergeSplitScreen(
                                initialMode = entry.arguments?.getString("mode") ?: "merge",
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Routes.CONVERTER,
                            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "create" })
                        ) { entry ->
                            ConverterScreen(
                                initialMode = entry.arguments?.getString("mode") ?: "create",
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Routes.COMPRESSOR,
                            arguments = listOf(navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            CompressorScreen(
                                initialDocId = entry.arguments?.getString("docId"),
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Routes.SECURITY,
                            arguments = listOf(
                                navArgument("mode") { type = NavType.StringType; defaultValue = "protect" },
                                navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { entry ->
                            SecurityScreen(
                                initialMode = entry.arguments?.getString("mode") ?: "protect",
                                initialDocId = entry.arguments?.getString("docId"),
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.SCANNER) {
                            DocumentScannerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(Routes.OCR) {
                            OcrScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.AI_STUDIO,
                            arguments = listOf(navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            AiStudioScreen(
                                initialDocId = entry.arguments?.getString("docId"),
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Routes.UTILITIES,
                            arguments = listOf(
                                navArgument("tab") { type = NavType.StringType; defaultValue = "compare" },
                                navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { entry ->
                            UtilitiesScreen(
                                initialTab = entry.arguments?.getString("tab") ?: "compare",
                                initialDocId = entry.arguments?.getString("docId"),
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.BACKUP) {
                            CloudBackupScreen(
                                viewModel = viewModel,
                                currentUser = currentUser,
                                onBack = { navController.popBackStack() },
                                onSignInClick = { viewModel.exitGuestMode() }
                            )
                        }
                        composable(Routes.GUEST_MIGRATION) {
                            GuestMigrationScreen(
                                viewModel = viewModel,
                                currentUser = currentUser,
                                onBack = { navController.popBackStack() },
                                onSignInClick = { viewModel.exitGuestMode() }
                            )
                        }
                        composable(Routes.PREMIUM) {
                            PremiumPlansScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.INFO_POLICY,
                            arguments = listOf(navArgument("topic") { type = NavType.StringType; defaultValue = "about" })
                        ) { entry ->
                            InfoPolicyScreen(
                                topic = entry.arguments?.getString("topic") ?: "about",
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
