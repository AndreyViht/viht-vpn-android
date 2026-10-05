package com.pingwin.vpn.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.VihtApiClient
import com.pingwin.vpn.VihtPreferences
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
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun VihtAuthScreen(
    onBack: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf("telegram") }

    // Telegram Auth State
    var telegramIdInput by remember { mutableStateOf(VihtPreferences.getTelegramId(context) ?: "") }
    var sentCode by remember { mutableStateOf("") }
    var enteredCode by remember { mutableStateOf("") }
    var isSendingCode by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }
    var codeSentSuccess by remember { mutableStateOf(false) }

    // Token Auth State
    var tokenInput by remember { mutableStateOf("") }
    var isCheckingToken by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VihtBgMain)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp)
    ) {
        // Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = VihtTextPrimary
                )
            }
            Text(
                text = "Вход в Viht VPN",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VihtTextPrimary
            )
        }

        // Subtitle
        Text(
            text = "Синхронизируйте ваши устройства и подписку с личным кабинетом",
            fontSize = 13.sp,
            color = VihtTextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs: Telegram / Token / VK & Yandex
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuthTabButton(
                text = "Telegram",
                selected = selectedTab == "telegram",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectedTab = "telegram"
                    errorMessage = null
                }
            )
            AuthTabButton(
                text = "Ключ",
                selected = selectedTab == "token",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectedTab = "token"
                    errorMessage = null
                }
            )
            AuthTabButton(
                text = "VK / Яндекс",
                selected = selectedTab == "oauth",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectedTab = "oauth"
                    errorMessage = null
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VihtAccentRed.copy(alpha = 0.15f))
                    .border(1.dp, VihtAccentRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    fontSize = 12.sp,
                    color = VihtAccentRed
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // TAB 1: TELEGRAM
        if (selectedTab == "telegram") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Авторизация по Telegram ID",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Бот @vpnvihtbot отправит одноразовый 6-значный проверочный код.",
                            fontSize = 12.sp,
                            color = VihtTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = telegramIdInput,
                            onValueChange = { telegramIdInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Ваш числовой Telegram ID") },
                            placeholder = { Text("Например: 123456789") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VihtNeonCyan,
                                unfocusedBorderColor = VihtBorderSubtle,
                                focusedTextColor = VihtTextPrimary,
                                unfocusedTextColor = VihtTextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Button: Send Code
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (telegramIdInput.isNotBlank() && !isSendingCode) VihtNeonCyan else VihtBgElevated)
                                .clickable(enabled = telegramIdInput.isNotBlank() && !isSendingCode) {
                                    scope.launch {
                                        isSendingCode = true
                                        errorMessage = null
                                        val code = String.format("%06d", Random.nextInt(100000, 999999))
                                        sentCode = code
                                        val result = VihtApiClient.sendTelegramAuthCode(telegramIdInput, code)
                                        isSendingCode = false
                                        if (result.isSuccess) {
                                            codeSentSuccess = true
                                            Toast.makeText(context, "Код отправлен в бота @vpnvihtbot!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            errorMessage = result.exceptionOrNull()?.message ?: "Не удалось отправить код"
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(color = VihtBgMain, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = VihtBgMain, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (codeSentSuccess) "Отправить код повторно" else "Получить проверочный код",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VihtBgMain
                                    )
                                }
                            }
                        }

                        // Code Input Block
                        if (codeSentSuccess) {
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = enteredCode,
                                onValueChange = { enteredCode = it.take(6) },
                                label = { Text("Код подтверждения из бота") },
                                placeholder = { Text("6 цифр") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VihtNeonGreen,
                                    unfocusedBorderColor = VihtBorderSubtle,
                                    focusedTextColor = VihtTextPrimary,
                                    unfocusedTextColor = VihtTextPrimary
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (enteredCode.length == 6) VihtNeonGreen else VihtBgElevated)
                                    .clickable(enabled = enteredCode.length == 6 && !isVerifyingCode) {
                                        if (enteredCode == sentCode) {
                                            VihtPreferences.setTelegramId(context, telegramIdInput)
                                            VihtPreferences.setAuthToken(context, "tg:$telegramIdInput")
                                            Toast.makeText(context, "Авторизация успешна!", Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            errorMessage = "Неверный код. Проверьте сообщение в @vpnvihtbot."
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Подтвердить и войти",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VihtBgMain
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Link to bot
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vpnvihtbot"))
                                    context.startActivity(intent)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Узнать свой ID в боте @vpnvihtbot",
                                fontSize = 12.sp,
                                color = VihtNeonCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Open", tint = VihtNeonCyan, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // TAB 2: TOKEN / SUBSCRIPTION LINK
        if (selectedTab == "token") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Вход по ключу или ссылке",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Вставьте ссылку подписки или токен (например, sub:12345 или https://anviht.ru/subscription-link/...)",
                            fontSize = 12.sp,
                            color = VihtTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Ключ или ссылка подписки") },
                            placeholder = { Text("sub:xxxx-xxxx") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clip.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!item.isNullOrBlank()) {
                                        tokenInput = item
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = VihtNeonCyan)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VihtNeonCyan,
                                unfocusedBorderColor = VihtBorderSubtle,
                                focusedTextColor = VihtTextPrimary,
                                unfocusedTextColor = VihtTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (tokenInput.isNotBlank()) VihtNeonGreen else VihtBgElevated)
                                .clickable(enabled = tokenInput.isNotBlank() && !isCheckingToken) {
                                    scope.launch {
                                        isCheckingToken = true
                                        errorMessage = null

                                        var clean = tokenInput.trim()
                                        if (clean.contains("subscription-link/")) {
                                            clean = clean.substringAfter("subscription-link/").substringBefore("?")
                                        }

                                        val res = VihtApiClient.fetchCabinetProfile(context, token = clean)
                                        isCheckingToken = false

                                        if (res.isSuccess) {
                                            VihtPreferences.setAuthToken(context, clean)
                                            Toast.makeText(context, "Вход выполнен успешно!", Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            errorMessage = "Не удалось загрузить подписку по этому ключу. Проверьте правильность ссылки."
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCheckingToken) {
                                CircularProgressIndicator(color = VihtBgMain, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text(
                                    text = "Войти по ключу",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VihtBgMain
                                )
                            }
                        }
                    }
                }
            }
        }

        // TAB 3: VK & YANDEX
        if (selectedTab == "oauth") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Вход через VK и Яндекс",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Авторизуйтесь в личном кабинете на сайте Viht, чтобы мгновенно получить доступ.",
                            fontSize = 12.sp,
                            color = VihtTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // VK ID Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0077FF))
                                .clickable {
                                    val hwid = VihtPreferences.getHwid(context)
                                    val url = "https://anviht.ru/auth?platform=android&hwid=$hwid"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Войти через VK ID",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Yandex Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFC3F1D))
                                .clickable {
                                    val hwid = VihtPreferences.getHwid(context)
                                    val url = "https://anviht.ru/auth?platform=android&provider=yandex&hwid=$hwid"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Войти через Яндекс ID",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VihtNeonCyan.copy(alpha = 0.2f) else VihtBgElevated)
            .border(
                1.dp,
                if (selected) VihtNeonCyan else VihtBorderSubtle,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) VihtNeonCyan else VihtTextSecondary
        )
    }
}
