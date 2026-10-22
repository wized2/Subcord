package com.wized2.subcord.web

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.wized2.subcord.Prefs
import com.wized2.subcord.translate.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TranslateBridge(
    private val webView: WebView,
    private val prefs: Prefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = TranslationEngine(prefs.targetLang)

    @JavascriptInterface
    fun translate(id: String, text: String) {
        if (id.isBlank() || text.isBlank()) return
        scope.launch {
            val out = try {
                engine.translateIfNeeded(text, prefs.skipEnglish)
            } catch (_: Exception) {
                null
            }
            if (out.isNullOrBlank()) return@launch
            val safeId = JSONObject.quote(id)
            val safeText = JSONObject.quote(out)
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "try{window.__subcordOnResult($safeId,$safeText);}catch(e){}",
                    null,
                )
            }
        }
    }

    fun close() {
        engine.close()
    }
}
