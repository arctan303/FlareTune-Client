package com.arcinks.flaretune

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps translated labels inside the width budget of the fixed-width slots in the app.
 *
 * The numbers are deliberately rough (one CJK/Hangul/Kana character ≈ one em, ASCII ≈ 0.55 em) and
 * carry headroom, because their job is to catch a translation that would visibly break a compact
 * control — not to predict real font metrics. Slots that scroll, wrap or ellipsize freely are not
 * listed here; the audit for those lives in the i18n task document.
 */
class TranslationLengthBudgetTest {
    private data class Slot(val keys: List<String>, val budgetDp: Double, val fontSp: Double, val hint: String)

    private val files = listOf(
        "strings.xml",
        "library_actions.xml",
        "listening_features.xml",
        "player_lyrics.xml",
        "system_media.xml",
        "language.xml",
    )
    private val localeDirs = listOf("values", "values-zh-rCN", "values-b+zh+Hant", "values-ja", "values-ko")

    private val slots = listOf(
        Slot(
            keys = listOf("ft_home", "ft_discover_page", "ft_search_page", "ft_library_page"),
            budgetDp = 84.0,
            fontSp = 12.0,
            hint = "bottom navigation item",
        ),
        Slot(
            keys = listOf("ft_multiselect", "ft_play_all"),
            budgetDp = 110.0,
            fontSp = 14.0,
            hint = "all-songs header button",
        ),
        Slot(
            keys = listOf("ft_library_quick_next", "ft_library_quick_playlist", "ft_library_quick_favorite", "ft_library_quick_favorited"),
            budgetDp = 200.0,
            fontSp = 12.0,
            hint = "third-width quick action tile (two lines allowed)",
        ),
        Slot(
            keys = listOf("ft_sleep_timer_minutes"),
            budgetDp = 145.0,
            fontSp = 16.0,
            hint = "sleep timer preset chip, one of two columns",
        ),
        Slot(
            keys = listOf("ft_sleep_timer_custom"),
            budgetDp = 300.0,
            fontSp = 16.0,
            hint = "sleep timer custom-duration row, full width",
        ),
    )

    private fun resRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        var dir = File(userDir).absoluteFile
        while (true) {
            File(dir, "src/main/res").takeIf(File::isDirectory)?.let { return it }
            File(dir, "app/src/main/res").takeIf(File::isDirectory)?.let { return it }
            dir = dir.parentFile ?: error("Could not locate src/main/res from $userDir")
        }
    }

    private fun readTexts(dir: String): Map<String, String> {
        val root = resRoot()
        val texts = linkedMapOf<String, String>()
        for (name in files) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(File(root, dir), name))
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
                val key = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> texts[key] = element.textContent
                    "plurals" -> {
                        val items = (0 until element.childNodes.length)
                            .mapNotNull { element.childNodes.item(it) as? Element }
                            .filter { it.tagName == "item" }
                            .map { it.textContent }
                        // The longest plural form is the worst case for a one-line slot.
                        if (items.isNotEmpty()) texts[key] = items.maxByOrNull { it.length } ?: items.first()
                    }
                }
            }
        }
        return texts
    }

    /** Rough rendered width in dp for [text] at [fontSp]. */
    private fun estimatedWidthDp(text: String, fontSp: Double): Double {
        var em = 0.0
        for (ch in text) {
            val code = ch.code
            em += when {
                ch == ' ' -> 0.28
                code in 0x2E80..0x9FFF -> 1.0      // CJK
                code in 0xAC00..0xD7AF -> 1.0      // Hangul
                code in 0x3040..0x30FF -> 1.0      // Kana
                code in 0xFF00..0xFF60 -> 1.0      // Full-width forms
                ch.isUpperCase() || ch.isDigit() -> 0.62
                ch.isLowerCase() -> 0.52
                ch in ".,:;!?'\"()[]|·…" -> 0.32
                else -> 0.6
            }
        }
        return em * fontSp
    }

    @Test
    fun compactLabelsStayInsideTheirSlot() {
        val textsByLocale = localeDirs.associateWith { readTexts(it) }
        for (slot in slots) {
            for ((dir, texts) in textsByLocale) {
                for (key in slot.keys) {
                    val text = texts[key] ?: continue
                    val width = estimatedWidthDp(text, slot.fontSp)
                    assertTrue(
                        "$dir/$key is ~${width.toInt()}dp, over the ${slot.budgetDp.toInt()}dp budget of the " +
                            "${slot.hint}: \"$text\"",
                        width <= slot.budgetDp,
                    )
                }
            }
        }
    }
}
