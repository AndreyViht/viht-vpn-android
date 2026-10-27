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
    companion object {
        fun isPaymentEntry(value: String): Boolean = runCatching {
            val uri = Uri.parse(value)
            uri.scheme == "https" && uri.host == "yoomoney.ru" && uri.port in setOf(-1, 443) && uri.userInfo == null &&
                uri.path == "/quickpay/confirm.xml"
        }.getOrDefault(false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("paymentUrl") ?: ""
        if (!isPaymentEntry(url)) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0c1521")) }
        val header = LinearLayout(this).apply { setPadding(20, 8, 12, 8); gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = (56 * resources.displayMetrics.density).toInt() }
        header.addView(TextView(this).apply { text = "Оплата · ЮMoney"; textSize = 18f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply { text = "Закрыть"; setOnClickListener { finish() } })
        root.addView(header)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 5))
        val message = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 13f; setPadding(20, 4, 20, 4) }
        root.addView(message)
        payment = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
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
                    // Bank applications may handle SBP/3DS; never open arbitrary local or intent:// URLs.
                    if (request.isForMainFrame && uri.scheme?.matches(Regex("bank[0-9]{8,20}|sberpay|sbp|mirpay")) == true) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                            .onFailure { message.text = "Приложение банка недоступно. Выберите другой способ оплаты." }
                    }
                    return true
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        message.text = "Не удалось загрузить оплату. Нажмите здесь для повтора или закройте окно."
                        message.setOnClickListener { message.text = ""; payment.reload() }
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
        if (savedInstanceState == null) payment.loadUrl(url) else payment.restoreState(savedInstanceState)
    }
    override fun onSaveInstanceState(outState: Bundle) { payment.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { if (::payment.isInitialized) { payment.stopLoading(); payment.destroy() }; super.onDestroy() }
}
