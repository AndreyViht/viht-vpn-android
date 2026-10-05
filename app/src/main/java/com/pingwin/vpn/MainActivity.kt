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

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val token = data.getQueryParameter("token") ?: data.getQueryParameter("sub")
            val tgId = data.getQueryParameter("tg_id") ?: data.getQueryParameter("telegram_id")

            if (!token.isNullOrBlank()) {
                VihtPreferences.setAuthToken(this, token)
                Toast.makeText(this, "Токен подписки получен!", Toast.LENGTH_SHORT).show()
            }
            if (!tgId.isNullOrBlank()) {
                VihtPreferences.setTelegramId(this, tgId)
                Toast.makeText(this, "Telegram ID синхронизирован!", Toast.LENGTH_SHORT).show()
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

                // Navigation states
                var currentTab by rememberSaveable { mutableStateOf(VihtTab.HOME) }
                var currentSubScreen by rememberSaveable { mutableStateOf(SubScreen.NONE) }

                // Servers and Profile state
                var servers by remember { mutableStateOf(VihtApiClient.DEFAULT_SERVERS) }
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
                    servers.find { it.id == selectedServerId } ?: servers.firstOrNull() ?: VihtApiClient.DEFAULT_SERVERS.first()
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
                            Toast.makeText(this@MainActivity, "Ключ сервера не найден", Toast.LENGTH_SHORT).show()
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
                            }
                        }

                        val devicesResult = VihtApiClient.fetchActiveDevices(this@MainActivity)
                        if (devicesResult.isSuccess) {
                            activeDevices = devicesResult.getOrThrow()
                        }
                        isCabinetLoading = false
                    }
                }

                // Ping measurer
                fun measureAllPings() {
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
                        isMeasuringPing = false
                    }
                }

                // Initial sync on startup
                LaunchedEffect(Unit) {
                    refreshCabinetData()
                }

                // Back handling
                BackHandler(enabled = currentSubScreen != SubScreen.NONE || currentTab != VihtTab.HOME) {
                    if (currentSubScreen != SubScreen.NONE) {
                        currentSubScreen = SubScreen.NONE
                    } else if (currentTab != VihtTab.HOME) {
                        currentTab = VihtTab.HOME
                    }
                }

                // UI Root
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VihtBgMain)
                ) {
                    // Screens
                    when (currentSubScreen) {
                        SubScreen.AUTH -> {
                            VihtAuthScreen(
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
                                            userProfile = null
                                            Toast.makeText(this@MainActivity, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
                                            refreshCabinetData()
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

                            // Bottom Navigation pinned
                            VihtBottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = { tab -> currentTab = tab }
                            )
                        }
                    }
                }
            }
        }
    }
}
