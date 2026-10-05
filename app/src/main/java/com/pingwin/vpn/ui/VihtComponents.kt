package com.pingwin.vpn.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.VpnConnectionState
import com.pingwin.vpn.ui.theme.VihtAccentRed
import com.pingwin.vpn.ui.theme.VihtBgCard
import com.pingwin.vpn.ui.theme.VihtBgElevated
import com.pingwin.vpn.ui.theme.VihtBgMain
import com.pingwin.vpn.ui.theme.VihtBgSurface
import com.pingwin.vpn.ui.theme.VihtBorderActive
import com.pingwin.vpn.ui.theme.VihtBorderCyan
import com.pingwin.vpn.ui.theme.VihtBorderSubtle
import com.pingwin.vpn.ui.theme.VihtElectricPurple
import com.pingwin.vpn.ui.theme.VihtNeonCyan
import com.pingwin.vpn.ui.theme.VihtNeonGreen
import com.pingwin.vpn.ui.theme.VihtNeonGreenGlow
import com.pingwin.vpn.ui.theme.VihtTextMuted
import com.pingwin.vpn.ui.theme.VihtTextPrimary
import com.pingwin.vpn.ui.theme.VihtTextSecondary

enum class VihtTab {
    HOME,
    SERVERS,
    CABINET,
    SETTINGS
}

@Composable
fun VihtGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = VihtBorderSubtle,
    backgroundColor: Color = VihtBgCard,
    cornerRadius: Int = 20,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius.dp)
            ),
        shape = RoundedCornerShape(cornerRadius.dp),
        color = backgroundColor
    ) {
        content()
    }
}

@Composable
fun NeonPowerButton(
    vpnState: VpnConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = vpnState == VpnConnectionState.CONNECTED
    val isConnecting = vpnState == VpnConnectionState.CONNECTING
    val isError = vpnState == VpnConnectionState.ERROR

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting) 1.15f else if (isConnected) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected || isConnecting) 0.35f else 0.08f,
        targetValue = if (isConnecting) 0.75f else if (isConnected) 0.55f else 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 700 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    val activeColor = when {
        isConnected -> VihtNeonGreen
        isConnecting -> VihtNeonCyan
        isError -> VihtAccentRed
        else -> VihtTextMuted
    }

    val glowColor = when {
        isConnected -> VihtNeonGreen.copy(alpha = haloAlpha)
        isConnecting -> VihtNeonCyan.copy(alpha = haloAlpha)
        isError -> VihtAccentRed.copy(alpha = 0.4f)
        else -> Color(0x15FFFFFF)
    }

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing halo
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    )
                )
        )

        // Middle ring
        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            activeColor,
                            if (isConnected) VihtNeonCyan else activeColor.copy(alpha = 0.4f),
                            activeColor
                        )
                    ),
                    shape = CircleShape
                )
                .background(VihtBgSurface)
        )

        // Inner clickable power button
        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VihtBgElevated,
                            VihtBgCard
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = activeColor.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "VPN Power",
                    tint = activeColor,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when {
                        isConnected -> "ОТКЛЮЧИТЬ"
                        isConnecting -> "СВЯЗЬ..."
                        isError -> "ОШИБКА"
                        else -> "ПОДКЛЮЧИТЬ"
                    },
                    color = activeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
fun VihtTopBar(
    title: String = "VIHT VPN",
    subtitle: String? = null,
    badgeText: String? = null,
    onBadgeClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(VihtNeonCyan.copy(alpha = 0.12f))
                    .border(1.dp, VihtNeonCyan.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Viht Shield",
                    tint = VihtNeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = VihtTextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = VihtTextSecondary
                    )
                }
            }
        }

        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VihtNeonGreen.copy(alpha = 0.15f))
                    .border(1.dp, VihtNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable(enabled = onBadgeClick != null) { onBadgeClick?.invoke() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(VihtNeonGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VihtNeonGreen
                    )
                }
            }
        }
    }
}

/**
 * Floating Island Navigation Bar ("островок как на айфоне внизу")
 */
@Composable
fun VihtFloatingIslandNavBar(
    currentTab: VihtTab,
    onTabSelected: (VihtTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xF20E1320),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        VihtNeonCyan.copy(alpha = 0.5f),
                        VihtElectricPurple.copy(alpha = 0.4f),
                        VihtNeonCyan.copy(alpha = 0.5f)
                    )
                )
            ),
            shadowElevation = 20.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingIslandItem(
                    icon = Icons.Default.Home,
                    label = "Главная",
                    selected = currentTab == VihtTab.HOME,
                    onClick = { onTabSelected(VihtTab.HOME) }
                )
                FloatingIslandItem(
                    icon = Icons.Default.Language,
                    label = "Серверы",
                    selected = currentTab == VihtTab.SERVERS,
                    onClick = { onTabSelected(VihtTab.SERVERS) }
                )
                FloatingIslandItem(
                    icon = Icons.Default.AccountCircle,
                    label = "Кабинет",
                    selected = currentTab == VihtTab.CABINET,
                    onClick = { onTabSelected(VihtTab.CABINET) }
                )
                FloatingIslandItem(
                    icon = Icons.Default.Settings,
                    label = "Настройки",
                    selected = currentTab == VihtTab.SETTINGS,
                    onClick = { onTabSelected(VihtTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
fun VihtBottomNavBar(
    currentTab: VihtTab,
    onTabSelected: (VihtTab) -> Unit,
    modifier: Modifier = Modifier
) {
    VihtFloatingIslandNavBar(currentTab = currentTab, onTabSelected = onTabSelected, modifier = modifier)
}

@Composable
private fun FloatingIslandItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (selected) VihtNeonCyan else VihtTextMuted
    val bgModifier = if (selected) {
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        VihtNeonCyan.copy(alpha = 0.22f),
                        VihtElectricPurple.copy(alpha = 0.18f)
                    )
                )
            )
            .border(1.dp, VihtNeonCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
    } else {
        Modifier.clip(RoundedCornerShape(22.dp))
    }

    Row(
        modifier = bgModifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (selected) 14.dp else 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = activeColor,
            modifier = Modifier.size(20.dp)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VihtNeonCyan
            )
        }
    }
}
