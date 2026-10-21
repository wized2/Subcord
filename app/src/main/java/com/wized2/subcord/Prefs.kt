package com.wized2.subcord

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("subcord", Context.MODE_PRIVATE)
    var showBubble: Boolean
        get() = sp.getBoolean("show_bubble", true)
        set(v) { sp.edit().putBoolean("show_bubble", v).apply() }
    var skipEnglish: Boolean
        get() = sp.getBoolean("skip_english", true)
        set(v) { sp.edit().putBoolean("skip_english", v).apply() }
    var targetLang: String
        get() = sp.getString("target_lang", "en") ?: "en"
        set(v) { sp.edit().putString("target_lang", v).apply() }
    var overlayEnabled: Boolean
        get() = sp.getBoolean("overlay_enabled", false)
        set(v) { sp.edit().putBoolean("overlay_enabled", v).apply() }
}
