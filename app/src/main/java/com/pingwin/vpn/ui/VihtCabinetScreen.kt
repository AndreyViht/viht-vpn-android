package com.pingwin.vpn.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.VihtDevice
import com.pingwin.vpn.VihtUserProfile
import com.pingwin.vpn.ui.theme.VihtAccentRed
import com.pingwin.vpn.ui.theme.VihtBgCard
import com.pingwin.vpn.ui.theme.VihtBgElevated
import com.pingwin.vpn.ui.theme.VihtBgMain
import com.pingwin.vpn.ui.theme.VihtBorderActive
import com.pingwin.vpn.ui.theme.VihtBorderCyan
import com.pingwin.vpn.ui.theme.VihtBorderSubtle
import com.pingwin.vpn.ui.theme.VihtElectricPurple
import com.pingwin.vpn.ui.theme.VihtNeonCyan
import com.pingwin.vpn.ui.theme.VihtNeonGreen
import com.pingwin.vpn.ui.theme.VihtTextMuted
import com.pingwin.vpn.ui.theme.VihtTextPrimary
import com.pingwin.vpn.ui.theme.VihtTextSecondary

@Composable
fun VihtCabinetScreen(
    userProfile: VihtUserProfile?,
    activeDevices: List<VihtDevice>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDisconnectDevice: (VihtDevice) -> Unit,
    onOpenAuth: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var deviceToDisconnect by remember { mutableStateOf<VihtDevice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VihtBgMain)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Личный кабинет",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VihtTextPrimary
                )
                Text(
                    text = if (userProfile?.telegramId != null) {
                        "Telegram ID: ${userProfile.telegramId}"
                    } else if (userProfile?.email != null) {
                        userProfile.email
                    } else {
                        "Анонимный доступ"
                    },
                    fontSize = 12.sp,
                    color = VihtTextSecondary
                )
            }

            // Refresh Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VihtNeonCyan.copy(alpha = 0.12f))
                    .border(1.dp, VihtNeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !isLoading) { onRefresh() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = VihtNeonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = VihtNeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Обновить",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VihtNeonCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Subscription Status Card
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            VihtGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (userProfile?.isActive == true) VihtBorderActive else VihtBorderSubtle
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VihtNeonGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Subscription",
                                    tint = VihtNeonGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (userProfile?.isActive == true) "ПОДПИСКА АКТИВНА" else "ПОДПИСКА НЕ АКТИВНА",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = if (userProfile?.isActive == true) VihtNeonGreen else VihtAccentRed
                                )
                                Text(
                                    text = if (userProfile?.isTrial == true) "Пробный период" else "Полный доступ без лимита",
                                    fontSize = 12.sp,
                                    color = VihtTextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VihtNeonGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${userProfile?.daysRemaining ?: 30} дн.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VihtNeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val progress = ((userProfile?.daysRemaining ?: 30).toFloat() / 30f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = VihtNeonGreen,
                        trackColor = VihtBgElevated
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Renew / Bot action button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VihtNeonGreen)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vpnvihtbot"))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Продлить через бота",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = VihtBgMain
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open",
                                tint = VihtBgMain,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Devices Section
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "АКТИВНЫЕ УСТРОЙСТВА (${activeDevices.size} из ${userProfile?.maxDevices ?: 3})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = VihtTextMuted
                )
            }

            if (activeDevices.isEmpty()) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Устройства загружаются...",
                            fontSize = 13.sp,
                            color = VihtTextSecondary
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeDevices.forEach { device ->
                        VihtGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (device.isCurrent) VihtBorderActive else VihtBorderSubtle
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
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
                                            .background(if (device.isCurrent) VihtNeonGreen.copy(alpha = 0.15f) else VihtNeonCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = "Device",
                                            tint = if (device.isCurrent) VihtNeonGreen else VihtNeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = device.model,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = VihtTextPrimary
                                            )
                                            if (device.isCurrent) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(VihtNeonGreen.copy(alpha = 0.15f))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "Это устройство",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = VihtNeonGreen
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "HWID: ${device.hwid.take(12)}... • ${device.lastActive}",
                                            fontSize = 11.sp,
                                            color = VihtTextSecondary
                                        )
                                    }
                                }

                                if (!device.isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(VihtAccentRed.copy(alpha = 0.12f))
                                            .clickable { deviceToDisconnect = device }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Отвязать",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = VihtAccentRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions: Change Account / Logout
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, VihtBorderCyan, RoundedCornerShape(12.dp))
                    .clickable { onOpenAuth() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Авторизация (Telegram / VK / Яндекс)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VihtNeonCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, VihtAccentRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onLogout() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Выйти из аккаунта",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VihtAccentRed
                )
            }
        }
    }

    // Confirmation Dialog for disconnecting device
    deviceToDisconnect?.let { dev ->
        AlertDialog(
            onDismissRequest = { deviceToDisconnect = null },
            title = {
                Text(text = "Отвязать устройство?", color = VihtTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Устройство \"${dev.model}\" (${dev.hwid.take(12)}...) будет отключено от вашей подписки Viht VPN.",
                    color = VihtTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDisconnectDevice(dev)
                        deviceToDisconnect = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VihtAccentRed)
                ) {
                    Text(text = "Отвязать", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDisconnect = null }) {
                    Text(text = "Отмена", color = VihtTextSecondary)
                }
            },
            containerColor = VihtBgElevated
        )
    }
}
