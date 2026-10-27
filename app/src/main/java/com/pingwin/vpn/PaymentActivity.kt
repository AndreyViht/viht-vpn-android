package com.pingwin.vpn

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Isolated provider surface: no Viht JavaScript interface, owner token or account cookies. */
class PaymentActivity : ComponentActivity() {
    private lateinit var payment: WebView
    private var formReady = false
    private var loadEpoch = 0
    private var loadWarning: Runnable? = null
    companion object {
        fun isPaymentEntry(value: String): Boolean = YooMoneyCheckout.isEntry(value)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("paymentUrl") ?: ""
        val checkout = runCatching { YooMoneyCheckout.fromSavedUrl(url) }.getOrElse { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0c1521")) }
        val header = LinearLayout(this).apply { setPadding(20, 8, 12, 8); gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = (56 * resources.displayMetrics.density).toInt() }
        header.addView(TextView(this).apply { text = "Оплата · ЮMoney"; textSize = 18f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply { text = "Закрыть"; setOnClickListener { finish() } })
        root.addView(header)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5))
        val message = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 13f; setPadding(20, 4, 20, 4) }
        root.addView(message)
        fun probeForm(epoch: Int, remaining: Int) {
            if (formReady || isFinishing || isDestroyed || epoch != loadEpoch) return
            payment.evaluateJavascript("""
                (function(){var t=document.body?document.body.innerText:'';
                var inputs=Array.from(document.querySelectorAll('input')).filter(function(e){return e.type!=='hidden'&&e.getBoundingClientRect().width>0;}).length;
                return inputs>=3&&/номер\s+карты|card\s+number/i.test(t);})()
            """.trimIndent()) { ready ->
                if (epoch != loadEpoch || isFinishing || isDestroyed) return@evaluateJavascript
                if (ready == "true") {
                    formReady = true; message.text = ""
                    loadWarning?.let { payment.removeCallbacks(it) }
                } else if (remaining > 0) payment.postDelayed({ probeForm(epoch, remaining - 1) }, 1000)
            }
        }
        fun submitCheckout() {
            message.text = ""; progress.visibility = android.view.View.VISIBLE
            formReady = false; loadEpoch += 1
            val epoch = loadEpoch
            loadWarning?.let { payment.removeCallbacks(it) }
            payment.postUrl(checkout.endpoint, checkout.body.toByteArray(Charsets.UTF_8))
            payment.postDelayed({ probeForm(epoch, 28) }, 1000)
            loadWarning = Runnable {
                if (!isFinishing && !isDestroyed && !formReady) {
                    progress.visibility = android.view.View.GONE
                    message.text = "ЮMoney не загрузил форму оплаты. Нажмите здесь, чтобы повторить."
                }
            }.also { payment.postDelayed(it, 30000) }
        }
        message.setOnClickListener { submitCheckout() }
        payment = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                useWideViewPort = true; loadWithOverviewMode = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, value: Int) {
                    progress.progress = value
                    progress.visibility = if (value == 100) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "anviht.ru" && uri.path == "/payment/android-return") { finish(); return true }
                    if (uri.scheme == "https") return false
                    if (request.isForMainFrame) {
                        val parsed=if(uri.scheme=="intent") runCatching { Intent.parseUri(uri.toString(),Intent.URI_INTENT_SCHEME) }.getOrNull() else null
                        val target=parsed?.data ?: uri
                        val fallback=parsed?.getStringExtra("browser_fallback_url")
                        val bankPackage=parsed?.getPackage()?.takeIf { BankLinkPolicy.safePackage(it) }
                        if (BankLinkPolicy.supportedScheme(target.scheme) || (bankPackage!=null && BankLinkPolicy.safeFallback(target.toString()))) {
                            // Never forward incoming components, selectors, actions or permission flags.
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW,target).addCategory(Intent.CATEGORY_BROWSABLE).apply { if(bankPackage!=null)setPackage(bankPackage) }) }
                                .onFailure {
                                    if (BankLinkPolicy.safeFallback(fallback)) { view?.loadUrl(fallback!!); message.text="Приложение банка не найдено. Открыта веб-страница подтверждения." }
                                    else message.text="Приложение банка недоступно. Выберите другой способ оплаты."
                                }
                        } else if (BankLinkPolicy.safeFallback(fallback)) view?.loadUrl(fallback!!)
                        else message.text="Эта ссылка не поддерживается. Выберите другой способ подтверждения оплаты."
                    }
                    return true
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        progress.visibility = android.view.View.GONE
                        message.text = "Не удалось загрузить оплату. Нажмите здесь для повтора или закройте окно."
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceResponse?) {
                    if (request?.isForMainFrame == true) {
                        progress.visibility = android.view.View.GONE
                        message.text = "Платёжный сервис временно недоступен. Нажмите здесь, чтобы повторить."
                    }
                }
            }
        }
        root.addView(payment, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom)); insets
        }
        if (savedInstanceState == null || payment.restoreState(savedInstanceState) == null) submitCheckout()
    }
    override fun onSaveInstanceState(outState: Bundle) { payment.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { if (::payment.isInitialized) { loadWarning?.let { payment.removeCallbacks(it) }; payment.stopLoading(); payment.destroy() }; super.onDestroy() }
}
