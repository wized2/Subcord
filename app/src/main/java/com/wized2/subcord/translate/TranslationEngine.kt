package com.wized2.subcord.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Lightweight network translator (no native ML Kit libs).
 * Uses the public Google Translate endpoint with auto language detect.
 * Results are cached in memory.
 */
class TranslationEngine(
    private val targetLang: String = "en",
) {
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 400
    }

    suspend fun translateIfNeeded(text: String, skipIfEnglish: Boolean): String? =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.length < 2) return@withContext null
            synchronized(cache) { cache[trimmed]?.let { return@withContext it } }

            if (skipIfEnglish && looksPrimarilyEnglish(trimmed)) {
                return@withContext null
            }

            val translated = translateHttp(trimmed, targetLang) ?: return@withContext null
            if (translated.equals(trimmed, ignoreCase = true)) return@withContext null
            synchronized(cache) { cache[trimmed] = translated }
            translated
        }

    fun close() {
        synchronized(cache) { cache.clear() }
    }

    companion object {
        /** Rough heuristic — avoids network when the line is already English-looking. */
        fun looksPrimarilyEnglish(s: String): Boolean {
            val letters = s.filter { it.isLetter() }
            if (letters.length < 3) return false
            val latin = letters.count {
                val c = it.lowercaseChar()
                c in 'a'..'z'
            }
            // Mostly Latin letters and no heavy non-Latin script
            val nonLatin = letters.count {
                val block = Character.UnicodeBlock.of(it)
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.HIRAGANA ||
                    block == Character.UnicodeBlock.KATAKANA ||
                    block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                    block == Character.UnicodeBlock.ARABIC ||
                    block == Character.UnicodeBlock.CYRILLIC ||
                    block == Character.UnicodeBlock.DEVANAGARI ||
                    block == Character.UnicodeBlock.THAI
            }
            if (nonLatin > 0) return false
            return latin.toFloat() / letters.size >= 0.85f
        }

        private fun translateHttp(text: String, target: String): String? {
            val q = URLEncoder.encode(text.take(900), StandardCharsets.UTF_8.name())
            val url = URL(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=$target&dt=t&q=$q",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Subcord/1.1 (Android)")
            }
            return try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                parseGtx(body)
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

        /** Parse [[\"translated\",\"original\",...],...] style GTX JSON. */
        private fun parseGtx(body: String): String? {
            return try {
                val root = JSONArray(body)
                val chunks = root.optJSONArray(0) ?: return null
                val sb = StringBuilder()
                for (i in 0 until chunks.length()) {
                    val part = chunks.optJSONArray(i)?.optString(0)
                    if (!part.isNullOrEmpty()) sb.append(part)
                }
                sb.toString().trim().ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }
    }
}
