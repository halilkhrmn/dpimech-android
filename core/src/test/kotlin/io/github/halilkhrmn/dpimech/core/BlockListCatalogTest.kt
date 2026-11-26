package io.github.halilkhrmn.dpimech.core

import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalog in this repository (`blocklists/catalog.json`) follows the rule the owner set: only
 * lists with an open licence, hosted on GitHub (or GitLab, Codeberg), with a note in every app
 * language.
 */
class BlockListCatalogTest {
    private val file = File("../blocklists/catalog.json")
    private val hosts = setOf("raw.githubusercontent.com", "gitlab.com", "codeberg.org")
    private val licences = setOf(
        "GPL-3.0", "GPL-2.0", "MIT", "MPL-2.0", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause",
        "CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "Unlicense",
    )
    private val languages = setOf("en", "tr", "ru", "fa", "ar")

    @Test
    fun everyEntryIsUsableOpenAndOnACodeHost() {
        val text = file.readText()
        val lists = BlockListCatalog.parse(text)
        val raw = Regex("\"id\"\\s*:").findAll(text).count()
        assertEquals(raw, lists.size, "an entry was dropped as unusable")
        assertTrue(lists.any { it.id == BlockList.DEFAULT }, "the default list is in the catalog")
        for (l in lists) {
            val host = URI(l.url).host
            assertTrue(host in hosts || host.endsWith(".github.io"), "${l.id}: $host is not a code host")
            assertTrue(l.license in licences, "${l.id}: licence ${l.license}")
            assertTrue(l.homepage.startsWith("https://github.com/") || l.homepage.startsWith("https://gitlab.com/") ||
                l.homepage.startsWith("https://codeberg.org/"), "${l.id}: homepage ${l.homepage}")
            assertEquals(languages, l.notes.keys, "${l.id}: notes")
        }
    }
}
