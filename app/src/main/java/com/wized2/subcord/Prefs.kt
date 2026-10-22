package com.wized2.subcord

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("subcord", Context.MODE_PRIVATE)

    var skipEnglish: Boolean
        get() = sp.getBoolean("skip_english", true)
        set(v) = sp.edit().putBoolean("skip_english", v).apply()

    var targetLang: String
        get() = sp.getString("target_lang", "en") ?: "en"
        set(v) = sp.edit().putString("target_lang", v).apply()

    /** Last Discord URL (channel/DM). Restored on next launch. */
    var lastUrl: String
        get() = sp.getString("last_url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) {
            val clean = v.trim()
            if (clean.startsWith("https://discord.com/") || clean.startsWith("https://discordapp.com/")) {
                sp.edit().putString("last_url", clean).apply()
            }
        }

    companion object {
        const val DEFAULT_URL = "https://discord.com/channels/@me"
    }
}
