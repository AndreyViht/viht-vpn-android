package com.pingwin.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: AndroidVihtBridge

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        bridge.onVpnPermissionResult(result.resultCode == RESULT_OK)
    }

    fun launchVpnPermission(intent: Intent) {
        vpnPermissionLauncher.launch(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.setForeground(true)
        AutomationService.sync(this)
        UpdateScheduler.sync(this)
    }

    override fun onStop() {
        AppVisibility.setForeground(false)
        super.onStop()
        when (VpnStatus.state.value) {
            VpnConnectionState.CONNECTED -> LauncherIconManager.showGreen(this)
            else -> LauncherIconManager.showBlue(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && (data.scheme == "vihtvpn" || data.path?.startsWith("/auth") == true)) {
            val token = data.getQueryParameter("token") ?: data.getQueryParameter("sub") ?: ""
            val subToken = data.getQueryParameter("sub_token") ?: ""
            val tgId = data.getQueryParameter("tg_id") ?: data.getQueryParameter("telegram_id") ?: ""
            val email = data.getQueryParameter("email") ?: ""

            val payload = JSONObject().apply {
                put("token", if (subToken.isNotBlank()) subToken else token)
                put("originalToken", token)
                put("subToken", subToken)
                put("tgId", tgId)
                put("email", email)
                put("provider", if (tgId.isNotBlank()) "telegram" else "browser")
                put("daysLeft", 493)
            }

            webView.post {
                webView.evaluateJavascript(
                    "if (window.__onAuthSuccess) window.__onAuthSuccess($payload);",
                    null
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dark background matching Windows theme
        window.decorView.setBackgroundColor(Color.parseColor("#07090E"))
        window.statusBarColor = Color.parseColor("#07090E")
        window.navigationBarColor = Color.parseColor("#07090E")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#07090E"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 100
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("file:///android_asset/")) {
                        return false
                    }
                    // Open external links in real browser
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return true
                }
            }
        }

        bridge = AndroidVihtBridge(this, webView)
        webView.addJavascriptInterface(bridge, "VihtNative")

        setContentView(webView)

        // Load the React app bundled from Windows-vpn-dectop
        webView.loadUrl("file:///android_asset/web/index.html")

        handleDeepLink(intent)

        // Listen for VPN connection state changes and broadcast to React
        lifecycleScope.launch {
            VpnStatus.state.collectLatest { state ->
                val stateStr = when (state) {
                    VpnConnectionState.CONNECTED -> "connected"
                    VpnConnectionState.CONNECTING -> "connecting"
                    VpnConnectionState.DISCONNECTED -> "disconnected"
                    VpnConnectionState.ERROR -> "error"
                }
                val msg = if (state == VpnConnectionState.CONNECTED) "'VPN подключен'" else "null"
                val js = """
                    if (window.__onVpnStateChanged) {
                        window.__onVpnStateChanged({
                            state: '$stateStr',
                            connectedAt: ${System.currentTimeMillis()},
                            message: $msg
                        });
                    }
                """.trimIndent()
                runOnUiThread {
                    webView.evaluateJavascript(js, null)
                }
            }
        }

        // Handle Back button navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    override fun onDestroy() {
        AuthCallbackServer.stop()
        super.onDestroy()
    }
}
