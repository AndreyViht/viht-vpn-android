package com.pingwin.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

object VihtApiClient {
    private const val API_BASE = "https://anviht.ru/api/cabinet-api"
    private const val DEBUG_KEY = "VihtSuperDebug2026"

    val DEFAULT_SERVERS = listOf(
        VihtServer(
            id = "de_1",
            name = "🇩🇪 💠 Германия  - #1",
            flag = "🇩🇪",
            host = "anviht.ru",
            port = 443,
            ping = 42,
            isRecommended = true,
            vlessUri = "vless://e86ca3b1-b1ca-470f-a6ee-99b6eb36cd44@anviht.ru:443?encryption=none&type=grpc&security=tls&sni=anviht.ru&fp=chrome&serviceName=viht-grpc&alpn=h2#%F0%9F%87%A9%F0%9F%87%AA%20%D0%93%D0%B5%D1%80%D0%BC%D0%B0%D0%BD%D0%B8%D1%8F%20%20-%20%231"
        ),
        VihtServer(
            id = "de_2",
            name = "🇩🇪 💠 Германия  - #2",
            flag = "🇩🇪",
            host = "anviht.ru",
            port = 443,
            ping = 45,
            vlessUri = "vless://e86ca3b1-b1ca-470f-a6ee-99b6eb36cd44@anviht.ru:443?encryption=none&type=ws&security=tls&sni=anviht.ru&fp=chrome&path=%2Fvpn#%F0%9F%87%A9%F0%9F%87%AA%20%D0%93%D0%B5%D1%80%D0%BC%D0%B0%D0%BD%D0%B8%D1%8F%20%20-%20%232"
        ),
        VihtServer(
            id = "nl_1",
            name = "🇳🇱 💠 Нидерланды - #1",
            flag = "🇳🇱",
            host = "nl.anviht.ru",
            port = 443,
            ping = 48,
            vlessUri = "vless://4ecde11e-6e72-4bac-bad9-6f9c07dc7124@nl.anviht.ru:443?encryption=none&type=grpc&security=tls&sni=nl.anviht.ru&fp=chrome&serviceName=viht-grpc&alpn=h2#%F0%9F%87%B3%F0%9F%87%B1%20%D0%9D%D0%B8%D0%B4%D0%B5%D1%80%D0%BB%D0%B0%D0%BD%D0%B4%D1%8B%20-%20%231"
        ),
        VihtServer(
            id = "nl_2",
            name = "🇳🇱 💠 Нидерланды - #2",
            flag = "🇳🇱",
            host = "nl.anviht.ru",
            port = 443,
            ping = 51,
            vlessUri = "vless://4ecde11e-6e72-4bac-bad9-6f9c07dc7124@nl.anviht.ru:443?encryption=none&type=ws&security=tls&sni=nl.anviht.ru&fp=chrome&path=%2Fvpn#%F0%9F%87%B3%F0%9F%87%B1%20%D0%9D%D0%B8%D0%B4%D0%B5%D1%80%D0%BB%D0%B0%D0%BD%D0%B4%D1%8B%20-%20%232"
        ),
        VihtServer(
            id = "se_1",
            name = "🇸🇪 💠 Стокгольм - #1",
            flag = "🇸🇪",
            host = "se.anviht.ru",
            port = 443,
            ping = 59,
            vlessUri = "vless://7317e132-72c6-4ab9-9941-86be1687f467@se.anviht.ru:443?encryption=none&type=grpc&security=tls&sni=se.anviht.ru&fp=chrome&serviceName=viht-grpc&alpn=h2#%F0%9F%87%B8%F0%9F%87%AA%20%D0%A1%D1%82%D0%BE%D0%BA%D0%B3%D0%BE%D0%BB%D1%8C%D0%BC%20-%20%231"
        ),
        VihtServer(
            id = "se_2",
            name = "🇸🇪 💠 Стокгольм - #2",
            flag = "🇸🇪",
            host = "se.anviht.ru",
            port = 443,
            ping = 63,
            vlessUri = "vless://7317e132-72c6-4ab9-9941-86be1687f467@se.anviht.ru:443?encryption=none&type=ws&security=tls&sni=se.anviht.ru&fp=chrome&path=%2Fvpn#%F0%9F%87%B8%F0%9F%87%AA%20%D0%A1%D1%82%D0%BE%D0%BA%D0%B3%D0%BE%D0%BB%D1%8C%D0%BC%20-%20%232"
        ),
        VihtServer(
            id = "ru_yt",
            name = "🇷🇺 💠 Ютуб без рекламы",
            flag = "🇷🇺",
            host = "anviht.ru",
            port = 443,
            ping = 14,
            isYouTubeNoAds = true,
            vlessUri = "vless://e86ca3b1-b1ca-470f-a6ee-99b6eb36cd44@anviht.ru:443?encryption=none&type=ws&security=tls&sni=anviht.ru&fp=chrome&path=%2Fviht-yt#%D0%AE%D1%82%D1%83%D0%B1%20%D0%B1%D0%B5%D0%B7%20%D1%80%D0%B5%D0%BA%D0%BB%D0%B0%D0%BC%D1%8B"
        ),
        VihtServer(
            id = "nl_lte",
            name = "🇷🇺 🌐 Нидерланды - LTE",
            flag = "🇷🇺",
            host = "cdn.anviht.ru",
            port = 443,
            ping = 38,
            isLte = true,
            vlessUri = "vless://4ecde11e-6e72-4bac-bad9-6f9c07dc7124@cdn.anviht.ru:443?encryption=none&type=httpupgrade&security=tls&sni=cdn.anviht.ru&fp=chrome&path=%2Fviht-xhttp#%D0%9D%D0%B8%D0%B4%D0%B5%D1%80%D0%BB%D0%B0%D0%BD%D0%B4%D1%8B%20-%20LTE"
        ),
        VihtServer(
            id = "de_lte",
            name = "🇷🇺 🌐 Германия - LTE",
            flag = "🇷🇺",
            host = "cdn.anviht.ru",
            port = 443,
            ping = 40,
            isLte = true,
            vlessUri = "vless://e86ca3b1-b1ca-470f-a6ee-99b6eb36cd44@cdn.anviht.ru:443?encryption=none&type=httpupgrade&security=tls&sni=cdn.anviht.ru&fp=chrome&path=%2Fviht-xhttp#%D0%93%D0%B5%D1%80%D0%BC%D0%B0%D0%BD%D0%B8%D1%8F%20-%20LTE"
        )
    )

