package com.pingwin.vpn

import org.junit.Assert.*
import org.junit.Test

class YooMoneyCheckoutTest {
    private val saved = "https://yoomoney.ru/quickpay/confirm.xml?receiver=4100119557878170&quickpay-form=shop&sum=59&label=pay_123_7_reference&paymentTypeChoice=on"
    @Test fun convertsExistingOrderToDocumentedPostWithoutChangingRecipientAmountOrReference() {
        val request = YooMoneyCheckout.fromSavedUrl(saved)
        assertEquals("https://yoomoney.ru/quickpay/confirm",request.endpoint)
        assertEquals("59",request.amount)
        assertTrue(request.body.contains("receiver=4100119557878170"))
        assertTrue(request.body.contains("sum=59"))
        assertTrue(request.body.contains("label=pay_123_7_reference"))
        assertTrue(request.body.contains("quickpay-form=button"))
        assertTrue(request.body.contains("paymentType=AC"))
        assertFalse(request.body.contains("paymentTypeChoice"))
        assertFalse(request.body.contains("quickpay-form=shop"))
    }
    @Test fun acceptsSavedSlotOrdersAndWalletSelection() {
        val request=YooMoneyCheckout.fromSavedUrl(saved.replace("pay_123_7_reference","slot_123_2_reference").replace("sum=59","sum=600")+"&paymentType=PC")
        assertTrue(request.body.contains("label=slot_123_2_reference"))
        assertTrue(request.body.contains("sum=600"));assertTrue(request.body.contains("paymentType=PC"))
    }
    @Test fun rejectsUntrustedHostsMissingParametersAndAmbiguousAmounts() {
        for(value in listOf(saved.replace("yoomoney.ru","evil.example"),saved.replace("https:","http:"),
            "https://yoomoney.ru/quickpay/confirm.xml",saved+"&sum=1",saved.replace("sum=59","sum=-1"),
            saved.replace("receiver=4100119557878170","receiver=bad"))) {
            assertTrue(runCatching { YooMoneyCheckout.fromSavedUrl(value) }.isFailure)
        }
    }
}
