package com.pingwin.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.VihtServer
import com.pingwin.vpn.VihtUserProfile
import com.pingwin.vpn.VpnConnectionState
import com.pingwin.vpn.ui.theme.VihtAccentRed
import com.pingwin.vpn.ui.theme.VihtBgCard
import com.pingwin.vpn.ui.theme.VihtBgElevated
import com.pingwin.vpn.ui.theme.VihtBgMain
import com.pingwin.vpn.ui.theme.VihtBorderActive
import com.pingwin.vpn.ui.theme.VihtBorderCyan
import com.pingwin.vpn.ui.theme.VihtBorderSubtle
import com.pingwin.vpn.ui.theme.VihtNeonCyan
import com.pingwin.vpn.ui.theme.VihtNeonGreen
import com.pingwin.vpn.ui.theme.VihtTextMuted
import com.pingwin.vpn.ui.theme.VihtTextPrimary
import com.pingwin.vpn.ui.theme.VihtTextSecondary
import kotlinx.coroutines.delay

@Composable
fun VihtHomeScreen(
    vpnState: VpnConnectionState,
    selectedServer: VihtServer,
    userProfile: VihtUserProfile?,
    bypassRussia: Boolean,
    onBypassRussiaChange: (Boolean) -> Unit,
    onPowerClick: () -> Unit,
    onServerSelectClick: () -> Unit,
    onAppRoutingClick: () -> Unit,
    onCabinetClick: () -> Unit
) {
    val isConnected = vpnState == VpnConnectionState.CONNECTED
    val isConnecting = vpnState == VpnConnectionState.CONNECTING

    // Session duration tracker
    var sessionSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isConnected) {
        if (isConnected) {
            sessionSeconds = 0L
            while (true) {
                delay(1000)
                sessionSeconds++
            }
        } else {
            sessionSeconds = 0L
        }
    }

    val hours = sessionSeconds / 3600
    val minutes = (sessionSeconds % 3600) / 60
    val seconds = sessionSeconds % 60
    val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VihtBgMain)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp)
    ) {
        // Top bar
        VihtTopBar(
            title = "VIHT VPN",
            subtitle = if (isConnected) "Защищенное соединение" else "Готов к работе",
            badgeText = if (userProfile != null && userProfile.isActive) {
                "${userProfile.daysRemaining} дн."
            } else if (isConnected) {
                "Онлайн"
            } else {
                "Кабинет"
            },
            onBadgeClick = onCabinetClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Center Power Toggle Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            NeonPowerButton(
                vpnState = vpnState,
                onClick = onPowerClick
            )
        }

        // Connection Status Banner
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isConnected -> "ЗАЩИЩЕНО"
                    isConnecting -> "ПОДКЛЮЧЕНИЕ..."
                    vpnState == VpnConnectionState.ERROR -> "ОШИБКА ПОДКЛЮЧЕНИЯ"
                    else -> "ОТКЛЮЧЕНО"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = when {
                    isConnected -> VihtNeonGreen
                    isConnecting -> VihtNeonCyan
                    vpnState == VpnConnectionState.ERROR -> VihtAccentRed
                    else -> VihtTextSecondary
                }
            )

            if (isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(VihtNeonGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Сессия: $timeFormatted",
                        fontSize = 13.sp,
                        color = VihtTextSecondary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Нажмите для безопасного подключения",
                    fontSize = 12.sp,
                    color = VihtTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Selected Server Card
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "ТЕКУЩИЙ СЕРВЕР",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = VihtTextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            VihtGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onServerSelectClick() },
                borderColor = if (isConnected) VihtBorderActive else VihtBorderSubtle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = selectedServer.flag,
                            fontSize = 28.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )

                        Column {
                            Text(
                                text = selectedServer.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedServer.host,
                                    fontSize = 12.sp,
                                    color = VihtTextSecondary
                                )
                                if (selectedServer.ping != null) {
                                    Text(
                                        text = " • ${selectedServer.ping} ms",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedServer.ping < 60) VihtNeonGreen else VihtNeonCyan
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(VihtNeonCyan.copy(alpha = 0.12f))
                            .border(1.dp, VihtNeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Сменить",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VihtNeonCyan
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Controls
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "БЫСТРЫЕ НАСТРОЙКИ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = VihtTextMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Bypass Russia Switch
            VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VihtNeonGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Bypass RU",
                                tint = VihtNeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Обход сайтов РФ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Банки и сервисы РФ работают напрямую",
                                fontSize = 11.sp,
                                color = VihtTextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = bypassRussia,
                        onCheckedChange = onBypassRussiaChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VihtNeonGreen,
                            checkedTrackColor = VihtNeonGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = VihtTextMuted,
                            uncheckedTrackColor = VihtBgElevated
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App Routing Quick Entry
            VihtGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppRoutingClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(VihtNeonCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "App Routing",
                                tint = VihtNeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Раздельное туннелирование",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Выбрать приложения через VPN или напрямую",
                                fontSize = 11.sp,
                                color = VihtTextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = VihtTextSecondary
                    )
                }
            }
        }
    }
}
