package com.pingwin.vpn

import org.json.JSONObject
import java.net.IDN
import java.net.URI

object VpnBypassSettings {
    val russianDomains = listOf("ru","su","xn--p1ai","yandex.net","vk.com","vk.me","userapi.com","vkuseraudio.com","vkuseraudio.net","vkuserlive.com","vk-cdn.net","mycdn.me","megafon.ru","gosuslugi.ru","goskey.ru","pochta.ru","rosatom.ru","rosseti.ru","government.ru","nalog.ru","mchs.gov.ru","mvd.ru","sberbank.ru","tinkoff.ru","vtb.ru","alfabank.ru","gazprombank.ru","raiffeisen.ru","sovcombank.ru","rshb.ru","psbank.ru","ozon.ru","wildberries.ru","samokat.ru","kuper.ru","megamarket.ru","magnit.ru","auchan.ru","metro-cc.ru","lenta.com","okey.ru","av.ru","dixy.ru","5ka.ru","perekrestok.ru","vkusvill.ru","vkusnoitochka.ru","burgerking.ru","dodopizza.ru","detmir.ru","rutube.ru","dzen.ru","mail.ru","rustore.ru","ok.ru","max.ru","1tv.ru","ntv.ru","tnt-online.ru","ctc.ru","matchtv.ru","kinopoisk.ru","okko.tv","ivi.ru","premier.one","kp.ru","rbc.ru","kommersant.ru","ria.ru","pobeda.aero","aeroflot.ru","s7.ru","rzd.ru","tutu.ru","taximaxim.com","citydrive.ru","dellin.ru","grandtrain.ru")
    fun domain(raw: String): String {
        val text = raw.trim().removePrefix("*.").removePrefix(".")
        require(text.length in 1..2048)
        val uri = URI(if (text.contains("://")) text else "https://$text")
        require(uri.scheme?.lowercase() in setOf("https","http") && uri.rawUserInfo == null) { "Введите домен или адрес сайта" }
        val host = uri.host ?: uri.rawAuthority?.takeUnless { it.contains(':') || it.contains('@') } ?: error("Некорректный адрес сайта")
        val normalized = IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase()
        require(normalized.length in 1..253 && normalized.contains('.') && normalized.split('.').all { it.length in 1..63 }) { "Некорректный домен" }
        if (normalized.matches(Regex("[0-9]+(\\.[0-9]+){3}"))) require(normalized.split('.').all { it.toIntOrNull()?.let { number -> number in 0..255 } == true }) { "Некорректный IP-адрес" }
        return normalized
    }
    fun domains(settings: JSONObject): List<String> {
        val array=(settings.optJSONObject("bypassRules")?.optJSONArray("domains") ?: settings.optJSONArray("excludedDomains")) ?: return emptyList()
        require(array.length()<=200) { "Не более 200 сайтов" }
        return (0 until array.length()).map { domain(array.getString(it)) }.distinct().sorted()
    }
    fun packages(settings: JSONObject): List<String> {
        val array=(settings.optJSONObject("bypassRules")?.optJSONArray("apps") ?: settings.optJSONArray("excludedApps")) ?: return emptyList()
        require(array.length()<=300) { "Слишком много приложений" }
        return (0 until array.length()).map { array.getString(it).also { pkg -> require(pkg.length<=255 && pkg.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) { "Некорректное приложение" } } }.distinct().sorted()
    }
}
