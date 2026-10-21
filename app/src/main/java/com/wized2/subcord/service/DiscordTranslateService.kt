package com.wized2.subcord.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.wized2.subcord.Prefs
import com.wized2.subcord.translate.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DiscordTranslateService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = MessageScanner()
    private var engine: TranslationEngine? = null
    private var lastScanJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = TranslationEngine(Prefs(this).targetLang)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in DISCORD_PACKAGES) return
        if (!Prefs(this).overlayEnabled) return
        lastScanJob?.cancel()
        lastScanJob = scope.launch {
            val root = rootInActiveWindow ?: return@launch
            try {
                val fresh = scanner.collectNewMessages(root)
                val eng = engine ?: return@launch
                val skipEn = Prefs(this@DiscordTranslateService).skipEnglish
                for (line in fresh) {
                    val translated = eng.translateIfNeeded(line, skipEn) ?: continue
                    OverlayService.showTranslation(this@DiscordTranslateService, line, translated)
                }
            } finally {
                root.recycle()
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        scope.cancel()
        engine?.close()
        engine = null
        super.onDestroy()
    }

    companion object {
        val DISCORD_PACKAGES = setOf("com.discord", "com.discord.canary", "com.discord.ptb")
    }
}
