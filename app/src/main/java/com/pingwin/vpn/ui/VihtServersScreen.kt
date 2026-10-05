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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.VihtServer
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

@Composable
fun VihtServersScreen(
    servers: List<VihtServer>,
    selectedServerId: String,
    isMeasuringPing: Boolean,
    onMeasurePingClick: () -> Unit,
    onServerSelect: (VihtServer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VihtBgMain)
            .statusBarsPadding()
            .padding(bottom = 100.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Серверы и ключи",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VihtTextPrimary
                )
                Text(
                    text = if (servers.isEmpty()) "Синхронизация..." else "${servers.size} доступных локаций",
                    fontSize = 12.sp,
                    color = VihtTextSecondary
                )
            }

            // Ping Button
            if (servers.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(VihtNeonCyan.copy(alpha = 0.12f))
                        .border(1.dp, VihtNeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isMeasuringPing) { onMeasurePingClick() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMeasuringPing) {
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
                            text = if (isMeasuringPing) "Замер..." else "Пинг",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VihtNeonCyan
                        )
                    }
                }
            }
        }

        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VihtNeonCyan, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Синхронизация ваших персональных серверов...",
                        fontSize = 13.sp,
                        color = VihtTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Servers List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                val isSelected = server.id == selectedServerId

                VihtGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onServerSelect(server) },
                    borderColor = if (isSelected) VihtBorderActive else VihtBorderSubtle,
                    backgroundColor = if (isSelected) VihtBgElevated else VihtBgCard
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
                            Text(
                                text = server.flag,
                                fontSize = 26.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = server.name,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) VihtNeonGreen else VihtTextPrimary,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Badges
                                    if (server.isRecommended) {
                                        BadgeChip(text = "Рекомендуемый", color = VihtNeonCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else if (server.isYouTubeNoAds) {
                                        BadgeChip(text = "300 Мбит/с", color = VihtAccentRed)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else if (server.isLte) {
                                        BadgeChip(text = "Обход ТСПУ", color = Color(0xFFFFB800))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    // Ping
                                    if (server.ping != null) {
                                        val pingColor = when {
                                            server.ping < 50 -> VihtNeonGreen
                                            server.ping < 100 -> VihtNeonCyan
                                            server.ping < 180 -> Color(0xFFFFB800)
                                            else -> VihtAccentRed
                                        }
                                        Text(
                                            text = "${server.ping} ms",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = pingColor
                                        )
                                    }
                                }
                            }
                        }

                        // Radio / check indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) VihtNeonGreen else Color.Transparent)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) VihtNeonGreen else VihtTextMuted,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = VihtBgMain,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
