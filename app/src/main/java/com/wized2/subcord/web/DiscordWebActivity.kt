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
            status.text = getString(R.string.web_injected)
        }

        setupWebView()
        webView.loadUrl(DISCORD_LOGIN)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        bridge = TranslateBridge(webView, prefs)
        webView.addJavascriptInterface(bridge!!, "SubcordBridge")

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = true
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.isVisible = newProgress < 100
                progress.progress = newProgress
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Keep Discord + captcha/auth flows inside the WebView
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                status.text = getString(R.string.web_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                status.text = getString(R.string.web_ready)
                val skip = if (prefs.skipEnglish) "true" else "false"
                view?.evaluateJavascript("window.__SUBCORD_SKIP_EN__=$skip;", null)
                injectScript(force = true)
                view?.postDelayed({ injectScript(force = false) }, 1200)
                view?.postDelayed({ injectScript(force = false) }, 3000)
                CookieManager.getInstance().flush()
            }
        }
    }

    private fun injectScript(force: Boolean) {
        if (injectJs.isBlank()) return
        val wrapped = "(function(){try{window.__SUBCORD_SKIP_EN__=${if (prefs.skipEnglish) "true" else "false"};$injectJs}catch(e){}} )();"
        webView.evaluateJavascript(wrapped, null)
        if (force) status.text = getString(R.string.web_injected)
    }

    private fun loadAsset(name: String): String = try {
        assets.open(name).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    } catch (_: Exception) {
        ""
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        injectScript(force = false)
    }

    override fun onDestroy() {
        bridge?.close()
        webView.removeJavascriptInterface("SubcordBridge")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val DISCORD_LOGIN = "https://discord.com/login"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}
