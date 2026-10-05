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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.ui.theme.VihtAccentRed
import com.pingwin.vpn.ui.theme.VihtBgCard
import com.pingwin.vpn.ui.theme.VihtBgElevated
import com.pingwin.vpn.ui.theme.VihtBgMain
import com.pingwin.vpn.ui.theme.VihtBorderCyan
import com.pingwin.vpn.ui.theme.VihtBorderSubtle
import com.pingwin.vpn.ui.theme.VihtElectricPurple
import com.pingwin.vpn.ui.theme.VihtNeonCyan
import com.pingwin.vpn.ui.theme.VihtNeonGreen
import com.pingwin.vpn.ui.theme.VihtTextMuted
import com.pingwin.vpn.ui.theme.VihtTextPrimary
import com.pingwin.vpn.ui.theme.VihtTextSecondary

@Composable
fun VihtSettingsScreen(
    bypassRussia: Boolean,
    onBypassRussiaChange: (Boolean) -> Unit,
    autoBoot: Boolean,
    onAutoBootChange: (Boolean) -> Unit,
    killSwitch: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    onAppRoutingClick: () -> Unit,
    onGeneralLanguageClick: () -> Unit,
    onLogsClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VihtBgMain)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Настройки",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VihtTextPrimary
            )
            Text(
                text = "Конфигурация сети, маршрутизации и безопасности",
                fontSize = 12.sp,
                color = VihtTextSecondary
            )
        }

        // Section 1: МАРШРУТИЗАЦИЯ И СЕТЬ
        SettingsSectionHeader(title = "МАРШРУТИЗАЦИЯ И ТРАФИК")

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bypass Russia
            VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
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
                        SettingsIconBox(icon = Icons.Default.Public, tint = VihtNeonGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Обход сайтов РФ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Госуслуги, банки и российские сервисы напрямую",
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

            // App Routing
            VihtGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppRoutingClick() }
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
                        SettingsIconBox(icon = Icons.Default.Apps, tint = VihtNeonCyan)
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
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = VihtTextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: АВТОМАТИЗАЦИЯ И БЕЗОПАСНОСТЬ
        SettingsSectionHeader(title = "АВТОМАТИЗАЦИЯ И БЕЗОПАСНОСТЬ")

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Auto-boot
            VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
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
                        SettingsIconBox(icon = Icons.Default.Power, tint = VihtElectricPurple)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Автоподключение при старте",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Включать VPN автоматически при загрузке Android",
                                fontSize = 11.sp,
                                color = VihtTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = autoBoot,
                        onCheckedChange = onAutoBootChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VihtElectricPurple,
                            checkedTrackColor = VihtElectricPurple.copy(alpha = 0.3f),
                            uncheckedThumbColor = VihtTextMuted,
                            uncheckedTrackColor = VihtBgElevated
                        )
                    )
                }
            }

            // Kill Switch
            VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
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
                        SettingsIconBox(icon = Icons.Default.Security, tint = VihtAccentRed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Kill Switch (Защита от утечек)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Блокировать интернет при случайном обрыве VPN",
                                fontSize = 11.sp,
                                color = VihtTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = killSwitch,
                        onCheckedChange = onKillSwitchChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VihtAccentRed,
                            checkedTrackColor = VihtAccentRed.copy(alpha = 0.3f),
                            uncheckedThumbColor = VihtTextMuted,
                            uncheckedTrackColor = VihtBgElevated
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: СИСТЕМА И ДИАГНОСТИКА
        SettingsSectionHeader(title = "СИСТЕМА И ДИАГНОСТИКА")

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Language
            VihtGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGeneralLanguageClick() }
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
                        SettingsIconBox(icon = Icons.Default.Language, tint = VihtNeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Язык приложения",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Русский / English",
                                fontSize = 11.sp,
                                color = VihtTextSecondary
                            )
                        }
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = VihtTextSecondary)
                }
            }

            // Diagnostic Logs
            VihtGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLogsClick() }
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
                        SettingsIconBox(icon = Icons.Default.Assessment, tint = VihtTextSecondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Диагностические логи",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Журнал подключений и sing-box Core",
                                fontSize = 11.sp,
                                color = VihtTextSecondary
                            )
                        }
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = VihtTextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 4: О ПРОГРАММЕ
        SettingsSectionHeader(title = "О ПРОГРАММЕ")

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBox(icon = Icons.Default.Info, tint = VihtNeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Viht VPN Android",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = VihtTextPrimary
                            )
                            Text(
                                text = "Версия 1.0.0 (Ядро sing-box 1.13.12)",
                                fontSize = 11.sp,
                                color = VihtNeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VihtBgElevated)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vpnvihtbot"))
                                context.startActivity(intent)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.SupportAgent, contentDescription = "Support", tint = VihtNeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Поддержка в Telegram: @vpnvihtbot", fontSize = 12.sp, color = VihtTextPrimary)
                        }
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Open", tint = VihtTextSecondary, modifier = Modifier.size(14.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VihtBgElevated)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://anviht.ru"))
                                context.startActivity(intent)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Public, contentDescription = "Site", tint = VihtNeonGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Официальный сайт: anviht.ru", fontSize = 12.sp, color = VihtTextPrimary)
                        }
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Open", tint = VihtTextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = VihtTextMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsIconBox(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
