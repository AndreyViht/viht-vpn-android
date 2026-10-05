package com.pingwin.vpn.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingwin.vpn.AuthCallbackServer
import com.pingwin.vpn.VihtApiClient
import com.pingwin.vpn.VihtPreferences
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
import com.pingwin.vpn.ui.theme.VihtTextMuted
import com.pingwin.vpn.ui.theme.VihtTextPrimary
import com.pingwin.vpn.ui.theme.VihtTextSecondary
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun VihtAuthScreen(
    isWelcomeMode: Boolean = false,
    onBack: () -> Unit = {},
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

    // Browser OAuth State (Yandex & VK)
    var isWaitingBrowserAuth by remember { mutableStateOf(false) }
    var browserProviderName by remember { mutableStateOf("") }

    // Token Auth State
    var tokenInput by remember { mutableStateOf("") }
    var isCheckingToken by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            AuthCallbackServer.stop()
        }
    }

    val startBrowserOAuth = { provider: String ->
        browserProviderName = if (provider == "yandex") "Яндекс ID" else "VK ID"
        isWaitingBrowserAuth = true
        errorMessage = null

        AuthCallbackServer.start { token, subToken, tgId, email ->
            scope.launch {
                val effectiveToken = subToken.ifBlank { token }
                if (effectiveToken.isNotBlank()) {
                    VihtPreferences.setAuthToken(context, effectiveToken)
                }
                if (tgId.isNotBlank()) {
                    VihtPreferences.setTelegramId(context, tgId)
                }
                if (email.isNotBlank()) {
                    VihtPreferences.setUserEmail(context, email)
                }

                // Fetch full profile and clients
                val res = VihtApiClient.fetchCabinetProfile(context, token = effectiveToken, telegramId = tgId)
                AuthCallbackServer.stop()
                isWaitingBrowserAuth = false

                if (res.isSuccess) {
                    val (profile, servers) = res.getOrThrow()
                    VihtPreferences.setSavedServers(context, servers)
                    Toast.makeText(context, "Вход выполнен успешно!", Toast.LENGTH_SHORT).show()
                    onAuthSuccess()
                } else {
                    errorMessage = "Не удалось загрузить подписку. Попробуйте еще раз."
                }
            }
        }

        val cbUrl = "http://127.0.0.1:${AuthCallbackServer.PORT}/auth/callback"
        val loginUrl = "https://anviht.ru/lk?provider=$provider&desktop_callback=${Uri.encode(cbUrl)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VihtBgMain)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp)
    ) {
        if (!isWelcomeMode) {
            // Back Header for settings/cabinet modal
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
        } else {
            // Welcome Header
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing Logo Shield
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    VihtNeonCyan.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.5.dp, VihtNeonCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Viht VPN",
                        tint = VihtNeonCyan,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VIHT VPN",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = VihtTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Добро пожаловать!",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VihtNeonGreen
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Авторизуйтесь, чтобы подключить вашу подписку и серверы",
                    fontSize = 13.sp,
                    color = VihtTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Waiting for Browser OAuth dialog card
        if (isWaitingBrowserAuth) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = VihtNeonCyan
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = VihtNeonCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Авторизация через $browserProviderName...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Мы открыли страницу входа в браузере. Завершите вход, и приложение автоматически подключит вашу подписку.",
                            fontSize = 12.sp,
                            color = VihtTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VihtNeonCyan.copy(alpha = 0.15f))
                                    .border(1.dp, VihtNeonCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                        if (!clipText.isNullOrBlank()) {
                                            var clean = clipText
                                            if (clean.contains("/sub/")) {
                                                clean = clean.substringAfter("/sub/").substringBefore("?").substringBefore("/").trim()
                                            } else if (clean.contains("subscription-link/")) {
                                                clean = clean.substringAfter("subscription-link/").substringBefore("?").substringBefore("/").trim()
                                            }
                                            scope.launch {
                                                val res = VihtApiClient.fetchCabinetProfile(context, token = clean)
                                                if (res.isSuccess) {
                                                    val (profile, servers) = res.getOrThrow()
                                                    VihtPreferences.setAuthToken(context, clean)
                                                    VihtPreferences.setSavedServers(context, servers)
                                                    AuthCallbackServer.stop()
                                                    isWaitingBrowserAuth = false
                                                    Toast.makeText(context, "Вход выполнен успешно!", Toast.LENGTH_SHORT).show()
                                                    onAuthSuccess()
                                                } else {
                                                    Toast.makeText(context, "Ссылка в буфере не подошла: скопируйте ссылку подписки в кабинете", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "Буфер обмена пуст. Скопируйте ссылку подписки в кабинете.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = VihtNeonCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Вставить из буфера",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VihtNeonCyan
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VihtBgElevated)
                                    .clickable {
                                        AuthCallbackServer.stop()
                                        isWaitingBrowserAuth = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Отмена",
                                    fontSize = 12.sp,
                                    color = VihtTextMuted
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Tabs: Telegram / Яндекс / VK / Ключ
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
                text = "Яндекс ID",
                selected = selectedTab == "yandex",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectedTab = "yandex"
                    errorMessage = null
                }
            )
            AuthTabButton(
                text = "VK ID",
                selected = selectedTab == "vk",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectedTab = "vk"
                    errorMessage = null
                }
            )
            AuthTabButton(
                text = "Ключ",
                selected = selectedTab == "token",
                modifier = Modifier.weight(0.9f),
                onClick = {
                    selectedTab = "token"
                    errorMessage = null
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error Banner
        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VihtAccentRed.copy(alpha = 0.15f))
                    .border(1.dp, VihtAccentRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    fontSize = 12.sp,
                    color = VihtAccentRed
                )
            }
        }

        // TAB 1: TELEGRAM AUTH
        if (selectedTab == "telegram") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Вход по Telegram ID",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Код подтверждения придет прямо в нашего бота @vpnvihtbot в Telegram.",
                            fontSize = 12.sp,
                            color = VihtTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Input Telegram ID
                        OutlinedTextField(
                            value = telegramIdInput,
                            onValueChange = { telegramIdInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Ваш числовой Telegram ID") },
                            placeholder = { Text("Например, 712345678") },
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Send Code Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (telegramIdInput.isNotBlank()) VihtNeonCyan else VihtBgElevated)
                                .clickable(enabled = telegramIdInput.isNotBlank() && !isSendingCode) {
                                    scope.launch {
                                        isSendingCode = true
                                        errorMessage = null
                                        val code = Random.nextInt(100000, 999999).toString()
                                        val result = VihtApiClient.sendTelegramAuthCode(telegramIdInput, code)
                                        isSendingCode = false

                                        if (result.isSuccess) {
                                            sentCode = code
                                            codeSentSuccess = true
                                            Toast.makeText(context, "Код отправлен в бота!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            errorMessage = result.exceptionOrNull()?.message
                                                ?: "Не удалось отправить код. Убедитесь, что запустили @vpnvihtbot в Telegram."
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
                                            scope.launch {
                                                isVerifyingCode = true
                                                VihtPreferences.setTelegramId(context, telegramIdInput)
                                                VihtPreferences.setAuthToken(context, "tg:$telegramIdInput")

                                                // Fetch real profile & clients
                                                val res = VihtApiClient.fetchCabinetProfile(
                                                    context,
                                                    token = "tg:$telegramIdInput",
                                                    telegramId = telegramIdInput
                                                )
                                                isVerifyingCode = false

                                                if (res.isSuccess) {
                                                    val (profile, servers) = res.getOrThrow()
                                                    VihtPreferences.setSavedServers(context, servers)
                                                    Toast.makeText(context, "Вход выполнен успешно!", Toast.LENGTH_SHORT).show()
                                                    onAuthSuccess()
                                                } else {
                                                    errorMessage = "Не удалось получить ключи подписки. Проверьте ID."
                                                }
                                            }
                                        } else {
                                            errorMessage = "Неверный проверочный код. Посмотрите сообщение в @vpnvihtbot."
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isVerifyingCode) {
                                    CircularProgressIndicator(color = VihtBgMain, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    Text(
                                        text = "Подтвердить и войти",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VihtBgMain
                                    )
                                }
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

        // TAB 2: YANDEX ID
        if (selectedTab == "yandex") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Вход через Яндекс ID",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Авторизуйтесь в личном кабинете на anviht.ru через Яндекс, и приложение автоматически загрузит вашу подписку.",
                            fontSize = 12.sp,
                            color = VihtTextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Yandex Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFC3F1D))
                                .clickable { startBrowserOAuth("yandex") }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Я",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
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

        // TAB 3: VK ID
        if (selectedTab == "vk") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Вход через VK ID",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Авторизуйтесь через аккаунт ВКонтакте, чтобы мгновенно получить ваши серверы и синхронизировать устройство.",
                            fontSize = 12.sp,
                            color = VihtTextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // VK ID Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0077FF))
                                .clickable { startBrowserOAuth("vk") }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "VK",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Войти через VK ID",
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

        // TAB 4: TOKEN / SUBSCRIPTION LINK
        if (selectedTab == "token") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                VihtGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Вход по ключу или ссылке",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VihtTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Вставьте ссылку подписки или токен (например, sub:xxxx или https://anviht.ru/subscription-link/...)",
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
                                        if (clean.contains("/sub/")) {
                                            clean = clean.substringAfter("/sub/").substringBefore("?").substringBefore("/").trim()
                                        } else if (clean.contains("subscription-link/")) {
                                            clean = clean.substringAfter("subscription-link/").substringBefore("?").substringBefore("/").trim()
                                        }

                                        if (clean.startsWith("vless://")) {
                                            val hash = if (clean.contains("#")) clean.substringAfter("#") else ""
                                            val name = if (hash.isNotBlank()) {
                                                runCatching { java.net.URLDecoder.decode(hash, "UTF-8") }.getOrDefault(hash)
                                            } else "Мой VLESS сервер"
                                            val customServer = VihtServer(
                                                id = "custom_${System.currentTimeMillis()}",
                                                name = name,
                                                flag = "🌐",
                                                host = "anviht.ru",
                                                port = 443,
                                                ping = 50,
                                                vlessUri = clean
                                            )
                                            VihtPreferences.setAuthToken(context, "custom_vless")
                                            VihtPreferences.setSavedServers(context, listOf(customServer))
                                            isCheckingToken = false
                                            Toast.makeText(context, "Ключ VLESS успешно добавлен!", Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                            return@launch
                                        }

                                        val res = VihtApiClient.fetchCabinetProfile(context, token = clean)
                                        isCheckingToken = false

                                        if (res.isSuccess) {
                                            val (profile, servers) = res.getOrThrow()
                                            VihtPreferences.setAuthToken(context, clean)
                                            VihtPreferences.setSavedServers(context, servers)
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
