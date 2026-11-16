package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import com.example.auth.AuthManager
import com.example.auth.SUPPORTED_COUNTRIES
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.components.PdfFlowLogoImage
import com.example.ui.theme.FlowAmber
import com.example.ui.theme.FlowCrimson
import com.example.ui.theme.FlowEmerald
import com.example.ui.theme.FlowIndigo
import com.example.ui.theme.FlowRoyalBlue
import com.example.ui.theme.FlowTeal
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val strings = LocalAppStrings.current
    var started by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.72f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "splash_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        label = "splash_alpha"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(1250L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF3F6FF), Color(0xFFEDE9FF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .padding(32.dp)
        ) {
            PdfFlowLogoImage(size = 112.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFF111827),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.titleMedium,
                color = FlowIndigo
            )
        }
    }
}

@Composable
fun LanguageSelectionScreen(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onContinue: () -> Unit
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdfFlowLogoImage(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(strings.selectLanguageTitle, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        strings.selectLanguageSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(AppLanguage.entries) { lang ->
                    val isSelected = lang == selectedLanguage
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLanguage(lang) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium
                            )
                            .testTag("lang_card_${lang.code}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (lang.isRtl) "${lang.englishName} • RTL" else lang.englishName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("lang_continue_button")
            ) {
                Text(strings.continueAction, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private data class OnboardingPageData(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val pages = remember(strings) {
        listOf(
            OnboardingPageData(strings.onboardReadTitle, strings.onboardReadDesc, Icons.Default.MenuBook, FlowIndigo),
            OnboardingPageData(strings.onboardEditTitle, strings.onboardEditDesc, Icons.Default.EditNote, FlowRoyalBlue),
            OnboardingPageData(strings.onboardConvertTitle, strings.onboardConvertDesc, Icons.Default.SwapHoriz, FlowTeal),
            OnboardingPageData(strings.onboardScanTitle, strings.onboardScanDesc, Icons.Default.DocumentScanner, FlowCrimson),
            OnboardingPageData(strings.onboardManageTitle, strings.onboardManageDesc, Icons.Default.FolderCopy, FlowAmber),
            OnboardingPageData(strings.onboardSecureTitle, strings.onboardSecureDesc, Icons.Default.Security, FlowEmerald)
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdfFlowLogoImage(size = 34.dp)
                Spacer(Modifier.width(8.dp))
                Text(strings.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onFinish, modifier = Modifier.testTag("onboard_skip_button")) {
                Text(strings.skipAction)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIdx ->
            val page = pages[pageIdx]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(
                            Brush.linearGradient(listOf(page.accent, page.accent.copy(alpha = 0.65f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, contentDescription = page.title, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = page.desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pages.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (active) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (pagerState.currentPage < pages.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinish()
                }
            },
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(52.dp)
                .testTag("onboard_next_button")
        ) {
            Text(
                text = if (pagerState.currentPage < pages.lastIndex) strings.nextAction else strings.getStartedAction,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun PermissionExplanationScreen(onContinue: () -> Unit) {
    val strings = LocalAppStrings.current
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PdfFlowLogoImage(size = 64.dp)
            Text(strings.permissionExplainTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                strings.permissionExplainDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = FlowCrimson)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Camera", fontWeight = FontWeight.SemiBold)
                            Text("Used only when you open the Document Scanner.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = FlowIndigo)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Notifications", fontWeight = FontWeight.SemiBold)
                            Text("Alerts you when PDF downloads or backups finish.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(
                onClick = { launcher.launch(permissionsToRequest) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("permissions_grant_button")
            ) {
                Text(strings.grantPermissions)
            }
            TextButton(onClick = onContinue, modifier = Modifier.testTag("permissions_skip_button")) {
                Text(strings.skipAction)
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showPhoneForm by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(SUPPORTED_COUNTRIES.first()) }
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpCode by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1000L)
            resendSeconds--
        }
    }

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthenticated,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            PdfFlowLogoImage(size = 84.dp)
            Text(strings.authWelcomeTitle, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(
                strings.authWelcomeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            errorMsg?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // A. Google Login (Credential Manager)
            Button(
                enabled = !isLoading,
                onClick = {
                    isLoading = true
                    errorMsg = null
                    AuthManager.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthenticated()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMsg = msg
                        },
                        scope = scope,
                        onAuthCancelled = { isLoading = false }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_signin_button")
            ) {
                if (isLoading && !showPhoneForm) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(strings.continueWithGoogle, style = MaterialTheme.typography.labelLarge)
                }
            }

            // B. Mobile Number OTP Login
            FilledTonalButton(
                enabled = !isLoading,
                onClick = { showPhoneForm = !showPhoneForm },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("phone_signin_toggle_button")
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.continueWithPhone)
            }

            AnimatedVisibility(visible = showPhoneForm) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box {
                            OutlinedButton(
                                onClick = { countryDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${selectedCountry.flag} ${selectedCountry.name} (${selectedCountry.dialCode})")
                            }
                            DropdownMenu(
                                expanded = countryDropdownExpanded,
                                onDismissRequest = { countryDropdownExpanded = false }
                            ) {
                                SUPPORTED_COUNTRIES.forEach { country ->
                                    DropdownMenuItem(
                                        text = { Text("${country.flag} ${country.name} (${country.dialCode})") },
                                        onClick = {
                                            selectedCountry = country
                                            countryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text(strings.phoneNumberLabel) },
                            prefix = { Text("${selectedCountry.dialCode} ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_number_input")
                        )

                        if (verificationId == null) {
                            Button(
                                enabled = !isLoading && phoneInput.length >= 6,
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        errorMsg = "Phone authentication requires an Activity context."
                                        return@Button
                                    }
                                    isLoading = true
                                    errorMsg = null
                                    val fullNumber = "${selectedCountry.dialCode}$phoneInput"
                                    AuthManager.startPhoneNumberVerification(
                                        activity = activity,
                                        fullPhoneNumber = fullNumber,
                                        resendToken = resendToken,
                                        onCodeSent = { verId, token ->
                                            verificationId = verId
                                            resendToken = token
                                            resendSeconds = 45
                                            isLoading = false
                                        },
                                        onAutoVerified = { cred ->
                                            scope.launch {
                                                AuthManager.signInWithPhoneCredential(cred)
                                                    .onSuccess {
                                                        isLoading = false
                                                        onAuthenticated()
                                                    }
                                                    .onFailure {
                                                        isLoading = false
                                                        errorMsg = it.localizedMessage
                                                    }
                                            }
                                        },
                                        onError = { msg ->
                                            isLoading = false
                                            errorMsg = msg
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("send_otp_button")
                            ) {
                                Text(strings.sendOtpAction)
                            }
                        } else {
                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                                label = { Text(strings.enterOtpLabel) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_input")
                            )
                            Button(
                                enabled = !isLoading && otpCode.length == 6,
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        errorMsg = null
                                        AuthManager.verifyOtpCode(verificationId!!, otpCode)
                                            .onSuccess {
                                                isLoading = false
                                                onAuthenticated()
                                            }
                                            .onFailure {
                                                isLoading = false
                                                errorMsg = it.localizedMessage ?: "Invalid or expired OTP."
                                            }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("verify_otp_button")
                            ) {
                                Text(strings.verifyOtpAction)
                            }
                            TextButton(
                                enabled = resendSeconds == 0 && !isLoading,
                                onClick = {
                                    val activity = context as? Activity ?: return@TextButton
                                    isLoading = true
                                    AuthManager.startPhoneNumberVerification(
                                        activity = activity,
                                        fullPhoneNumber = "${selectedCountry.dialCode}$phoneInput",
                                        resendToken = resendToken,
                                        onCodeSent = { verId, token ->
                                            verificationId = verId
                                            resendToken = token
                                            resendSeconds = 45
                                            isLoading = false
                                        },
                                        onAutoVerified = {},
                                        onError = {
                                            isLoading = false
                                            errorMsg = it
                                        }
                                    )
                                }
                            ) {
                                Text(
                                    if (resendSeconds > 0) "${strings.resendOtpAction} (${resendSeconds}s)"
                                    else strings.resendOtpAction
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // C. Guest Mode with mandatory local-storage warning
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = FlowAmber.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FlowAmber)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.guestWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            OutlinedButton(
                enabled = !isLoading,
                onClick = onContinueAsGuest,
                colors = ButtonDefaults.outlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("guest_mode_button")
            ) {
                Text(strings.continueAsGuest)
            }
        }
    }
}

@Composable
fun AppLockScreen(
    expectedPin: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    errorText = errString.toString()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PDF Flow")
            .setSubtitle("Verify your identity to access your documents")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) {
            val mgr = BiometricManager.from(context)
            if (mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
                triggerBiometric()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("PDF Flow Locked", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    enteredPin = it.filter { c -> c.isDigit() }.take(6)
                    errorText = null
                    if (enteredPin == expectedPin && expectedPin.isNotEmpty()) {
                        onUnlocked()
                    }
                },
                label = { Text("Enter PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("applock_pin_input")
            )
            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    if (enteredPin == expectedPin || expectedPin.isEmpty()) onUnlocked()
                    else errorText = "Incorrect PIN. Try again."
                },
                modifier = Modifier.fillMaxWidth().testTag("applock_unlock_button")
            ) {
                Text("Unlock")
            }
            if (biometricEnabled) {
                FilledTonalButton(onClick = ::triggerBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometrics")
                }
            }
        }
    }
}
