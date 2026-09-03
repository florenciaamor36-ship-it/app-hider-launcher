package com.example

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppInfo
import com.example.ui.LauncherViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                LauncherScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // State collections
    val visibleApps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val allInstalledApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val isHideActive by viewModel.isHideModeActive.collectAsStateWithLifecycle()
    val securityPin by viewModel.securityPin.collectAsStateWithLifecycle()
    val authMethod by viewModel.authMethod.collectAsStateWithLifecycle()
    val securityPassword by viewModel.securityPassword.collectAsStateWithLifecycle()
    val triggerTapsRequired by viewModel.triggerTaps.collectAsStateWithLifecycle()
    val hiddenAppsList by viewModel.hiddenApps.collectAsStateWithLifecycle()

    // Premium and state collections
    val disguiseName by viewModel.disguiseName.collectAsStateWithLifecycle()
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val intruderLogs by viewModel.intruderLogs.collectAsStateWithLifecycle()

    val hiddenPackageNames = remember(hiddenAppsList) {
        hiddenAppsList.map { it.packageName }.toSet()
    }

    // UI Local state
    var searchQuery by remember { mutableStateOf("") }
    var settingsSearchQuery by remember { mutableStateOf("") }
    var isPinDialogOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    // Multi-tap detection states on workspace background
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    // Pin & Password Dialog inputs
    var pinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPinError by remember { mutableStateOf(false) }

    // System lock launcher to verify pattern/biometrics using KeyguardManager
    val systemLockLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isSettingsOpen = true
            Toast.makeText(context, "Autenticación del sistema correcta", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Autenticación del sistema cancelada o incorrecta", Toast.LENGTH_SHORT).show()
            viewModel.addIntruderLog("Fallo de acceso biométrico/patrón")
        }
    }

    // Change Pin states
    var isChangingPin by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmNewPinInput by remember { mutableStateOf("") }
    var pinChangeError by remember { mutableStateOf("") }

    // Change Password states
    var isChangingPassword by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmNewPasswordInput by remember { mutableStateOf("") }
    var passwordChangeError by remember { mutableStateOf("") }

    // Onboarding Guide tutorial states
    var isOnboardingOpen by remember { mutableStateOf(false) }
    var onboardingStep by remember { mutableStateOf(1) }

    // Collapsible sections states
    var isUserGuideExpanded by remember { mutableStateOf(false) }
    var isAboutLegalExpanded by remember { mutableStateOf(false) }
    var isCreditsExpanded by remember { mutableStateOf(false) }

    // Sectorization filter tab state (TODAS, SOCIAL, BANCOS, JUEGOS, HERRAMIENTAS, OCULTAS, VISIBLES)
    var selectedCategoryTab by remember { mutableStateOf("TODAS") }

    // Default Launcher Status detection
    var isDefaultHome by remember { mutableStateOf(checkIsDefaultLauncher(context)) }
    var isDefaultBannerDismissed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultHome = checkIsDefaultLauncher(context)
                viewModel.loadInstalledApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Pagination states
    var settingsCurrentPage by remember { mutableStateOf(0) }

    // Reset settings page on query or category change
    LaunchedEffect(settingsSearchQuery, selectedCategoryTab) {
        settingsCurrentPage = 0
    }

    // Time & Date tickers
    var currentTime by remember { mutableStateOf("--:--") }
    var currentDate by remember { mutableStateOf("---") }

    LaunchedEffect(Unit) {
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("EEEE, d 'de' MMMM", java.util.Locale("es", "ES"))
        while (true) {
            val now = java.util.Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Filtered launcher home apps based on top search bar
    val filteredHomeApps = remember(visibleApps, searchQuery) {
        if (searchQuery.isBlank()) {
            visibleApps
        } else {
            visibleApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Filtered settings apps based on search query AND Sectorization Tab
    val filteredSettingsApps = remember(allInstalledApps, settingsSearchQuery, selectedCategoryTab, hiddenPackageNames) {
        val queryFiltered = if (settingsSearchQuery.isBlank()) {
            allInstalledApps
        } else {
            allInstalledApps.filter {
                it.appName.contains(settingsSearchQuery, ignoreCase = true) ||
                it.packageName.contains(settingsSearchQuery, ignoreCase = true)
            }
        }

        when (selectedCategoryTab) {
            "OCULTAS" -> queryFiltered.filter { hiddenPackageNames.contains(it.packageName) }
            "VISIBLES" -> queryFiltered.filter { !hiddenPackageNames.contains(it.packageName) }
            "SOCIAL" -> queryFiltered.filter { app ->
                val pkg = app.packageName.lowercase()
                val name = app.appName.lowercase()
                pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("facebook") || 
                pkg.contains("instagram") || pkg.contains("tiktok") || pkg.contains("twitter") || 
                pkg.contains("snapchat") || pkg.contains("linkedin") || pkg.contains("tinder") || 
                pkg.contains("social") || name.contains("chat") || name.contains("messenger")
            }
            "BANCOS" -> queryFiltered.filter { app ->
                val pkg = app.packageName.lowercase()
                val name = app.appName.lowercase()
                pkg.contains("bank") || pkg.contains("banco") || pkg.contains("pay") || 
                pkg.contains("wallet") || pkg.contains("finance") || pkg.contains("crypto") || 
                pkg.contains("binance") || pkg.contains("invert") || pkg.contains("bolsa") ||
                name.contains("banco") || name.contains("pago") || name.contains("tarjeta") || name.contains("ahorro")
            }
            "JUEGOS" -> queryFiltered.filter { app ->
                val pkg = app.packageName.lowercase()
                val name = app.appName.lowercase()
                pkg.contains("game") || pkg.contains("juego") || pkg.contains("play") || 
                pkg.contains("netflix") || pkg.contains("spotify") || pkg.contains("disney") || 
                pkg.contains("prime") || pkg.contains("youtube") || pkg.contains("twitch") ||
                name.contains("juego") || name.contains("video") || name.contains("tv")
            }
            "HERRAMIENTAS" -> queryFiltered.filter { app ->
                val pkg = app.packageName.lowercase()
                val name = app.appName.lowercase()
                pkg.contains("tool") || pkg.contains("chrome") || pkg.contains("drive") || 
                pkg.contains("email") || pkg.contains("gmail") || pkg.contains("map") || 
                pkg.contains("setting") || pkg.contains("camera") || pkg.contains("contact") || 
                pkg.contains("file") || pkg.contains("calculator") || pkg.contains("clock") ||
                name.contains("ajustes") || name.contains("configuracion") || name.contains("herramientas")
            }
            else -> queryFiltered // "TODAS"
        }
    }

    // Pagination calculations
    val homeItemsPerPage = 12
    val homeTotalPages = remember(filteredHomeApps) {
        val total = if (filteredHomeApps.isEmpty()) 1 else ((filteredHomeApps.size + homeItemsPerPage - 1) / homeItemsPerPage)
        if (total < 1) 1 else total
    }
    val homePagerState = rememberPagerState(pageCount = { homeTotalPages })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(filteredHomeApps.size, searchQuery) {
        if (homePagerState.currentPage >= homeTotalPages && homeTotalPages > 0) {
            homePagerState.scrollToPage(0)
        }
    }

    val settingsItemsPerPage = 8
    val settingsTotalPages = remember(filteredSettingsApps) {
        if (filteredSettingsApps.isEmpty()) 1 else ((filteredSettingsApps.size + settingsItemsPerPage - 1) / settingsItemsPerPage)
    }
    val safeSettingsCurrentPage = if (settingsCurrentPage >= settingsTotalPages) 0 else settingsCurrentPage
    val pagedSettingsApps = remember(filteredSettingsApps, safeSettingsCurrentPage) {
        filteredSettingsApps.drop(safeSettingsCurrentPage * settingsItemsPerPage).take(settingsItemsPerPage)
    }

    // Direct security trigger function
    val triggerAuth: () -> Unit = {
        focusManager.clearFocus()
        if (authMethod == "SYSTEM") {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (km.isDeviceSecure) {
                val intent = km.createConfirmDeviceCredentialIntent(
                    "Ajustes de Seguridad",
                    "Confirma tu patrón, PIN, contraseña o huella dactilar para entrar."
                )
                if (intent != null) {
                    systemLockLauncher.launch(intent)
                } else {
                    isPinDialogOpen = true
                }
            } else {
                Toast.makeText(context, "El dispositivo no tiene bloqueo de pantalla configurado. Usando método alternativo.", Toast.LENGTH_LONG).show()
                isPinDialogOpen = true
            }
        } else {
            isPinDialogOpen = true
        }
    }

    // Main layout container (Transparent background so system wallpaper draws behind)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(triggerTapsRequired, authMethod) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        val nowTime = System.currentTimeMillis()
                        if (nowTime - lastTapTime < 2000) {
                            tapCount += 1
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = nowTime

                        if (tapCount >= triggerTapsRequired) {
                            tapCount = 0 // Reset tap counter
                            triggerAuth()
                        }
                    }
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // App Launcher Desktop Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant clock and lock trigger container (Tap or Long Press the clock to unlock)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date & Time widget with direct tap and long-press authentication triggers
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(triggerTapsRequired, authMethod) {
                            detectTapGestures(
                                onTap = {
                                    val nowTime = System.currentTimeMillis()
                                    if (nowTime - lastTapTime < 2000) {
                                        tapCount += 1
                                    } else {
                                        tapCount = 1
                                    }
                                    lastTapTime = nowTime

                                    if (tapCount >= triggerTapsRequired) {
                                        tapCount = 0
                                        triggerAuth()
                                    }
                                },
                                onLongPress = {
                                    triggerAuth()
                                }
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("desktop_clock_widget")
                ) {
                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 8f
                            )
                        )
                    )
                    Text(
                        text = currentDate,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 8f
                            )
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Search bar for applications
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp))
                    .testTag("app_search_bar"),
                placeholder = {
                    Text(
                        "Buscar en $disguiseName...",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Color.White
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = Color.White
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Default Launcher Banner: alert if not set as default home launcher
            if (!isDefaultHome && !isDefaultBannerDismissed) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("default_launcher_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Establecer como Launcher Predeterminado",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Elige esta app como inicio para que permanezca activa al salir de apps o reiniciar el celular.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { launchSetDefaultLauncher(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Elegir Launcher", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { isDefaultBannerDismissed = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Ahora no", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic view: Loading vs. Empty state vs. App grid
            if (isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (filteredHomeApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = "No hay aplicaciones",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No hay aplicaciones visibles" else "No se encontraron coincidencias",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toca $triggerTapsRequired veces seguidas la pantalla en un espacio vacío para abrir la configuración secreta.",
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        )
                    }
                }
            } else {
                // Horizontal Swipeable Pager: Drag smoothly between desktop pages
                HorizontalPager(
                    state = homePagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("home_horizontal_pager")
                ) { pageIndex ->
                    val pageApps = remember(filteredHomeApps, pageIndex) {
                        filteredHomeApps.drop(pageIndex * homeItemsPerPage).take(homeItemsPerPage)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .testTag("app_grid_$pageIndex"),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pageApps, key = { it.packageName }) { app ->
                            AppGridItem(app = app, context = context)
                        }
                    }
                }

                // Pagination Dot Indicators and Buttons for Home Desktop apps
                if (homeTotalPages > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (homePagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        homePagerState.animateScrollToPage(homePagerState.currentPage - 1)
                                    }
                                }
                            },
                            enabled = homePagerState.currentPage > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Página anterior",
                                tint = if (homePagerState.currentPage > 0) Color.White else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until homeTotalPages) {
                                val isSelected = i == homePagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 10.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f))
                                        .clickable {
                                            coroutineScope.launch {
                                                homePagerState.animateScrollToPage(i)
                                            }
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (homePagerState.currentPage < homeTotalPages - 1) {
                                    coroutineScope.launch {
                                        homePagerState.animateScrollToPage(homePagerState.currentPage + 1)
                                    }
                                }
                            },
                            enabled = homePagerState.currentPage < homeTotalPages - 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Página siguiente",
                                tint = if (homePagerState.currentPage < homeTotalPages - 1) Color.White else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- SECTION: SECURE AUTHENTICATION DIALOG (Overlay) ---
        if (isPinDialogOpen) {
            AlertDialog(
                onDismissRequest = {
                    isPinDialogOpen = false
                    pinInput = ""
                    passwordInput = ""
                    isPinError = false
                },
                modifier = Modifier.testTag("auth_dialog"),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (authMethod == "PASSWORD") "Ingresa tu Contraseña" else "Ingresa tu PIN",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (authMethod == "PASSWORD") "Por defecto: admin123" else "Por defecto: 0000",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (authMethod == "PASSWORD") {
                            // PASSWORD Textfield input
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    isPinError = false
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                label = { Text("Contraseña") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input_dialog"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            if (isPinError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Contraseña incorrecta. Intenta de nuevo.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                             Button(
                                onClick = {
                                    if (passwordInput == securityPassword) {
                                        isSettingsOpen = true
                                        isPinDialogOpen = false
                                        passwordInput = ""
                                        Toast.makeText(context, "Ajustes de Seguridad Desbloqueados", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isPinError = true
                                        viewModel.addIntruderLog("Fallo de Contraseña Alfanumérica")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_verify_button")
                            ) {
                                Text("Verificar Contraseña")
                            }
                        } else {
                            // PIN Dots indicator for entered characters
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                for (i in 0 until 4) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = if (pinInput.length > i) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            // PIN Keyboard helper input (hidden standard textfield to drive keyboard entry)
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { input ->
                                    if (input.length <= 4 && input.all { it.isDigit() }) {
                                        pinInput = input
                                        isPinError = false
                                        // Automatic verify when 4 digits are completed
                                        if (input.length == 4) {
                                            if (input == securityPin) {
                                                isSettingsOpen = true
                                                isPinDialogOpen = false
                                                pinInput = ""
                                                Toast.makeText(context, "Ajustes de Seguridad Desbloqueados", Toast.LENGTH_SHORT).show()
                                            } else {
                                                isPinError = true
                                                viewModel.addIntruderLog("Fallo de PIN Numérico")
                                                pinInput = ""
                                            }
                                        }
                                    }
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(1.dp)
                                    .shadow(0.dp)
                                    .testTag("pin_hidden_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )

                            // Beautiful grid pinpad inside the alertdialog
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                val keypad = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9"),
                                    listOf("Borrar", "0", "OK")
                                )

                                for (row in keypad) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (key in row) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.6f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (key == "OK") {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        }
                                                    )
                                                    .clickable {
                                                        when (key) {
                                                            "Borrar" -> {
                                                                if (pinInput.isNotEmpty()) {
                                                                    pinInput = pinInput.dropLast(1)
                                                                    isPinError = false
                                                                }
                                                            }
                                                            "OK" -> {
                                                                if (pinInput == securityPin) {
                                                                    isSettingsOpen = true
                                                                    isPinDialogOpen = false
                                                                    pinInput = ""
                                                                    Toast.makeText(context, "Ajustes de Seguridad Desbloqueados", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    isPinError = true
                                                                    viewModel.addIntruderLog("Fallo de PIN Numérico")
                                                                    pinInput = ""
                                                                }
                                                            }
                                                            else -> {
                                                                if (pinInput.length < 4) {
                                                                    pinInput += key
                                                                    isPinError = false
                                                                    if (pinInput.length == 4) {
                                                                        if (pinInput == securityPin) {
                                                                            isSettingsOpen = true
                                                                            isPinDialogOpen = false
                                                                            pinInput = ""
                                                                            Toast.makeText(context, "Ajustes de Seguridad Desbloqueados", Toast.LENGTH_SHORT).show()
                                                                        } else {
                                                                            isPinError = true
                                                                            viewModel.addIntruderLog("Fallo de PIN Numérico")
                                                                            pinInput = ""
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            }
                                                        },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = key,
                                                    fontWeight = FontWeight.Bold,
                                                    style = if (key.all { it.isDigit() }) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                                                    color = if (key == "OK") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isPinError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "PIN incorrecto. Intenta de nuevo.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("pin_error_msg")
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = {
                            isPinDialogOpen = false
                            pinInput = ""
                            passwordInput = ""
                            isPinError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancelar", textAlign = TextAlign.Center)
                    }
                }
            )
        }

        // --- SECTION: SECURE LAUNCHER SETTINGS SLIDE-UP SHEET ---
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_surface")
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ajustes de Seguridad",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                isSettingsOpen = false
                                settingsSearchQuery = ""
                                isChangingPin = false
                                pinChangeError = ""
                                newPinInput = ""
                                confirmNewPinInput = ""
                                isChangingPassword = false
                                newPasswordInput = ""
                                confirmNewPasswordInput = ""
                                passwordChangeError = ""
                            },
                            modifier = Modifier.testTag("close_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar Ajustes"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Full settings page with all features
                        // Launcher Predeterminado Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_default_launcher_card"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDefaultHome) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                        }
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isDefaultHome) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (isDefaultHome) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (isDefaultHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Launcher Predeterminado",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isDefaultHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            ) {
                                                Text(
                                                    text = if (isDefaultHome) "ACTIVO" else "INACTIVO",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = if (isDefaultHome) {
                                                "✅ Esta app está elegida como la pantalla de inicio principal de tu celular. Al encender el celular o salir de cualquier app, permanecerás siempre en este launcher seguro.\n\nPuedes cambiar o elegir otro launcher cuando quieras."
                                            } else {
                                                "⚠️ Esta app todavía no está establecida como tu pantalla de inicio predeterminada en Android. Por eso, al salir de apps o presionar el botón de inicio, el celular vuelve al launcher de fábrica.\n\nToca el botón para configurarla como 'App de inicio' y que quede fija y permanente al prender el teléfono."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = { launchSetDefaultLauncher(context) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDefaultHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isDefaultHome) "Cambiar / Elegir otro Launcher" else "Establecer como Launcher Predeterminado",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Ocultar Aplicaciones",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "Activa para ocultar las apps seleccionadas del launcher principal.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isHideActive,
                                            onCheckedChange = { active ->
                                                viewModel.setHideModeActive(active)
                                            },
                                            modifier = Modifier.testTag("toggle_hide_mode")
                                        )
                                    }
                                }
                            }

                            // Interactive step-by-step onboarding guide button
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Guía Paso a Paso (Tutorial)",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "Repasa el tutorial interactivo para aprender a usar App Hider.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                isOnboardingOpen = true
                                                onboardingStep = 1
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) {
                                            Text("Iniciar Guía")
                                        }
                                    }
                                }
                            }

                            // App Disguise Camouflage Selection Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Camuflaje de Launcher (Premium)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Disfraza el nombre de la app para máxima privacidad.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf("Calculadora", "Reloj", "App Hider", "Clima").forEach { fakeName ->
                                                val isSelected = disguiseName == fakeName
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                        .clickable {
                                                            viewModel.updateDisguiseName(fakeName)
                                                            Toast.makeText(context, "Disfraz cambiado a $fakeName", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = fakeName,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }



                            // Configurable Tap Gesture Trigger (5, 10, or 20 taps)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Toques para Acceso Secreto",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Número de toques seguidos en la pantalla vacía para abrir el PIN/Contraseña.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(5, 10, 20).forEach { taps ->
                                                val isSelected = triggerTapsRequired == taps
                                                Button(
                                                    onClick = { viewModel.updateTriggerTaps(taps) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                                    ),
                                                    modifier = Modifier.weight(1f).testTag("taps_option_$taps")
                                                ) {
                                                    Text("$taps Toques", style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Configuration of authentication methods (PIN, Password, System)
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Método de Autenticación",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Elige la forma en que se protegerá tu contenido oculto.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Option: PIN
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.updateAuthMethod("PIN") }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = authMethod == "PIN",
                                                onClick = { viewModel.updateAuthMethod("PIN") },
                                                modifier = Modifier.testTag("auth_method_pin")
                                            )
                                            Column {
                                                Text("PIN numérico (4 dígitos)", fontWeight = FontWeight.SemiBold)
                                                Text("Ingreso rápido mediante un teclado de números.", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        // Option: PASSWORD
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.updateAuthMethod("PASSWORD") }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = authMethod == "PASSWORD",
                                                onClick = { viewModel.updateAuthMethod("PASSWORD") },
                                                modifier = Modifier.testTag("auth_method_password")
                                            )
                                            Column {
                                                Text("Contraseña alfanumérica", fontWeight = FontWeight.SemiBold)
                                                Text("Mayor seguridad usando letras, números y símbolos.", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        // Option: SYSTEM (Fingerprint, Face unlock, Swipe Pattern)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.updateAuthMethod("SYSTEM") }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = authMethod == "SYSTEM",
                                                onClick = { viewModel.updateAuthMethod("SYSTEM") },
                                                modifier = Modifier.testTag("auth_method_system")
                                            )
                                            Column {
                                                Text("Seguridad del Sistema", fontWeight = FontWeight.SemiBold)
                                                Text("Usa huella digital, rostro o patrón nativo de tu celular.", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }

                            // Dynamic Change Credential (PIN or PASSWORD)
                            if (authMethod == "PIN" || authMethod == "PASSWORD") {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            if (authMethod == "PIN") {
                                                // Change PIN flow
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "PIN de Seguridad",
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.titleMedium
                                                        )
                                                        Text(
                                                            text = "Código PIN actual: $securityPin",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Button(
                                                        onClick = { isChangingPin = !isChangingPin },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    ) {
                                                        Text(if (isChangingPin) "Cancelar" else "Cambiar PIN")
                                                    }
                                                }

                                                if (isChangingPin) {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        OutlinedTextField(
                                                            value = newPinInput,
                                                            onValueChange = { input ->
                                                                if (input.length <= 4 && input.all { it.isDigit() }) {
                                                                    newPinInput = input
                                                                }
                                                            },
                                                            label = { Text("Nuevo PIN (4 dígitos)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                                                        )

                                                        OutlinedTextField(
                                                            value = confirmNewPinInput,
                                                            onValueChange = { input ->
                                                                if (input.length <= 4 && input.all { it.isDigit() }) {
                                                                    confirmNewPinInput = input
                                                                }
                                                            },
                                                            label = { Text("Confirmar PIN") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input")
                                                        )

                                                        if (pinChangeError.isNotEmpty()) {
                                                            Text(
                                                                text = pinChangeError,
                                                                color = MaterialTheme.colorScheme.error,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Button(
                                                            onClick = {
                                                                if (newPinInput.length != 4) {
                                                                    pinChangeError = "El PIN debe tener exactamente 4 dígitos."
                                                                } else if (newPinInput != confirmNewPinInput) {
                                                                    pinChangeError = "Los códigos PIN no coinciden."
                                                                } else {
                                                                    viewModel.updateSecurityPin(newPinInput)
                                                                    isChangingPin = false
                                                                    newPinInput = ""
                                                                    confirmNewPinInput = ""
                                                                    pinChangeError = ""
                                                                    Toast.makeText(context, "PIN actualizado con éxito", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.align(Alignment.End).testTag("save_pin_button")
                                                        ) {
                                                            Text("Guardar PIN")
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Change PASSWORD flow
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "Contraseña Alfanumérica",
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.titleMedium
                                                        )
                                                        Text(
                                                            text = "Cambia la contraseña de seguridad.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Button(
                                                        onClick = { isChangingPassword = !isChangingPassword },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    ) {
                                                        Text(if (isChangingPassword) "Cancelar" else "Cambiar Contraseña")
                                                    }
                                                }

                                                if (isChangingPassword) {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        OutlinedTextField(
                                                            value = newPasswordInput,
                                                            onValueChange = { newPasswordInput = it },
                                                            label = { Text("Nueva Contraseña") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth().testTag("new_password_input")
                                                        )

                                                        OutlinedTextField(
                                                            value = confirmNewPasswordInput,
                                                            onValueChange = { confirmNewPasswordInput = it },
                                                            label = { Text("Confirmar Contraseña") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth().testTag("confirm_password_input")
                                                        )

                                                        if (passwordChangeError.isNotEmpty()) {
                                                            Text(
                                                                text = passwordChangeError,
                                                                color = MaterialTheme.colorScheme.error,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Button(
                                                            onClick = {
                                                                if (newPasswordInput.isBlank()) {
                                                                    passwordChangeError = "La contraseña no puede estar vacía."
                                                                } else if (newPasswordInput != confirmNewPasswordInput) {
                                                                    passwordChangeError = "Las contraseñas no coinciden."
                                                                } else {
                                                                    viewModel.updateSecurityPassword(newPasswordInput)
                                                                    isChangingPassword = false
                                                                    newPasswordInput = ""
                                                                    confirmNewPasswordInput = ""
                                                                    passwordChangeError = ""
                                                                    Toast.makeText(context, "Contraseña actualizada con éxito", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.align(Alignment.End).testTag("save_password_button")
                                                        ) {
                                                            Text("Guardar Contraseña")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Failed login Intruder attempts log card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Registro de Intrusos (Premium)",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    text = "Monitorea intentos incorrectos de PIN/Contraseña.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (intruderLogs.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.clearIntruderLogs() }) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteSweep,
                                                        contentDescription = "Borrar logs",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (intruderLogs.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("¡Sin intentos sospechosos registrados! 👍", style = MaterialTheme.typography.bodySmall)
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                intruderLogs.take(5).forEach { log ->
                                                    val parts = log.split("::")
                                                    val timestampMillis = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                                                    val reason = parts.getOrNull(1) ?: log
                                                    val formattedDate = if (timestampMillis > 0L) {
                                                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                                                            .format(java.util.Date(timestampMillis))
                                                    } else {
                                                        "Fecha desconocida"
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                                                                RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Portrait,
                                                            contentDescription = "Silueta",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column {
                                                            Text(
                                                                text = reason,
                                                                fontWeight = FontWeight.SemiBold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
                                                            Text(
                                                                text = formattedDate,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                if (intruderLogs.size > 5) {
                                                    Text(
                                                        text = "y ${intruderLogs.size - 5} intentos fallidos más registrados.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // --- PREMIUM COLLAPSIBLE CARD: USER GUIDE (Guía de Uso) ---
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isUserGuideExpanded = !isUserGuideExpanded }
                                        .testTag("user_guide_card"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Guía de Uso",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isUserGuideExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isUserGuideExpanded) "Colapsar" else "Expandir"
                                            )
                                        }
                                        if (isUserGuideExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "💡 ¿Cómo usar App Hider de forma segura?\n\n" +
                                                        "1. Configura tu método de autenticación preferido (PIN, Contraseña o Bloqueo del Sistema) en las opciones superiores de este panel.\n\n" +
                                                        "2. Selecciona las aplicaciones que deseas ocultar de la pantalla de inicio (Home) marcando la casilla de verificación en la sección de abajo.\n\n" +
                                                        "3. Las aplicaciones seleccionadas se ocultarán inmediatamente de la pantalla de inicio. Para volver a acceder a los Ajustes de Seguridad, realiza el número elegido de toques seguidos en cualquier parte vacía de la pantalla de inicio.\n\n" +
                                                        "4. Gesto rápido: Si ya estás autenticado, puedes abrir la configuración en cualquier momento haciendo doble toque en el fondo de la pantalla de inicio.\n\n" +
                                                        "5. Clave de Señuelo (Decoy Mode): Si configuras una clave de señuelo diferente, al ingresarla abrirás la app con un Panel de Control simulado inofensivo que no muestra tus aplicaciones privadas.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 22.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // --- PREMIUM COLLAPSIBLE CARD: ABOUT & LEGAL (Acerca de la App y Términos Legales) ---
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isAboutLegalExpanded = !isAboutLegalExpanded }
                                        .testTag("about_legal_card"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Acerca de y Aspectos Legales",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isAboutLegalExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isAboutLegalExpanded) "Colapsar" else "Expandir"
                                            )
                                        }
                                        if (isAboutLegalExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "🔒 Seguridad y Privacidad Absoluta\n\n" +
                                                        "• Privacidad de Datos: App Hider opera con un esquema sin recopilación de información de usuario. Tus aplicaciones seleccionadas se guardan de manera estrictamente local mediante cifrado en el almacenamiento aislado de la aplicación (Room/SQLite).\n\n" +
                                                        "• Cero Telemetría: Esta aplicación no cuenta con rastreadores ni servicios de análisis web que puedan recopilar datos sobre tus hábitos de uso o geolocalización. El acceso a internet se limita exclusivamente a la sincronización en la nube opcional iniciada manualmente por ti.\n\n" +
                                                        "• Términos Legales: El uso de esta aplicación está destinado a la protección de la privacidad del propietario del dispositivo. Al utilizarla, aceptas que eres el propietario legal del dispositivo y que operarás la app de acuerdo con las leyes y regulaciones de tu territorio local.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 22.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // --- PREMIUM COLLAPSIBLE CARD: DEVELOPER CREDITS (Créditos a Desarrolladores) ---
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isCreditsExpanded = !isCreditsExpanded }
                                        .testTag("credits_card"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Créditos de Desarrolladores",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isCreditsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isCreditsExpanded) "Colapsar" else "Expandir",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (isCreditsExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "🇦🇷 Diseñado y Desarrollado con Orgullo por:\n\n" +
                                                        "• La Clave Argentina\n" +
                                                        "• Tienda SSH\n\n" +
                                                        "Esta robusta suite de camuflaje, privacidad y protección digital ha sido construida en conjunto por los prestigiosos equipos técnicos y creativos de La Clave Argentina y Tienda SSH en el año 2026. Todos los derechos reservados bajo la legislación vigente de protección de propiedad intelectual.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 22.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            // Checklist selector label and search tools
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Seleccionar aplicaciones a ocultar",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Marca o desmarca las apps que se ocultarán en el Home.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = settingsSearchQuery,
                                        onValueChange = { settingsSearchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp)
                                            .testTag("settings_app_search"),
                                        placeholder = { Text("Buscar en la lista...") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    // Sectorization tab buttons (Horizontal scrolling category filters list)
                                    Text(
                                        text = "Filtrar por Categoría:",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("TODAS", "SOCIAL", "BANCOS", "JUEGOS", "HERRAMIENTAS", "OCULTAS", "VISIBLES").forEach { cat ->
                                            val isSelected = selectedCategoryTab == cat
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                                    )
                                                    .clickable { selectedCategoryTab = cat }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                                    .testTag("category_tab_$cat")
                                            ) {
                                                Text(
                                                    text = cat,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        // App List Checklist
                        items(pagedSettingsApps, key = { it.packageName }) { app ->
                            val isHidden = hiddenPackageNames.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.toggleAppHiddenState(
                                            packageName = app.packageName,
                                            appName = app.appName,
                                            shouldHide = !isHidden
                                        )
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // App Icon
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap,
                                        contentDescription = app.appName,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    AndroidView(
                                        factory = { context ->
                                            ImageView(context).apply {
                                                scaleType = ImageView.ScaleType.FIT_CENTER
                                            }
                                        },
                                        update = { imageView ->
                                            imageView.setImageDrawable(app.icon)
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = {
                                            try {
                                                val pm = context.packageManager
                                                var launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                                if (launchIntent == null && app.className.isNotBlank()) {
                                                    launchIntent = Intent(Intent.ACTION_MAIN).apply {
                                                        addCategory(Intent.CATEGORY_LAUNCHER)
                                                        setClassName(app.packageName, app.className)
                                                    }
                                                }
                                                if (launchIntent != null) {
                                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(launchIntent)
                                                } else {
                                                    Toast.makeText(context, "No se puede abrir ${app.appName}", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error al abrir: ${e.localizedMessage ?: "desconocido"}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("open_app_${app.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Abrir ${app.appName}",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Abrir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Checkbox(
                                        checked = isHidden,
                                        onCheckedChange = { checked ->
                                            viewModel.toggleAppHiddenState(
                                                packageName = app.packageName,
                                                appName = app.appName,
                                                shouldHide = checked
                                            )
                                        },
                                        modifier = Modifier.testTag("checkbox_${app.packageName}")
                                    )
                                }
                            }
                        }

                        // Settings checklist pagination controls
                        if (settingsTotalPages > 1) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (safeSettingsCurrentPage > 0) settingsCurrentPage = safeSettingsCurrentPage - 1 },
                                        enabled = safeSettingsCurrentPage > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowLeft,
                                            contentDescription = "Anterior",
                                            tint = if (safeSettingsCurrentPage > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }

                                    Text(
                                        text = "Pág ${safeSettingsCurrentPage + 1} de $settingsTotalPages",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    IconButton(
                                        onClick = { if (safeSettingsCurrentPage < settingsTotalPages - 1) settingsCurrentPage = safeSettingsCurrentPage + 1 },
                                        enabled = safeSettingsCurrentPage < settingsTotalPages - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Siguiente",
                                            tint = if (safeSettingsCurrentPage < settingsTotalPages - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                        }

                        // Appended Safe Uninstallation card at the absolute bottom of the settings LazyColumn
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("uninstall_app_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Desinstalar Aplicación",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Para eliminar App Hider de tu teléfono de forma directa, presiona el botón de abajo. Si la configuraste como Launcher predeterminado, Android te pedirá seleccionar tu launcher original antes de quitarla.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            try {
                                                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(uninstallIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error al iniciar desinstalación: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("uninstall_app_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Proceder a Desinstalar App Hider")
                                    }
                                }
                            }
                        }
                    }

                    // Bottom info/tutorial text
                    Text(
                        text = "💡 Gesto Secreto: Puedes abrir los Ajustes de Seguridad en cualquier momento haciendo doble toque en el fondo del Home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AppGridItem(app: AppInfo, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                try {
                    val pm = context.packageManager
                    var intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent == null && app.className.isNotBlank()) {
                        intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setClassName(app.packageName, app.className)
                        }
                    }
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "No se puede abrir ${app.appName}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al abrir la app: ${e.localizedMessage ?: "desconocido"}", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon container using hardware-accelerated Compose Image with fallback
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (app.iconBitmap != null) {
                Image(
                    bitmap = app.iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { imageView ->
                        imageView.setImageDrawable(app.icon)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Text with a strong shadow outline to guarantee legibility on top of ANY custom system wallpaper
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                shadow = Shadow(
                    color = Color.Black,
                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                    blurRadius = 6f
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

fun checkIsDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

fun launchSetDefaultLauncher(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                context.startActivity(intent)
                return
            }
        }
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            context.startActivity(intent)
        } catch (e2: Exception) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e3: Exception) {
                Toast.makeText(context, "Abre Ajustes > Aplicaciones > Aplicaciones predeterminadas > App de inicio", Toast.LENGTH_LONG).show()
            }
        }
    }
}
