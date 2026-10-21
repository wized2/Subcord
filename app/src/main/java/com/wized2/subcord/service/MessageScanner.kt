package com.wized2.subcord.service

import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedHashSet

class MessageScanner(private val maxSeen: Int = 80) {
    private val seen = object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            val added = super.add(element)
            while (size > maxSeen) remove(iterator().next())
            return added
        }
    }

    fun collectNewMessages(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val lines = ArrayList<String>()
        walk(root, lines)
        return lines.filter { seen.add(fingerprint(it)) }
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val candidate = when {
            text.length >= 2 -> text
            desc.length >= 2 -> desc
            else -> ""
        }
        if (candidate.isNotEmpty() && looksLikeMessage(candidate)) out.add(candidate)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, out)
            child.recycle()
        }
    }

    private fun looksLikeMessage(s: String): Boolean {
        if (s.length > 800) return false
        if (s.matches(Regex("""^\d{1,2}:\d{2}(\s?[AP]M)?$"""))) return false
        if (s.equals("Discord", true)) return false
        if (s.startsWith("http://") || s.startsWith("https://")) return false
        return s.count { it.isLetter() } >= 3
    }

    private fun fingerprint(s: String) = s.lowercase().replace(Regex("""\s+"""), " ").take(240)
}

