package com.flaretune.client

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
 * format placeholders per key, the plural forms each language actually needs, and the five language
 * names that must never be translated.
 */
class StringResourceParityTest {
    private data class Item(val quantity: String, val text: String)

    private data class Entry(
        val items: List<Item>,
        val translatable: Boolean,
    ) {
        val text: String get() = items.joinToString(" | ") { it.text }
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

    private fun resRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        var dir = File(userDir).absoluteFile
        while (true) {
            File(dir, "src/main/res").takeIf(File::isDirectory)?.let { return it }
            File(dir, "app/src/main/res").takeIf(File::isDirectory)?.let { return it }
            dir = dir.parentFile ?: error("Could not locate src/main/res from $userDir")
        }
    }

    private fun read(dir: String): Map<String, Entry> {
        val root = resRoot()
        val entries = linkedMapOf<String, Entry>()
        for (name in files) {
            val file = File(File(root, dir), name)
            assertTrue("missing resource file $dir/$name", file.isFile)
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index) as? Element ?: continue
                val key = element.getAttribute("name")
                assertTrue("blank resource name in $dir/$name", key.isNotBlank())
                when (element.tagName) {
                    "string" -> entries[key] = Entry(
                        items = listOf(Item("", element.textContent)),
                        translatable = element.getAttribute("translatable") != "false",
                    )
                    "plurals" -> {
                        val items = (0 until element.childNodes.length)
                            .mapNotNull { element.childNodes.item(it) as? Element }
                            .filter { it.tagName == "item" }
                            .map { Item(it.getAttribute("quantity"), it.textContent) }
                        entries[key] = Entry(items = items, translatable = true)
                    }
                }
            }
        }
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
    fun placeholdersMatchTheBaseLanguage() {
        val base = read(baseDir)
        for (dir in localeDirs) {
            val locale = read(dir)
            for ((key, entry) in locale) {
                val reference = base[key] ?: continue
                val expected = reference.items.flatMap { placeholders(it.text) }.toSet()
                for (item in entry.items) {
                    assertEquals(
                        "$dir/$key changed format placeholders",
                        expected,
                        placeholders(item.text).toSet(),
                    )
                }
            }
        }
    }

    @Test
    fun translationsAreNotEmpty() {
        val base = read(baseDir)
        for (dir in localeDirs) {
            val locale = read(dir)
            for ((key, entry) in locale) {
                assertTrue("$dir/$key lost its base entry", base.containsKey(key))
                assertTrue("$dir/$key is blank", entry.items.all { it.text.isNotBlank() })
                val reference = base.getValue(key)
                if (!reference.translatable) continue
                // A translated value identical to the English base is a leftover, except for values that
                // carry no words of their own (pure placeholder patterns such as "%1$s · %2$s").
                if (reference.items.joinToString(" ") { it.text }.replace(placeholder, "").none { it.isLetter() }) continue
                assertTrue("$dir/$key is still the English base text", entry.text != reference.text)
            }
        }
    }

    @Test
    fun pluralsUseTheFormsEachLanguageNeeds() {
        val base = read(baseDir)
        val basePlurals = base.filterValues { it.items.size > 1 }
        assertTrue("expected plural resources", basePlurals.size >= 7)
        for ((key, entry) in basePlurals) {
            assertEquals("values/$key plural forms", listOf("one", "other"), entry.items.map { it.quantity })
        }
        for (dir in localeDirs) {
            val locale = read(dir)
            for ((key, entry) in locale.filter { (_, value) -> value.items.size > 1 || value.items.first().quantity.isNotEmpty() }) {
                assertTrue("$dir/$key must only use the other quantity", entry.items.map { it.quantity } == listOf("other"))
                assertTrue("$dir/$key is not a base plural", basePlurals.containsKey(key))
            }
            assertEquals("$dir plural keys", basePlurals.keys, locale.filterValues { it.items.first().quantity.isNotEmpty() }.keys)
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
