package com.babasitaram.pro;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

public class MainActivity extends Activity {
    private static final String TAG = "BabaSitaRamRuntime";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (getIntent().getBooleanExtra("CI_FEATURE_TEST", false)) {
                    runCiFeatureTest(view);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(TAG, message.message());
                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void runCiFeatureTest(WebView view) {
        String script = "(function(){"
                + "try{"
                + "var out={};"
                + "switchTab('home'); out.home=!!document.querySelector('#s-home.active');"
                + "switchTab('ledger'); out.ledger=!!document.querySelector('#s-ledger.active');"
                + "goKhataTile(); out.khata=!!document.querySelector('#s-ledger.active') && preferredDetailMode==='khata';"
                + "goByaajTile(); out.byaaj=!!document.querySelector('#s-ledger.active') && preferredDetailMode==='byaaj';"
                + "switchTab('tools'); out.tools=!!document.querySelector('#s-tools.active');"
                + "switchTab('diary'); out.diary=!!document.querySelector('#s-diary.active');"
                + "switchTab('home');"
                + "setTimeout(function(){"
                + "var li=document.querySelector('#topAvatar img');"
                + "var hi=document.querySelector('#heroBaba img');"
                + "out.logo=!!(li&&li.complete&&li.naturalWidth>0&&li.src.indexOf('logo.webp')>=0);"
                + "out.hero=!!(hi&&hi.complete&&hi.naturalWidth>0&&hi.src.indexOf('logo.webp')>=0);"
                + "console.log('CI_FEATURES_RESULT:'+JSON.stringify(out));"
                + "},2500);"
                + "}catch(e){console.log('CI_FEATURES_FAIL:'+String(e&&e.stack||e));}"
                + "})();";
        view.evaluateJavascript(script, value -> Log.d(TAG, "CI_FEATURE_EVAL_DONE"));
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
