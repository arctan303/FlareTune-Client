package com.arcinks.flaretune

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the five interface languages the app ships (zh-CN, zh-Hant, en, ja, ko).
 *
 * Wording may change freely; this test only enforces structure: the same keys everywhere, the same
 * format placeholders per key, the plural forms each language actually needs, no untranslated
 * leftovers per plural quantity, and the five language names that must never be translated.
 */
class StringResourceParityTest {
    private data class Item(val quantity: String, val text: String)

    private data class Entry(val items: List<Item>, val translatable: Boolean) {
        val text: String get() = items.joinToString(" | ") { it.text }

        /** Worst case for a single-line slot: the plural "other" form, or the only value. */
        fun other(): String? =
            items.firstOrNull { it.quantity == "other" }?.text ?: items.firstOrNull()?.text
    }

    private val files = listOf(
        "strings.xml",
        "library_actions.xml",
        "listening_features.xml",
        "player_lyrics.xml",
        "system_media.xml",
        "language.xml",
    )
    private val baseDir = "values"
    private val localeDirs = listOf("values-zh-rCN", "values-b+zh+Hant", "values-ja", "values-ko")
    private val placeholder = Regex("""%\d+\$[sd]""")

    private fun mainRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        var dir = File(userDir).absoluteFile
        while (true) {
            File(dir, "src/main/res").takeIf(File::isDirectory)?.let { return it }
            File(dir, "app/src/main/res").takeIf(File::isDirectory)?.let { return it }
            dir = dir.parentFile ?: error("Could not locate src/main/res from $userDir")
        }
    }

    private fun debugRoot(): File = File(mainRoot().parentFile.parentFile, "debug/res")

    private fun parse(file: File, into: MutableMap<String, Entry>) {
        assertTrue("missing resource file ${file.path}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            val key = element.getAttribute("name")
            assertTrue("blank resource name in ${file.path}", key.isNotBlank())
            when (element.tagName) {
                "string" -> into[key] = Entry(
                    items = listOf(Item("", element.textContent)),
                    translatable = element.getAttribute("translatable") != "false",
                )
                "plurals" -> {
                    val items = (0 until element.childNodes.length)
                        .mapNotNull { element.childNodes.item(it) as? Element }
                        .filter { it.tagName == "item" }
                        .map { Item(it.getAttribute("quantity"), it.textContent) }
                    into[key] = Entry(items = items, translatable = true)
                }
            }
        }
    }

    private fun read(dir: String): Map<String, Entry> {
        val entries = linkedMapOf<String, Entry>()
        for (name in files) parse(File(File(mainRoot(), dir), name), entries)
        return entries
    }

    /** The debug source set only carries the preview harness strings. */
    private fun readDebug(dir: String): Map<String, Entry> {
        val entries = linkedMapOf<String, Entry>()
        parse(File(File(debugRoot(), dir), "strings.xml"), entries)
        return entries
    }

    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLocaleShipsEveryTranslatableKey() {
        val base = read(baseDir)
        val expected = base.filterValues { it.translatable }.keys
        assertTrue("base language lost keys", expected.size > 400)
        for (dir in localeDirs) {
            assertEquals("$dir must ship exactly the translatable base keys", expected, read(dir).keys)
        }
    }

    @Test
    fun debugPreviewStringsShipInEveryLocale() {
        val base = readDebug(baseDir)
        assertEquals(setOf("ft_preview_mode", "ft_open_app"), base.keys)
        for (dir in localeDirs) {
            val locale = readDebug(dir)
            assertEquals("debug/$dir must mirror the debug base keys", base.keys, locale.keys)
            for ((key, entry) in locale) {
                assertTrue("debug/$dir/$key is blank", entry.text.isNotBlank())
                assertFalse("debug/$dir/$key still holds the English text", entry.text == base.getValue(key).text)
            }
        }
    }

    @Test
    fun placeholdersMatchTheBaseLanguage() {
        val base = read(baseDir)
        for (dir in localeDirs) {
            for ((key, entry) in read(dir)) {
                val reference = base[key] ?: continue
                val expected = reference.items.flatMap { placeholders(it.text) }.toSet()
                for (item in entry.items) {
                    assertEquals("$dir/$key changed format placeholders", expected, placeholders(item.text).toSet())
                }
            }
        }
    }

    @Test
    fun translationsAreNotEmpty() {
        val base = read(baseDir)
        for (dir in localeDirs) {
            for ((key, entry) in read(dir)) {
                assertTrue("$dir/$key is blank", entry.items.isNotEmpty() && entry.items.all { it.text.isNotBlank() })
                val reference = base[key] ?: continue
                if (!reference.translatable) continue
                // Compare the same quantity, so an untranslated plural cannot hide behind the
                // "one | other" concatenation of the base entry.
                val referenceText = reference.other() ?: continue
                val localeText = entry.other() ?: continue
                // Values that carry no words of their own (pure placeholder patterns) are exempt.
                if (referenceText.replace(placeholder, "").none { it.isLetter() }) continue
                assertTrue("$dir/$key is still the English base text", localeText != referenceText)
            }
        }
    }

    @Test
    fun pluralsUseTheFormsEachLanguageNeeds() {
        val base = read(baseDir)
        fun pluralEntries(entries: Map<String, Entry>) = entries.filterValues { entry -> entry.items.any { it.quantity.isNotEmpty() } }
        val basePlurals = pluralEntries(base)
        assertTrue("expected plural resources", basePlurals.size >= 7)
        for ((key, entry) in basePlurals) {
            assertTrue("values/$key has no plural items", entry.items.isNotEmpty())
            assertEquals("values/$key plural forms", listOf("one", "other"), entry.items.map { it.quantity })
        }
        for (dir in localeDirs) {
            val localePlurals = pluralEntries(read(dir))
            for ((key, entry) in localePlurals) {
                assertTrue("$dir/$key must only use the other quantity", entry.items.map { it.quantity } == listOf("other"))
                assertTrue("$dir/$key is not a base plural", basePlurals.containsKey(key))
            }
            assertEquals("$dir plural keys", basePlurals.keys, localePlurals.keys)
        }
    }

    @Test
    fun languageNamesStayUntranslatedAndEveryLocaleIsListed() {
        val base = read(baseDir)
        val names = mapOf(
            "ft_language_zh_cn" to "简体中文",
            "ft_language_zh_hant" to "繁體中文",
            "ft_language_en" to "English",
            "ft_language_ja" to "日本語",
            "ft_language_ko" to "한국어",
        )
        for ((key, value) in names) {
            val entry = base[key] ?: error("missing $key")
            assertEquals("$key display name", value, entry.text.trim())
            assertFalse("$key must not be translated", entry.translatable)
        }
        assertEquals(
            "only brand and language names may be non-translatable",
            names.keys + "app_name",
            base.filterValues { !it.translatable }.keys,
        )
    }
}
