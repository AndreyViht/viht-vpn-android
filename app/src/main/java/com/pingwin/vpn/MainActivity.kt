package com.pingwin.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pingwin.vpn.ui.VihtAuthScreen
import com.pingwin.vpn.ui.VihtBottomNavBar
import com.pingwin.vpn.ui.VihtFloatingIslandNavBar
import com.pingwin.vpn.ui.VihtCabinetScreen
import com.pingwin.vpn.ui.VihtHomeScreen
import com.pingwin.vpn.ui.VihtServersScreen
import com.pingwin.vpn.ui.VihtSettingsScreen
import com.pingwin.vpn.ui.VihtTab
import com.pingwin.vpn.ui.theme.PingwinTheme
import com.pingwin.vpn.ui.theme.VihtBgMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private enum class SubScreen {
        NONE,
        AUTH,
        APP_ROUTING,
        GENERAL,
        LOGS
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.setForeground(true)
        AutomationService.sync(this)
        UpdateScheduler.sync(this)
    }

    override fun onStop() {
        AppVisibility.setForeground(false)
        super.onStop()
        when (VpnStatus.state.value) {
            VpnConnectionState.CONNECTED -> LauncherIconManager.showGreen(this)
            else -> LauncherIconManager.showBlue(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private var onDeepLinkReceived: (() -> Unit)? = null

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val token = data.getQueryParameter("token") ?: data.getQueryParameter("sub")
            val subToken = data.getQueryParameter("sub_token")
            val tgId = data.getQueryParameter("tg_id") ?: data.getQueryParameter("telegram_id")
            val email = data.getQueryParameter("email")

            val effectiveToken = subToken ?: token
            if (!effectiveToken.isNullOrBlank()) {
                VihtPreferences.setAuthToken(this, effectiveToken)
            }
            if (!tgId.isNullOrBlank()) {
                VihtPreferences.setTelegramId(this, tgId)
            }
            if (!email.isNullOrBlank()) {
                VihtPreferences.setUserEmail(this, email)
            }

            if (!effectiveToken.isNullOrBlank() || !tgId.isNullOrBlank()) {
                Toast.makeText(this, "Авторизация успешно получена!", Toast.LENGTH_SHORT).show()
                onDeepLinkReceived?.invoke()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)

        setContent {
            PingwinTheme {
                val scope = rememberCoroutineScope()
                val vpnState by VpnStatus.state.collectAsState()

                // Auth state gate
                var isLoggedIn by rememberSaveable {
                    mutableStateOf(VihtPreferences.isLoggedIn(this@MainActivity))
                }

                // Navigation states
                var currentTab by rememberSaveable { mutableStateOf(VihtTab.HOME) }
                var currentSubScreen by rememberSaveable { mutableStateOf(SubScreen.NONE) }

                // Servers and Profile state
                var servers by remember {
                    mutableStateOf(VihtPreferences.getSavedServers(this@MainActivity) ?: emptyList())
                }
                var selectedServerId by rememberSaveable {
                    mutableStateOf(VihtPreferences.getSelectedServerId(this@MainActivity))
                }
                var userProfile by remember { mutableStateOf<VihtUserProfile?>(null) }
                var activeDevices by remember { mutableStateOf<List<VihtDevice>>(emptyList()) }
                var isCabinetLoading by remember { mutableStateOf(false) }
                var isMeasuringPing by remember { mutableStateOf(false) }

                // Settings states
                var bypassRussia by remember {
                    mutableStateOf(VihtPreferences.isBypassRussianSites(this@MainActivity))
                }
                var autoBoot by remember {
                    mutableStateOf(VihtPreferences.isAutoConnectOnBoot(this@MainActivity))
                }
                var killSwitch by remember {
                    mutableStateOf(VihtPreferences.isKillSwitch(this@MainActivity))
                }

                val selectedServer = remember(servers, selectedServerId) {
                    servers.find { it.id == selectedServerId }
                        ?: servers.firstOrNull()
                        ?: VihtServer(
                            id = "none",
                            name = if (isLoggedIn) "Загрузка серверов..." else "Требуется авторизация",
                            flag = "🌐",
                            host = "anviht.ru",
                            port = 443
                        )
                }

                // Pending connection permissions
                var pendingConfig by remember { mutableStateOf<String?>(null) }
                var pendingConnectionId by remember { mutableStateOf<String?>(null) }

                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val config = pendingConfig
                    val connId = pendingConnectionId
                    if (result.resultCode == RESULT_OK && config != null && connId != null) {
                        AutoVlessVpnService.start(this@MainActivity, config, connId)
                    } else {
                        VpnStatus.set(VpnConnectionState.ERROR)
                    }
                    pendingConfig = null
                    pendingConnectionId = null
                }

                // VPN start/stop handler
                fun toggleVpn() {
                    if (vpnState == VpnConnectionState.CONNECTED || vpnState == VpnConnectionState.CONNECTING) {
                        AutoVlessVpnService.stop(this@MainActivity)
                        return
                    }

                    try {
                        val vlessLink = selectedServer.vlessUri
                        if (vlessLink.isBlank()) {
                            Toast.makeText(this@MainActivity, "Ключ сервера не найден. Авторизуйтесь в кабинете.", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val profile = ConnectionProfileParser.parse(vlessLink)
                        val routingBase = RoutingSettingsStore.load(this@MainActivity)
                        val effectiveRouting = routingBase.copy(bypassRussiaSites = bypassRussia)

                        val config = ConnectionConfigBuilder.build(
                            profile = profile,
                            routing = effectiveRouting,
                            detailedLogging = DiagnosticLogStore.isDetailedEnabled(this@MainActivity)
                        )

                        val validation = LibboxValidator.validate(config)
                        if (validation.isFailure) {
                            VpnStatus.set(VpnConnectionState.ERROR)
                            Toast.makeText(this@MainActivity, "Ошибка конфигурации VPN", Toast.LENGTH_LONG).show()
                            return
                        }

                        val permissionIntent = VpnService.prepare(this@MainActivity)
                        if (permissionIntent != null) {
                            pendingConfig = config
                            pendingConnectionId = selectedServer.id
                            vpnPermissionLauncher.launch(permissionIntent)
                        } else {
                            AutoVlessVpnService.start(this@MainActivity, config, selectedServer.id)
                        }
                    } catch (e: Exception) {
                        VpnStatus.set(VpnConnectionState.ERROR)
                        Toast.makeText(this@MainActivity, "Ошибка запуска: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                // Refresh cabinet & servers
                fun refreshCabinetData() {
                    scope.launch {
                        isCabinetLoading = true
                        val profileResult = VihtApiClient.fetchCabinetProfile(this@MainActivity)
                        if (profileResult.isSuccess) {
                            val (prof, srvs) = profileResult.getOrThrow()
                            userProfile = prof
                            if (srvs.isNotEmpty()) {
                                servers = srvs
                                VihtPreferences.setSavedServers(this@MainActivity, srvs)
                            }
                        }

                        val devicesResult = VihtApiClient.fetchActiveDevices(this@MainActivity)
                        if (devicesResult.isSuccess) {
                            activeDevices = devicesResult.getOrThrow()
                        }
                        isCabinetLoading = false
                    }
                }

                // Register deep link listener
                LaunchedEffect(Unit) {
                    onDeepLinkReceived = {
                        isLoggedIn = true
                        refreshCabinetData()
                    }
                    if (isLoggedIn) {
                        refreshCabinetData()
                    }
                }

                // Ping measurer
                fun measureAllPings() {
                    if (servers.isEmpty()) return
                    scope.launch {
                        isMeasuringPing = true
                        val updated = withContext(Dispatchers.IO) {
                            servers.map { srv ->
                                async {
                                    val ping = VihtApiClient.measurePing(srv.host, srv.port)
                                    srv.copy(ping = ping ?: srv.ping)
                                }
                            }.awaitAll()
                        }
                        servers = updated
                        VihtPreferences.setSavedServers(this@MainActivity, updated)
                        isMeasuringPing = false
                    }
                }

                // Back handling
                BackHandler(enabled = !isLoggedIn || currentSubScreen != SubScreen.NONE || currentTab != VihtTab.HOME) {
                    if (!isLoggedIn) {
                        finish()
                    } else if (currentSubScreen != SubScreen.NONE) {
                        currentSubScreen = SubScreen.NONE
                    } else if (currentTab != VihtTab.HOME) {
                        currentTab = VihtTab.HOME
                    }
                }

                // UI Root
                if (!isLoggedIn) {
                    // FIRST LAUNCH GATE: WELCOME & AUTHENTICATION ONLY
                    VihtAuthScreen(
                        isWelcomeMode = true,
                        onAuthSuccess = {
                            isLoggedIn = true
                            refreshCabinetData()
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VihtBgMain)
                    ) {
                        // Screens
                        when (currentSubScreen) {
                            SubScreen.AUTH -> {
                                VihtAuthScreen(
                                    isWelcomeMode = false,
                                    onBack = { currentSubScreen = SubScreen.NONE },
                                    onAuthSuccess = {
                                        currentSubScreen = SubScreen.NONE
                                        refreshCabinetData()
                                    }
                                )
                            }
                            SubScreen.APP_ROUTING -> {
                                AppRoutingScreen(
                                    onBack = { currentSubScreen = SubScreen.NONE }
                                )
                            }
                            SubScreen.GENERAL -> {
                                GeneralScreen(
                                    onBack = { currentSubScreen = SubScreen.NONE }
                                )
                            }
                            SubScreen.LOGS -> {
                                LogsScreen(
                                    onBack = { currentSubScreen = SubScreen.NONE }
                                )
                            }
                            SubScreen.NONE -> {
                                when (currentTab) {
                                    VihtTab.HOME -> {
                                        VihtHomeScreen(
                                            vpnState = vpnState,
                                            selectedServer = selectedServer,
                                            userProfile = userProfile,
                                            bypassRussia = bypassRussia,
                                            onBypassRussiaChange = { enabled ->
                                                bypassRussia = enabled
                                                VihtPreferences.setBypassRussianSites(this@MainActivity, enabled)
                                            },
                                            onPowerClick = { toggleVpn() },
                                            onServerSelectClick = { currentTab = VihtTab.SERVERS },
                                            onAppRoutingClick = { currentSubScreen = SubScreen.APP_ROUTING },
                                            onCabinetClick = { currentTab = VihtTab.CABINET }
                                        )
                                    }
                                    VihtTab.SERVERS -> {
                                        VihtServersScreen(
                                            servers = servers,
                                            selectedServerId = selectedServerId,
                                            isMeasuringPing = isMeasuringPing,
                                            onMeasurePingClick = { measureAllPings() },
                                            onServerSelect = { srv ->
                                                selectedServerId = srv.id
                                                VihtPreferences.setSelectedServerId(this@MainActivity, srv.id)
                                                // Auto-reconnect if connected
                                                if (vpnState == VpnConnectionState.CONNECTED) {
                                                    toggleVpn()
                                                    toggleVpn()
                                                }
                                                currentTab = VihtTab.HOME
                                            }
                                        )
                                    }
                                    VihtTab.CABINET -> {
                                        VihtCabinetScreen(
                                            userProfile = userProfile,
                                            activeDevices = activeDevices,
                                            isLoading = isCabinetLoading,
                                            onRefresh = { refreshCabinetData() },
                                            onDisconnectDevice = { dev ->
                                                scope.launch {
                                                    val res = VihtApiClient.disconnectDevice(this@MainActivity, dev.hwid)
                                                    if (res.isSuccess) {
                                                        Toast.makeText(this@MainActivity, "Устройство отключено", Toast.LENGTH_SHORT).show()
                                                        refreshCabinetData()
                                                    } else {
                                                        Toast.makeText(this@MainActivity, "Не удалось отключить", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onOpenAuth = { currentSubScreen = SubScreen.AUTH },
                                            onLogout = {
                                                VihtPreferences.clearAuth(this@MainActivity)
                                                isLoggedIn = false
                                                servers = emptyList()
                                                userProfile = null
                                                currentTab = VihtTab.HOME
                                                Toast.makeText(this@MainActivity, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                    VihtTab.SETTINGS -> {
                                        VihtSettingsScreen(
                                            bypassRussia = bypassRussia,
                                            onBypassRussiaChange = { enabled ->
                                                bypassRussia = enabled
                                                VihtPreferences.setBypassRussianSites(this@MainActivity, enabled)
                                            },
                                            autoBoot = autoBoot,
                                            onAutoBootChange = { enabled ->
                                                autoBoot = enabled
                                                VihtPreferences.setAutoConnectOnBoot(this@MainActivity, enabled)
                                            },
                                            killSwitch = killSwitch,
                                            onKillSwitchChange = { enabled ->
                                                killSwitch = enabled
                                                VihtPreferences.setKillSwitch(this@MainActivity, enabled)
                                            },
                                            onAppRoutingClick = { currentSubScreen = SubScreen.APP_ROUTING },
                                            onGeneralLanguageClick = { currentSubScreen = SubScreen.GENERAL },
                                            onLogsClick = { currentSubScreen = SubScreen.LOGS }
                                        )
                                    }
                                }

                                // Floating Island Navigation Bar at the bottom
                                VihtFloatingIslandNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { tab: VihtTab -> currentTab = tab },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
