package com.pingwin.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var bridge: AndroidVihtBridge
    @Volatile var isTrustedPage: Boolean = false
        private set
    private var inviteScan: kotlinx.coroutines.CompletableDeferred<String?>? = null
    private val inviteScanner = registerForActivityResult(com.journeyapps.barcodescanner.ScanContract()) { result -> inviteScan?.complete(result.contents);inviteScan=null }
    suspend fun scanInvite(): org.json.JSONObject {
        val pending=kotlinx.coroutines.CompletableDeferred<String?>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){
            check(inviteScan==null){"Сканер уже открыт"};inviteScan=pending
            inviteScanner.launch(com.journeyapps.barcodescanner.ScanOptions().setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE).setPrompt("Отсканируйте приглашение Viht VPN").setBeepEnabled(false).setOrientationLocked(false))
        }
        val content=pending.await()?:return org.json.JSONObject().put("ok",false).put("cancelled",true)
        return org.json.JSONObject().put("ok",true).put("content",content)
    }
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (::bridge.isInitialized) bridge.onVpnPermissionResult(result.resultCode == RESULT_OK)
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun launchVpnPermission(intent: Intent) { vpnPermission.launch(intent) }
    fun ensureNotificationPermission() {
        runOnUiThread {
            val prefs=getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE)
            if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notificationPermissionAsked",false) && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                { prefs.edit().putBoolean("notificationPermissionAsked",true).apply(); notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }
    override fun onStart() { super.onStart(); AppVisibility.setForeground(true); UpdateScheduler.sync(this); AppAnnouncements.schedule(this) }
    override fun onResume() { super.onResume(); if (::bridge.isInitialized && isTrustedPage) bridge.pageReady() }
    override fun onStop() { AppVisibility.setForeground(false); super.onStop() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if (::bridge.isInitialized) bridge.completeBrowserAuth(intent.data); if (::bridge.isInitialized && isTrustedPage) bridge.pageReady() }
    suspend fun addQuickTile(): org.json.JSONObject {
        if (Build.VERSION.SDK_INT < 33) {
            val message="Откройте шторку → Изменить плитки → перетащите Viht VPN"
            android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show()
            return org.json.JSONObject().put("ok",true).put("manual",true).put("message",message)
        }
        val result=kotlinx.coroutines.CompletableDeferred<Int>()
        getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
            android.content.ComponentName(this,VihtTileService::class.java),"Viht VPN",
            android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_viht_tile),mainExecutor) { code -> result.complete(code) }
        val code=kotlinx.coroutines.withTimeoutOrNull(20000) { result.await() }
        val added=code==1 || code==2
        if(added) getSharedPreferences("viht_ui_settings",Context.MODE_PRIVATE).edit().putBoolean("tileAdded",true).apply()
        val message=when(code) { 1 -> "Плитка уже добавлена в шторку";2 -> "Плитка добавлена";else -> "Android не добавил плитку. Откройте шторку → Изменить плитки → Viht VPN." }
        return org.json.JSONObject().put("ok",added).put("added",added).put("message",message).put("error",if(added) org.json.JSONObject.NULL else message)
    }
    fun consumeTileConnect(): Boolean = intent?.getBooleanExtra("viht_tile_connect", false)?.also { intent.removeExtra("viht_tile_connect") } ?: false
    fun consumeOpenAnnouncement(): String? = intent?.getStringExtra(AppAnnouncements.EXTRA_OPEN)?.also { intent.removeExtra(AppAnnouncements.EXTRA_OPEN) }
    fun consumeOpenUpdates(): Boolean = intent?.getBooleanExtra(UpdateCheckWorker.EXTRA_OPEN_UPDATES, false)?.also { intent.removeExtra(UpdateCheckWorker.EXTRA_OPEN_UPDATES) } ?: false
    private fun local(url: String): Boolean = runCatching {
        val uri = Uri.parse(url)
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.path?.startsWith("/assets/web/") == true
    }.getOrDefault(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val provider = WebView.getCurrentWebViewPackage()
        if ((provider?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0) < 103) {
            setContentView(TextView(this).apply {
                text = "Для Viht VPN обновите Android System WebView или Chrome, затем откройте приложение снова."
                setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#0a111d")); textSize = 18f
                setPadding(30, 80, 30, 30)
                setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + (provider?.packageName ?: "com.google.android.webview")))) } }
            })
            return
        }
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0a111d"))
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true; textZoom = 100
                useWideViewPort = true; loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = false
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (local(request.url.toString())) return false
                    if (request.isForMainFrame && request.url.scheme in setOf("http", "https")) runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { isTrustedPage = url?.let(::local) == true }
                override fun onPageFinished(view: WebView?, url: String?) {
                    isTrustedPage = url?.let(::local) == true
                    if (isTrustedPage && ::bridge.isInitialized) bridge.pageReady()
                }
            }
        }
        bridge = AndroidVihtBridge(this, webView)
        bridge.completeBrowserAuth(intent?.data)
        webView.addJavascriptInterface(bridge, "VihtNative")
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0a111d"))
            addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { var initial=true; VpnStatus.state.collect { bridge.emit("vpn-state", VpnStatus.snapshot().put("snapshot",initial)); initial=false } }
                launch { VpnStatus.traffic.collect { bridge.emit("vpn-traffic", it) } }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isTrustedPage) { moveTaskToBack(true); return }
                webView.evaluateJavascript("""
                    (function(){var d=document.querySelector('dialog[open]');if(d){d.close();return true;}
                    var overlays=document.querySelectorAll('.billing-shade,.fluent-modal-overlay');
                    var e=new CustomEvent('viht-back',{cancelable:true,detail:{modal:overlays.length>0}});
                    if(!window.dispatchEvent(e))return true;
                    if(overlays.length){var o=overlays[overlays.length-1];var close=o.querySelector('.fluent-modal-close,.billing-close');(close||o).click();return true;}
                    return false;})()
                """.trimIndent()) { handled -> if(handled!="true"&&!isFinishing)moveTaskToBack(true) }
            }
        })
    }
    override fun onDestroy() {
        isTrustedPage = false
        if (::bridge.isInitialized) bridge.close()
        if (::webView.isInitialized) { webView.removeJavascriptInterface("VihtNative"); webView.destroy() }
        if (isFinishing) AuthCallbackServer.stop()
        super.onDestroy()
    }
}
