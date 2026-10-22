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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DiscordTranslateService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = MessageScanner()
    private var engine: TranslationEngine? = null
    private var debounceJob: Job? = null
    private val translateMutex = Mutex()

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = TranslationEngine(Prefs(this).targetLang)
        instance = this
        // First pass shortly after connect if overlay already on
        scheduleScan(immediate = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in DISCORD_PACKAGES) return
        if (!Prefs(this).overlayEnabled) return
        // Debounce only — do NOT cancel in-flight HTTP work
        scheduleScan(immediate = false)
    }

    private fun scheduleScan(immediate: Boolean) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            if (!isActive) return@launch
            runScan()
        }
    }

    private suspend fun runScan() {
        if (!Prefs(this).overlayEnabled) return
        val eng = engine ?: return
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return
        val fresh = try {
            scanner.collectNewMessages(root)
        } finally {
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
        if (fresh.isEmpty()) return
        val skipEn = Prefs(this).skipEnglish
        // Serialize translations so we don't hammer the network / cancel ourselves
        translateMutex.withLock {
            for (line in fresh) {
                if (!Prefs(this).overlayEnabled) break
                val translated = try {
                    eng.translateIfNeeded(line, skipEn)
                } catch (_: Exception) {
                    null
                } ?: continue
                OverlayService.showTranslation(this@DiscordTranslateService, line, translated)
                delay(80)
            }
        }
    }

    /** Called when user starts overlay so currently visible messages are tried again. */
    fun forceRescan() {
        scanner.clearSeen()
        scheduleScan(immediate = true)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        debounceJob?.cancel()
        scope.cancel()
        engine?.close()
        engine = null
        super.onDestroy()
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
        val DISCORD_PACKAGES = setOf("com.discord", "com.discord.canary", "com.discord.ptb")

        @Volatile
        private var instance: DiscordTranslateService? = null

        fun requestRescan() {
            instance?.forceRescan()
        }
    }
}
