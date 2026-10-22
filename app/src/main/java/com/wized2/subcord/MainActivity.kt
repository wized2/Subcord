package com.wized2.subcord

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wized2.subcord.service.OverlayService
import com.wized2.subcord.web.DiscordWebActivity

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var btnToggle: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        statusText = findViewById(R.id.statusText)
        btnToggle = findViewById(R.id.btnToggleService)

        findViewById<MaterialButton>(R.id.btnOpenWeb)?.setOnClickListener {
            startActivity(Intent(this, DiscordWebActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        findViewById<MaterialButton>(R.id.btnOpenDiscord).setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage("com.discord")
                ?: packageManager.getLaunchIntentForPackage("com.discord.canary")
                ?: packageManager.getLaunchIntentForPackage("com.discord.ptb")
            if (launch != null) startActivity(launch)
            else startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.discord"),
                ),
            )
        }
        findViewById<MaterialSwitch>(R.id.switchBubble).apply {
            isChecked = prefs.showBubble
            setOnCheckedChangeListener { _, c -> prefs.showBubble = c }
        }
        findViewById<MaterialSwitch>(R.id.switchSkipEnglish).apply {
            isChecked = prefs.skipEnglish
            setOnCheckedChangeListener { _, c -> prefs.skipEnglish = c }
        }
        btnToggle.setOnClickListener {
            if (OverlayService.isRunning() || prefs.overlayEnabled) {
                OverlayService.stop(this)
            } else {
                if (!hasOverlayPermission() || !isAccessibilityEnabled()) {
                    refreshStatus()
                    return@setOnClickListener
                }
                OverlayService.start(this)
            }
            refreshStatus()
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val a11y = isAccessibilityEnabled()
        val overlay = hasOverlayPermission()
        val running = OverlayService.isRunning() || prefs.overlayEnabled
        statusText.text = buildString {
            append(if (a11y) "Accessibility: enabled" else "Accessibility: not enabled")
            append("\n")
            append(if (overlay) "Overlay: allowed" else "Overlay: not allowed")
            append("\n")
            append(if (running) "Translator: running" else "Translator: stopped")
        }
        btnToggle.text = getString(if (running) R.string.stop_overlay else R.string.start_overlay)
        btnToggle.isEnabled = (a11y && overlay) || running
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${packageName}.service.DiscordTranslateService"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
