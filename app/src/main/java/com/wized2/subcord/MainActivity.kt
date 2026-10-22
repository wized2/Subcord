package com.wized2.subcord

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val prefs = Prefs(this)

        findViewById<TextView>(R.id.statusText).text =
            getString(R.string.settings_last_url, prefs.lastUrl)

        findViewById<MaterialSwitch>(R.id.switchSkipEnglish).apply {
            isChecked = prefs.skipEnglish
            setOnCheckedChangeListener { _, c -> prefs.skipEnglish = c }
        }

        findViewById<MaterialButton>(R.id.btnBackToDiscord).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnResetHome).setOnClickListener {
            prefs.lastUrl = Prefs.DEFAULT_URL
            findViewById<TextView>(R.id.statusText).text =
                getString(R.string.settings_last_url, prefs.lastUrl)
        }
    }
}
