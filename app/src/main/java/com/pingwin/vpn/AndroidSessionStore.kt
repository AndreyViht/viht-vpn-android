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

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private fun read(context: Context, storageKey: String = KEY): JSONObject? = runCatching {
        val envelope = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(storageKey, null) ?: return@runCatching null
        val json = JSONObject(envelope)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(json.getString("iv"), Base64.NO_WRAP)))
        JSONObject(String(cipher.doFinal(Base64.decode(json.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
    }.getOrNull()
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
    fun hasSession(context: Context): Boolean = read(context) != null
    suspend fun cacheUi(context: Context, value: JSONObject) = withContext(Dispatchers.IO) {
        mutex.withLock { val session=read(context) ?: return@withLock; write(context,JSONObject().put("owner",session.getString("ownerId")).put("savedAt",System.currentTimeMillis()).put("data",value),"ui_snapshot") }
    }
    suspend fun uiSnapshot(context: Context): JSONObject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner=read(context)?.optString("ownerId") ?: return@withLock null
            val cached=read(context,"ui_snapshot") ?: return@withLock null
            if(cached.optString("owner")!=owner || System.currentTimeMillis()-cached.optLong("savedAt")>604800000L) return@withLock null
            cached.optJSONObject("data")
        }
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
        token
    }
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
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
            if (attempt != generation.get()) throw SessionFailure("Сессия закрыта", true)
            if (reply.status == 400 || reply.status == 401) {
                context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().commit()
                throw SessionFailure("Сессия истекла. Войдите заново.", true)
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
