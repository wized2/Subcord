package com.wized2.subcord.translate

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class TranslationEngine(private val targetLang: String = TranslateLanguage.ENGLISH) {
    private val langId = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 400
    }

    suspend fun identifyLanguage(text: String): String? = suspendCancellableCoroutine { cont ->
        langId.identifyLanguage(text)
            .addOnSuccessListener { code -> cont.resume(if (code == "und") null else code) }
            .addOnFailureListener { cont.resume(null) }
    }

    suspend fun translateIfNeeded(text: String, skipIfEnglish: Boolean): String? {
        val trimmed = text.trim()
        if (trimmed.length < 2) return null
        synchronized(cache) { cache[trimmed]?.let { return it } }
        val src = identifyLanguage(trimmed) ?: return null
        if (skipIfEnglish && (src == "en" || src.startsWith("en-"))) return null
        if (src == targetLang) return null
        val mlSrc = TranslateLanguage.fromLanguageTag(src) ?: return null
        val mlTgt = TranslateLanguage.fromLanguageTag(targetLang) ?: TranslateLanguage.ENGLISH
        val key = "$mlSrc>$mlTgt"
        val translator = translators.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(mlSrc).setTargetLanguage(mlTgt).build()
            )
        }
        val modelOk = suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded()
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
        if (!modelOk) return null
        val out = suspendCancellableCoroutine { cont ->
            translator.translate(trimmed)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        if (out != null) synchronized(cache) { cache[trimmed] = out }
        return out
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
        langId.close()
    }
}
