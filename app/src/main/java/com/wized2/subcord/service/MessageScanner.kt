package com.wized2.subcord.service

import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedHashSet

/**
 * Walks Discord's accessibility tree and returns only new message-like lines.
 * Keeps a short LRU of fingerprints so the same bubble is not retranslated.
 */
class MessageScanner(private val maxSeen: Int = 120) {
    private val seen = object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            val added = super.add(element)
            while (size > maxSeen) remove(iterator().next())
            return added
        }
    }

    fun collectNewMessages(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val lines = ArrayList<String>(16)
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
        if (candidate.isNotEmpty() && looksLikeMessage(candidate)) {
            out.add(candidate)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, out)
            try {
                child.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun looksLikeMessage(s: String): Boolean {
        if (s.length < 2 || s.length > 1200) return false
        if (TIMESTAMP.matches(s)) return false
        if (s.matches(Regex("""^\d+$"""))) return false
        if (UI_LABELS.any { it.equals(s, ignoreCase = true) }) return false
        if (s.startsWith("http://") || s.startsWith("https://")) return false
        if (s.count { it.isLetter() } < 3) return false
        return true
    }

    private fun fingerprint(s: String): String =
        s.lowercase().replace(WHITESPACE, " ").take(280)

    companion object {
        private val TIMESTAMP = Regex("""^\d{1,2}:\d{2}(\s?[AP]M)?$""", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex("""\s+""")
        private val UI_LABELS = listOf(
            "Discord", "Search", "Members", "Gift", "GIF", "Stickers", "Emoji",
            "Apps", "Message", "Send a message", "Reply", "Edit", "Delete",
            "Copy Text", "Pin Message", "Mark Unread", "Mute", "Unmute",
            "Add Reaction", "Forward", "Create Thread", "More", "Cancel",
            "Online", "Idle", "Do Not Disturb", "Invisible", "Offline",
            "Voice Connected", "Disconnect", "Share Your Screen",
            "Start Video", "Turn Off Camera", "Deafened",
            "Home", "Friends", "Nitro", "Shop", "Discover", "Notifications",
            "Inbox", "Help", "User Settings", "Log Out",
        )
    }
}
