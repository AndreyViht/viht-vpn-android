package com.pingwin.vpn

import org.json.JSONObject
import java.net.IDN
import java.net.URI

object VpnBypassSettings {
    val russianDomains = listOf("ru","su","xn--p1ai","yandex.net","vk.com","vk.me","userapi.com","vkuseraudio.com","vkuseraudio.net","vkuserlive.com","vk-cdn.net","mycdn.me")
    fun domain(raw: String): String {
        val text = raw.trim().removePrefix("*.").removePrefix(".")
        require(text.length in 1..2048)
        val uri = URI(if (text.contains("://")) text else "https://$text")
        require(uri.scheme?.lowercase() in setOf("https","http") && uri.rawUserInfo == null) { "Введите домен или адрес сайта" }
        val host = uri.host ?: uri.rawAuthority?.takeUnless { it.contains(':') || it.contains('@') } ?: error("Некорректный адрес сайта")
        val normalized = IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase()
        require(normalized.length in 1..253 && normalized.contains('.') && normalized.split('.').all { it.length in 1..63 }) { "Некорректный домен" }
        return normalized
    }
    fun domains(settings: JSONObject): List<String> {
        val array=settings.optJSONArray("excludedDomains") ?: return emptyList()
        require(array.length()<=200) { "Не более 200 сайтов" }
        return (0 until array.length()).map { domain(array.getString(it)) }.distinct()
    }
    fun packages(settings: JSONObject): List<String> {
        val array=settings.optJSONArray("excludedApps") ?: return emptyList()
        require(array.length()<=300) { "Слишком много приложений" }
        return (0 until array.length()).map { array.getString(it).also { pkg -> require(pkg.length<=255 && pkg.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) { "Некорректное приложение" } } }.distinct()
    }
}
