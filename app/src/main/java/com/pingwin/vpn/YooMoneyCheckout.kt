package com.pingwin.vpn

import java.math.BigDecimal
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Saved checkout URLs are data envelopes, never GET requests to the payment provider. */
data class YooMoneyCheckout(val endpoint: String, val body: String, val amount: String) {
    companion object {
        const val ENDPOINT = "https://yoomoney.ru/quickpay/confirm"
        fun isEntry(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "yoomoney.ru" && uri.port in setOf(-1,443) && uri.userInfo == null &&
                uri.path in setOf("/quickpay/confirm.xml", "/quickpay/confirm")
        }.getOrDefault(false)

        fun fromSavedUrl(value: String): YooMoneyCheckout {
            require(isEntry(value)) { "Недопустимая платёжная ссылка" }
            val uri = URI(value)
            val fields = linkedMapOf<String,String>()
            for (part in (uri.rawQuery ?: "").split('&').filter(String::isNotEmpty)) {
                val pair = part.split('=', limit=2)
                val name = URLDecoder.decode(pair[0], "UTF-8")
                require(!fields.containsKey(name)) { "Параметры оплаты повторяются" }
                fields[name] = URLDecoder.decode(pair.getOrElse(1){""}, "UTF-8")
            }
            val receiver = fields["receiver"] ?: ""
            val amount = fields["sum"] ?: ""
            val label = fields["label"] ?: ""
            require(receiver.matches(Regex("[0-9]{10,20}"))) { "Не указан получатель оплаты" }
            require(amount.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,2})?")) &&
                BigDecimal(amount)>BigDecimal.ZERO && BigDecimal(amount)<=BigDecimal("1000000")) { "Некорректная сумма оплаты" }
            require(label.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Не указан номер заказа" }
            val method = fields["paymentType"]?.takeIf { it in setOf("AC","PC") } ?: "AC"
            val post = linkedMapOf("receiver" to receiver, "quickpay-form" to "button", "paymentType" to method,
                "sum" to amount, "label" to label, "successURL" to "https://anviht.ru/payment/android-return")
            val body = post.entries.joinToString("&") { (name,value) ->
                URLEncoder.encode(name,"UTF-8") + "=" + URLEncoder.encode(value,"UTF-8")
            }
            return YooMoneyCheckout(ENDPOINT,body,amount)
        }
    }
}
