package com.pingwin.vpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionFailure(message: String, val unauthorized: Boolean = false) : Exception(message)

object AndroidSessionStore {
    private val mutex = Mutex()
    private val generation = AtomicLong(0)
    private const val STORE = "viht_secure_owner"
    private const val KEY = "owner_session"
    private const val ALIAS = "viht.owner.session.v1"

    @Synchronized private fun key(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Session encryption key temporarily unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun read(context: Context, storageKey: String = KEY): JSONObject? {
        val envelope = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(storageKey, null) ?: return null
        return try {
        val json = JSONObject(envelope)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, Base64.decode(json.getString("iv"), Base64.NO_WRAP)))
        JSONObject(String(cipher.doFinal(Base64.decode(json.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) {
            throw SessionFailure("Не удалось открыть сохранённый вход. Повторите попытку — данные входа сохранены.")
        }
    }
    private fun write(context: Context, session: JSONObject, storageKey: String = KEY) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(session.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        check(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(storageKey, envelope.toString()).commit()) { "Не удалось сохранить защищённую сессию" }
    }
    private fun config(context: Context) = context.assets.open("auth-config.json").bufferedReader().use { JSONObject(it.readText()) }
    private fun authRequest(url: String, method: String = "GET", headers: Map<String, String>, body: String? = null): HttpReply {
        return try { VihtHttp.request(url, method, headers + ("Connection" to "close"), body) }
        catch (_: IOException) { throw SessionFailure("Сервис входа не ответил. Проверьте интернет и повторите попытку.") }
    }
    private fun expiry(token: String): Long = runCatching {
        val payload = String(Base64.decode(token.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        JSONObject(payload).optLong("exp", 0) * 1000
    }.getOrDefault(0)
    fun hasSession(context: Context): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).contains(KEY)
    fun sessionEpoch(): Long = generation.get()
    suspend fun storedSession(context: Context): JSONObject = withContext(Dispatchers.IO) {
        // Atomic encrypted preferences read: never wait behind a network refresh's mutex.
        val epoch=generation.get()
        val session=read(context)
        if(epoch!=generation.get())throw SessionFailure("Состояние входа изменилось. Повторите запрос.")
        JSONObject().put("ok",true).put("hasSession",session!=null).put("epoch",epoch).apply {
            if(session!=null){put("token",session.getString("accessToken"));put("provider",session.optString("provider","yandex"))}
        }
    }
    suspend fun cacheUi(context: Context, value: JSONObject) = withContext(Dispatchers.IO) {
        mutex.withLock { val session=read(context) ?: return@withLock; if(value.optString("_owner")!=session.getString("ownerId")) return@withLock; write(context,JSONObject().put("owner",session.getString("ownerId")).put("savedAt",System.currentTimeMillis()).put("data",value),"ui_snapshot") }
    }
    suspend fun uiSnapshot(context: Context): JSONObject? = withContext(Dispatchers.IO) {
        // Reading the encrypted snapshot must not wait for network token refresh.
        val epoch=generation.get()
        val owner=read(context)?.optString("ownerId") ?: return@withContext null
        val cached=read(context,"ui_snapshot") ?: return@withContext null
        if(cached.optString("owner")!=owner || System.currentTimeMillis()-cached.optLong("savedAt")>604800000L || generation.get()!=epoch || read(context)?.optString("ownerId")!=owner) return@withContext null
        cached.optJSONObject("data")?.put("_owner",owner)
    }
    fun cancelPending() { generation.incrementAndGet() }
    suspend fun saveBrowserPending(context: Context, value: JSONObject?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (value == null) context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove("browser_pending").commit()
            else { write(context, value, "browser_pending"); true }
        }
    }
    suspend fun loadBrowserPending(context: Context): JSONObject? = withContext(Dispatchers.IO) { mutex.withLock { read(context, "browser_pending") } }


    suspend fun accept(context: Context, token: String, refresh: String?, provider: String): String = withContext(Dispatchers.IO) {
        require(provider == "yandex" || provider == "vk") { "Неподдерживаемый способ входа" }
        val attempt = generation.incrementAndGet()
        if (token.split('.').size != 3) throw SessionFailure("Не получена сессия владельца. Повторите вход.", true)
        val configuration = config(context)
        val response = authRequest(configuration.getString("authUrl") + "/user", headers = mapOf("apikey" to configuration.getString("anonKey"), "Authorization" to "Bearer $token"))
        if (response.status == 401 || response.status == 403) throw SessionFailure("Сессия входа недействительна. Повторите вход.", true)
        if (response.status !in 200..299) throw SessionFailure("Сервис входа временно недоступен. Повторите попытку.")
        val owner = JSONObject(response.body).getString("id")
        mutex.withLock {
            if (attempt != generation.get()) throw SessionFailure("Вход отменён", true)
            write(context, JSONObject().put("accessToken", token).put("refreshToken", refresh).put("ownerId", owner).put("provider", provider))
            VihtPreferences.clearAuth(context)
        }
        FirebasePush.sync(context)
        token
    }
    suspend fun beginScan(context: Context, purpose: String) = withContext(Dispatchers.IO) {
        mutex.withLock {require(purpose in setOf("guest","owner-pair"));write(context,JSONObject().put("purpose",purpose).put("expires",System.currentTimeMillis()+600000),"scan_context")}
    }
    suspend fun finishScan(context: Context, content: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending=read(context,"scan_context")
            context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove("scan_context").commit()
            if(content!=null&&content.length<=4096){
                write(context,JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("purpose",pending?.optString("purpose") ?: if(content.startsWith("https://anviht.ru/pair#"))"owner-pair" else "guest").put("content",content).put("expires",System.currentTimeMillis()+300000),"scan_result")
            }
        }
    }
    suspend fun pendingScan(context: Context): JSONObject? = withContext(Dispatchers.IO){mutex.withLock{read(context,"scan_result")?.takeIf{it.optLong("expires")>System.currentTimeMillis()}}}
    suspend fun acknowledgeScan(context: Context, id: String) = withContext(Dispatchers.IO){mutex.withLock{if(read(context,"scan_result")?.optString("id")==id)context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove("scan_result").commit()}}

    suspend fun guestAttempt(context: Context, invite: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if(read(context)!=null)throw SessionFailure("Сначала выйдите из текущего аккаунта")
            val old=read(context,"guest_pending")
            if(old!=null && old.optString("invite")==invite)return@withLock old.getString("token")
            if(old!=null)throw SessionFailure("Завершите предыдущее подключение или перезапустите приложение для восстановления")
            val token="vg_"+ByteArray(32).also(java.security.SecureRandom()::nextBytes).joinToString(""){"%02x".format(it)}
            write(context,JSONObject().put("invite",invite).put("token",token),"guest_pending");token
        }
    }
    suspend fun pendingGuest(context: Context): JSONObject? = withContext(Dispatchers.IO){mutex.withLock{if(read(context)==null)read(context,"guest_pending") else null}}
    suspend fun clearPendingGuest(context: Context) = withContext(Dispatchers.IO){mutex.withLock{context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove("guest_pending").commit()}}
    suspend fun acceptGuest(context: Context, token: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(Regex("vg_[a-f0-9]{64}").matches(token))
            if(read(context)!=null)throw SessionFailure("Сначала выйдите из текущего аккаунта")
            generation.incrementAndGet()
            write(context,JSONObject().put("accessToken",token).put("ownerId","guest:"+token.substring(3,19)).put("provider","guest"))
            context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove("guest_pending").commit()
            VihtPreferences.clearAuth(context);ConnectionStore.saveAll(context,emptyList())
        }
        FirebasePush.sync(context)
    }
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        runCatching { kotlinx.coroutines.withTimeout(3500) { FirebasePush.unregister(context) } }
        AppAnnouncements.cancelNotification(context)
        generation.incrementAndGet()
        mutex.withLock {
            check(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit()) { "Не удалось закрыть сессию" }
            VihtPreferences.clearAuth(context)
            ConnectionStore.saveAll(context, emptyList())
        }
    }
    suspend fun getToken(context: Context, rejectedToken: String = ""): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = read(context) ?: throw SessionFailure("Войдите в аккаунт", true)
            val access = session.getString("accessToken")
            if(access.startsWith("vg_")){if(rejectedToken.isBlank())return@withLock access;throw SessionFailure("Гостевой доступ завершён",true)}
            if ((rejectedToken.isBlank() || rejectedToken != access) && expiry(access) > System.currentTimeMillis() + 60_000) return@withLock access
            val refresh = session.optString("refreshToken").takeUnless { it.isBlank() || it == "null" }
            if (refresh == null) {
                if (rejectedToken.isBlank() && expiry(access) > System.currentTimeMillis()) return@withLock access
                throw SessionFailure("Сессия истекла. Войдите заново.", true)
            }
            val attempt = generation.get()
            val configuration = config(context)
            val reply = authRequest(configuration.getString("authUrl") + "/token?grant_type=refresh_token", "POST",
                mapOf("apikey" to configuration.getString("anonKey")), JSONObject().put("refresh_token", refresh).toString())
            if (attempt != generation.get()) throw SessionFailure("Состояние входа изменилось. Повторите запрос.")
            if (reply.status == 400 || reply.status == 401) {
                if(SessionRefreshPolicy.revoked(reply.status,reply.body)) {
                    context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit()
                    throw SessionFailure("Сессия истекла. Войдите заново.", true)
                }
                throw SessionFailure("Сервис входа временно не подтвердил сессию. Данные входа сохранены; повторите попытку.")
            }
            if (reply.status !in 200..299) throw SessionFailure("Сервис входа временно недоступен")
            val rotated = JSONObject(reply.body)
            val next = rotated.getString("access_token")
            val nextRefresh = rotated.getString("refresh_token")
            if (expiry(next) <= System.currentTimeMillis() || rotated.optJSONObject("user")?.optString("id")?.let { it != session.getString("ownerId") } == true) throw SessionFailure("Не удалось обновить сессию")
            write(context, session.put("accessToken", next).put("refreshToken", nextRefresh))
            next
        }
    }
}