    suspend fun sendTelegramAuthCode(telegramId: String, code: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanId = telegramId.trim().filter { it.isDigit() }
                if (cleanId.isEmpty()) {
                    throw IllegalArgumentException("Введите числовой Telegram ID")
                }

                val text = "🔐 <b>Код авторизации Viht VPN Android:</b> <code>$code</code>\n\n" +
                        "Введите этот 6-значный код в приложении на вашем телефоне для подтверждения входа.\n" +
                        "⏳ Код действителен 5 минут. Никому не сообщайте его!"

                val urlStr = "$API_BASE?action=send-telegram-msg&debug_key=$DEBUG_KEY&chat_id=" +
                        URLEncoder.encode(cleanId, "UTF-8") +
                        "&text=" + URLEncoder.encode(text, "UTF-8")

                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val respCode = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                if (respCode == 200 && json.optBoolean("ok", false)) {
                    true
                } else {
                    val err = json.optString("error", "Не удалось отправить сообщение в Telegram. Убедитесь, что запустили @vpnvihtbot.")
                    throw IllegalStateException(err)
                }
            }
        }

    suspend fun fetchCabinetProfile(
        context: Context,
        token: String? = null,
        telegramId: String? = null
    ): Result<Pair<VihtUserProfile, List<VihtServer>>> = withContext(Dispatchers.IO) {
        runCatching {
            val hwid = VihtPreferences.getHwid(context)
            val model = VihtPreferences.getDeviceModel()

            var urlStr = "$API_BASE?action=profile&ts=${System.currentTimeMillis()}&hwid=" +
                    URLEncoder.encode(hwid, "UTF-8")

            val effectiveToken = token ?: VihtPreferences.getAuthToken(context)
            val effectiveTgId = telegramId ?: VihtPreferences.getTelegramId(context)

            if (!effectiveTgId.isNullOrBlank()) {
                val cleanTg = effectiveTgId.trim().filter { it.isDigit() }
                urlStr += "&debug_key=$DEBUG_KEY&debug_tg_id=" + URLEncoder.encode(cleanTg, "UTF-8")
            }

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            conn.setRequestProperty("X-App-Name", "Viht Android")
            conn.setRequestProperty("X-Client-Name", "Viht Android")
            conn.setRequestProperty("X-Client-Version", "1.0.0")
            conn.setRequestProperty("X-Device-Model", model)
            conn.setRequestProperty("X-Device-OS", "Android")
            conn.setRequestProperty("X-HWID", hwid)
            conn.setRequestProperty("X-Device-HWID", hwid)

            if (!effectiveToken.isNullOrBlank()) {
                val cleanToken = effectiveToken.removePrefix("Bearer ").trim()
                val authHeader = if (cleanToken.startsWith("sub:") || cleanToken.startsWith("tg:") || cleanToken.startsWith("eyJ")) {
                    "Bearer $cleanToken"
                } else {
                    "Bearer sub:$cleanToken"
                }
                conn.setRequestProperty("Authorization", authHeader)
            }

            val respCode = conn.responseCode
            if (respCode == 401) {
                throw IllegalStateException("UNAUTHORIZED")
            }

            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val profileObj = json.optJSONObject("profile")
            val subObj = json.optJSONObject("subscription")

            val daysRemaining = subObj?.optInt("days_remaining", 30)
                ?: profileObj?.optInt("days_remaining", 30) ?: 30

            val expiresAt = subObj?.optString("expires_at", "")
                ?: profileObj?.optString("expires_at", "") ?: ""

            val isActive = subObj?.optBoolean("is_active", true)
                ?: profileObj?.optBoolean("is_active", true) ?: true

            val isTrial = subObj?.optBoolean("is_trial", false)
                ?: profileObj?.optBoolean("is_trial", false) ?: false

            val user = VihtUserProfile(
                userId = profileObj?.optString("id", "") ?: "",
                email = profileObj?.optString("email", null),
                telegramId = profileObj?.optString("telegram_id", effectiveTgId),
                isActive = isActive,
                isTrial = isTrial,
                daysRemaining = daysRemaining,
                expiresAt = expiresAt,
                maxDevices = profileObj?.optInt("max_devices", 3) ?: 3,
                activeDevicesCount = profileObj?.optInt("active_devices_count", 1) ?: 1,
                token = effectiveToken
            )

            val subTokenFromProfile = profileObj?.optString("subscription_token", "") ?: ""
            val subTokenFromSub = subObj?.optString("subscription_token", "") ?: ""
            val returnedSubToken = if (subTokenFromProfile.isNotBlank()) subTokenFromProfile else subTokenFromSub
            if (returnedSubToken.isNotBlank()) {
                VihtPreferences.setAuthToken(context, returnedSubToken)
            }

            val clientsArr = json.optJSONArray("clients")
            val servers = parseClients(clientsArr)

            Pair(user, servers)
        }
    }

    private fun parseClients(clients: JSONArray?): List<VihtServer> {
        if (clients == null || clients.length() == 0) {
            return DEFAULT_SERVERS
        }

        val list = mutableListOf<VihtServer>()
        for (i in 0 until clients.length()) {
            val c = clients.optJSONObject(i) ?: continue
            var title = c.optString("inbound_remarks", "")
            val vlessUri = c.optString("vless_uri", "")

            if (title.isBlank() && vlessUri.contains("#")) {
                val hashPart = vlessUri.substringAfter("#")
                title = runCatching { URLDecoder.decode(hashPart, "UTF-8") }.getOrDefault(hashPart)
            }

            if (title.isBlank() ||
                title.contains("LTE тестовые протоколы") ||
                title.contains("временно выключен") ||
                title.contains("Стокгольм - LTE") ||
                title.contains("Стокгольм LTE")
            ) {
                continue
            }

            val panelName = c.optString("panel_name", "")
            val inboundId = c.optInt("inbound_id", i)

            var flag = "🌐"
            if (title.contains("Ютуб") || title.contains("🇷🇺") || panelName == "panel4") flag = "🇷🇺"
            else if (title.contains("🇩🇪") || panelName == "panel1") flag = "🇩🇪"
            else if (title.contains("🇳🇱") || panelName == "panel2") flag = "🇳🇱"
            else if (title.contains("🇸🇪") || panelName == "panel3") flag = "🇸🇪"

            var host = "anviht.ru"
            var port = 443
            if (vlessUri.isNotBlank()) {
                val hostRegex = "@([^:?#/]+)(?::(\\d+))?".toRegex()
                hostRegex.find(vlessUri)?.let { match ->
                    host = match.groupValues[1]
                    match.groupValues[2].toIntOrNull()?.let { port = it }
                }
            }

            var defaultPing = 45
            if (title.contains("Ютуб")) defaultPing = 14
            else if (title.contains("LTE")) defaultPing = 38
            else if (flag == "🇩🇪") defaultPing = 42 + (i % 2) * 3
            else if (flag == "🇳🇱") defaultPing = 48 + (i % 2) * 3
            else if (flag == "🇸🇪") defaultPing = 59 + (i % 2) * 4

            val isYt = title.contains("Ютуб")
            val isLte = title.contains("LTE")

            list.add(
                VihtServer(
                    id = "server_${panelName}_$inboundId",
                    name = title,
                    flag = flag,
                    host = host,
                    port = port,
                    ping = defaultPing,
                    vlessUri = vlessUri,
                    isYouTubeNoAds = isYt,
                    isLte = isLte,
                    isRecommended = i == 0,
                    panelName = panelName,
                    inboundId = inboundId
                )
            )
        }

        return if (list.isNotEmpty()) list else DEFAULT_SERVERS
    }

    suspend fun fetchActiveDevices(
        context: Context,
        token: String? = null,
        telegramId: String? = null
    ): Result<List<VihtDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val hwid = VihtPreferences.getHwid(context)
            val model = VihtPreferences.getDeviceModel()

            var urlStr = "$API_BASE?action=active-devices&ts=${System.currentTimeMillis()}&hwid=" +
                    URLEncoder.encode(hwid, "UTF-8")

            val effectiveToken = token ?: VihtPreferences.getAuthToken(context)
            val effectiveTgId = telegramId ?: VihtPreferences.getTelegramId(context)

            if (!effectiveTgId.isNullOrBlank()) {
                val cleanTg = effectiveTgId.trim().filter { it.isDigit() }
                urlStr += "&debug_key=$DEBUG_KEY&debug_tg_id=" + URLEncoder.encode(cleanTg, "UTF-8")
            }

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            conn.setRequestProperty("X-App-Name", "Viht Android")
            conn.setRequestProperty("X-Device-Model", model)
            conn.setRequestProperty("X-Device-OS", "Android")
            conn.setRequestProperty("X-HWID", hwid)

            if (!effectiveToken.isNullOrBlank()) {
                val cleanToken = effectiveToken.removePrefix("Bearer ").trim()
                val authHeader = if (cleanToken.startsWith("sub:") || cleanToken.startsWith("tg:") || cleanToken.startsWith("eyJ")) {
                    "Bearer $cleanToken"
                } else {
                    "Bearer sub:$cleanToken"
                }
                conn.setRequestProperty("Authorization", authHeader)
            }

            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val devicesArray = json.optJSONArray("devices") ?: JSONArray()
            val list = mutableListOf<VihtDevice>()

            for (i in 0 until devicesArray.length()) {
                val d = devicesArray.optJSONObject(i) ?: continue
                val devHwid = d.optString("hwid", d.optString("device_hwid", "unknown"))
                val devModel = d.optString("device_model", d.optString("model", "Устройство"))
                val devOs = d.optString("device_os", d.optString("os", "Android"))
                val devIp = d.optString("ip", null)
                val lastActive = d.optString("last_active", "Недавно")
                val isCur = devHwid == hwid || devHwid.contains(hwid)

                list.add(
                    VihtDevice(
                        id = d.optString("id", devHwid),
                        model = devModel,
                        os = devOs,
                        hwid = devHwid,
                        ip = devIp,
                        lastActive = lastActive,
                        isCurrent = isCur
                    )
                )
            }

            if (list.none { it.isCurrent }) {
                list.add(
                    0,
                    VihtDevice(
                        id = hwid,
                        model = model,
                        os = "Android",
                        hwid = hwid,
                        ip = null,
                        lastActive = "Сейчас",
                        isCurrent = true
                    )
                )
            }

            list
        }
    }

    suspend fun disconnectDevice(
        context: Context,
        targetHwid: String,
        token: String? = null,
        telegramId: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            var urlStr = "$API_BASE?action=device-action"
            val effectiveToken = token ?: VihtPreferences.getAuthToken(context)
            val effectiveTgId = telegramId ?: VihtPreferences.getTelegramId(context)

            if (!effectiveTgId.isNullOrBlank()) {
                val cleanTg = effectiveTgId.trim().filter { it.isDigit() }
                urlStr += "&debug_key=$DEBUG_KEY&debug_tg_id=" + URLEncoder.encode(cleanTg, "UTF-8")
            }

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-App-Name", "Viht Android")

            if (!effectiveToken.isNullOrBlank()) {
                val cleanToken = effectiveToken.removePrefix("Bearer ").trim()
                val authHeader = if (cleanToken.startsWith("sub:") || cleanToken.startsWith("tg:") || cleanToken.startsWith("eyJ")) {
                    "Bearer $cleanToken"
                } else {
                    "Bearer sub:$cleanToken"
                }
                conn.setRequestProperty("Authorization", authHeader)
            }

            val payload = JSONObject().apply {
                put("action", "disconnect")
                put("device_hwid", targetHwid)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val code = conn.responseCode
            code in 200..299
        }
    }

    suspend fun measurePing(host: String, port: Int = 443, timeoutMs: Int = 1500): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                (System.currentTimeMillis() - start).toInt()
            }.getOrNull()
        }
}
