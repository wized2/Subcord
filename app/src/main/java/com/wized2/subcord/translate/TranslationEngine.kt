package com.wized2.subcord.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Network translator with multiple backends (no native ML Kit libs).
 */
class TranslationEngine(
    private val targetLang: String = "en",
) {
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 500
    }

    suspend fun translateIfNeeded(text: String, skipIfEnglish: Boolean): String? =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.length < 2) return@withContext null
            synchronized(cache) { cache[trimmed]?.let { return@withContext it } }

            if (skipIfEnglish && looksPrimarilyEnglish(trimmed)) {
                return@withContext null
            }

            val translated = translateGtx(trimmed, targetLang)
                ?: translateMyMemory(trimmed, targetLang)
                ?: return@withContext null

            if (translated.equals(trimmed, ignoreCase = true)) return@withContext null
            synchronized(cache) { cache[trimmed] = translated }
            translated
        }

    fun close() {
        synchronized(cache) { cache.clear() }
    }

    companion object {
        fun looksPrimarilyEnglish(s: String): Boolean {
            val letters = s.filter { it.isLetter() }
            if (letters.length < 3) return false
            val nonLatin = letters.count { ch ->
                val block = Character.UnicodeBlock.of(ch)
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.HIRAGANA ||
                    block == Character.UnicodeBlock.KATAKANA ||
                    block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                    block == Character.UnicodeBlock.ARABIC ||
                    block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
                    block == Character.UnicodeBlock.ARABIC_EXTENDED_A ||
                    block == Character.UnicodeBlock.CYRILLIC ||
                    block == Character.UnicodeBlock.DEVANAGARI ||
                    block == Character.UnicodeBlock.THAI
            }
            if (nonLatin > 0) return false
            val latin = letters.count { it.lowercaseChar() in 'a'..'z' }
            return latin.toFloat() / letters.length >= 0.85f
        }

        private fun httpGet(urlStr: String, timeoutMs: Int = 10000): String? {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Subcord/1.1.1")
                setRequestProperty("Accept", "*/*")
            }
            return try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

        private fun translateGtx(text: String, target: String): String? {
            val q = URLEncoder.encode(text.take(900), StandardCharsets.UTF_8.name())
            val body = httpGet(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=$target&dt=t&q=$q",
            ) ?: return null
            return parseGtx(body)
        }

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

        /** Fallback when GTX is blocked/rate-limited. */
        private fun translateMyMemory(text: String, target: String): String? {
            val q = URLEncoder.encode(text.take(500), StandardCharsets.UTF_8.name())
            val body = httpGet(
                "https://api.mymemory.translated.net/get?q=$q&langpair=autodetect|$target",
            ) ?: return null
            return try {
                val obj = JSONObject(body)
                val translated = obj.optJSONObject("responseData")
                    ?.optString("translatedText")
                    ?.trim()
                    .orEmpty()
                if (translated.isEmpty() || translated.contains("INVALID", ignoreCase = true)) null
                else translated
            } catch (_: Exception) {
                null
            }
        }
    }
}
