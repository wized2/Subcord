package com.wized2.subcord.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.wized2.subcord.MainActivity
import com.wized2.subcord.Prefs
import com.wized2.subcord.R
import java.io.BufferedReader
import java.io.InputStreamReader

class DiscordWebActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var prefs: Prefs
    private var bridge: TranslateBridge? = null
    private var injectJs: String = ""
    private var lastSavedUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discord_web)
        prefs = Prefs(this)
        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.statusChip)
        injectJs = loadAsset("subcord_inject.js")

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btnInject).setOnClickListener {
            injectScript(force = true)
        }

        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val startUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") }
            ?: prefs.lastUrl
        webView.loadUrl(startUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        bridge = TranslateBridge(webView, prefs)
        webView.addJavascriptInterface(bridge!!, "SubcordBridge")

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            // Mobile Chrome — Discord serves the mobile chat UI (not desktop 3-column)
            userAgentString = MOBILE_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            allowFileAccess = false
            allowContentAccess = true
            // Prefer mobile layout
            setGeolocationEnabled(false)
            // Slightly lower memory pressure
            offscreenPreRaster = true
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // Tell pages we are a narrow phone
        webView.evaluateJavascript(
            "document.querySelector('meta[name=viewport]')||null",
            null,
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.isVisible = newProgress in 1..99
                progress.progress = newProgress
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString().orEmpty()
                if (u.startsWith("https://discord.com/") || u.startsWith("https://discordapp.com/") ||
                    u.contains("recaptcha") || u.contains("hcaptcha") || u.contains("cloudflare") ||
                    u.contains("google.com") || u.contains("gstatic.com")
                ) {
                    return false
                }
                // Block leaving Discord to random schemes
                return !u.startsWith("http")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                status.text = getString(R.string.web_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                status.text = getString(R.string.web_ready)
                url?.let { saveUrlIfChannel(it) }
                ensureMobileViewport(view)
                val skip = if (prefs.skipEnglish) "true" else "false"
                view?.evaluateJavascript("window.__SUBCORD_SKIP_EN__=$skip;", null)
                injectScript(force = true)
                // SPA mount — light re-inject, not spammy
                view?.postDelayed({ injectScript(force = false) }, 1500)
                CookieManager.getInstance().flush()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let { saveUrlIfChannel(it) }
            }
        }
    }

    private fun ensureMobileViewport(view: WebView?) {
        view?.evaluateJavascript(
            """
            (function(){
              var m=document.querySelector('meta[name=viewport]');
              if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
              m.content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover';
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun saveUrlIfChannel(url: String) {
        if (url == lastSavedUrl) return
        // Persist login home + channel/DM deep links
        if (url.startsWith("https://discord.com/channels/") ||
            url.startsWith("https://discord.com/login") ||
            url.startsWith("https://discord.com/app") ||
            url.startsWith("https://discord.com/channels/@me")
        ) {
            lastSavedUrl = url
            prefs.lastUrl = url
        }
    }

    private fun injectScript(force: Boolean) {
        if (injectJs.isBlank()) return
        val skip = if (prefs.skipEnglish) "true" else "false"
        // Avoid double-wrapping issues: set flag then run asset IIFE
        webView.evaluateJavascript("window.__SUBCORD_SKIP_EN__=$skip;", null)
        webView.evaluateJavascript(injectJs, null)
        if (force) status.text = getString(R.string.web_injected)
    }

    private fun loadAsset(name: String): String = try {
        assets.open(name).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    } catch (_: Exception) {
        ""
    }

    override fun onPause() {
        webView.url?.let { saveUrlIfChannel(it) }
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        injectScript(force = false)
    }

    override fun onDestroy() {
        bridge?.close()
        try {
            webView.removeJavascriptInterface("SubcordBridge")
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        // Real Android Chrome mobile UA so Discord serves mobile UI
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
