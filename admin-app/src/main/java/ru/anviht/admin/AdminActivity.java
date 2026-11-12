package ru.anviht.admin;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;

public final class AdminActivity extends Activity {
    private static final String HOST = "anviht.ru";
    private static final String PREFIX = "/__viht_admin_app__/";
    private WebView page;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        page = new WebView(this);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.setBackgroundColor(0xff0c1420);
        page.getSettings().setJavaScriptEnabled(true);
        page.getSettings().setDomStorageEnabled(true);
        page.getSettings().setAllowFileAccess(false);
        page.getSettings().setAllowContentAccess(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(page, false);
        WebViewAssetLoader.AssetsPathHandler assets = new WebViewAssetLoader.AssetsPathHandler(this);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder().setDomain(HOST)
            .addPathHandler(PREFIX, path -> assets.handle("web/" + path)).build();
        page.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }
        });
        setContentView(page);
        page.loadUrl("https://" + HOST + PREFIX + "admin.html");
    }

    @Override public void onBackPressed() {
        page.evaluateJavascript("window.dispatchEvent(new CustomEvent('viht-back',{cancelable:true}))",
            result -> { if ("true".equals(result)) finish(); });
    }
}
