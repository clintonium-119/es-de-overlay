package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidesTest {
    private val easyland = GameRef("snes", "02) Hacks/Easyland.zip")

    @Test
    fun `manual path mirrors the ROM subfolder`() {
        assertEquals("snes/manuals/02) Hacks/Easyland.pdf", manualPath(easyland, false).joinToString("/"))
    }

    @Test
    fun `manual for a directory ROM keeps its whole name`() {
        assertEquals(
            "scummvm/manuals/dig.scummvm.pdf",
            manualPath(GameRef("scummvm", "dig.scummvm"), true).joinToString("/"),
        )
    }

    @Test
    fun `guide path ends in the ROM basename`() {
        assertEquals(listOf("snes", "guides", "02) Hacks", "Easyland"), guidePath(easyland, false))
        assertEquals(listOf("scummvm", "guides", "dig.scummvm"), guidePath(GameRef("scummvm", "dig.scummvm"), true))
    }

    @Test
    fun `a single guide file matches whatever the extension's case`() {
        assertEquals(listOf("Easyland.PDF"), guideEntries("Easyland", listOf("Easyland.PDF"), emptyList()))
    }

    @Test
    fun `guides in a base folder are listed sorted by name`() {
        assertEquals(
            listOf("Easyland/guide.pdf", "Easyland/Map.png", "Easyland/pages.cbz", "Easyland/walkthrough.txt"),
            guideEntries(
                "Easyland",
                listOf("Easyland"),
                listOf("walkthrough.txt", "pages.cbz", "Map.png", "guide.pdf"),
            ),
        )
    }

    @Test
    fun `a file and a folder both present return both`() {
        assertEquals(
            listOf("Easyland.txt", "Easyland/map.jpeg"),
            guideEntries("Easyland", listOf("Easyland", "Easyland.txt"), listOf("map.jpeg")),
        )
    }

    @Test
    fun `a saved html guide is listed with the other formats`() {
        assertEquals(
            listOf("Shantae/guide.html", "Shantae/maps.cbz", "Shantae/notes.txt"),
            guideEntries("Shantae", listOf("Shantae"), listOf("notes.txt", "guide.html", "maps.cbz")),
        )
    }

    @Test
    fun `unsupported extensions are skipped`() {
        assertEquals(
            listOf("Easyland/scan.jpg"),
            guideEntries("Easyland", listOf("Easyland.cbr", "Easyland.epub"), listOf("readme.doc", "scan.jpg")),
        )
    }

    @Test
    fun `another game sharing a prefix does not match`() {
        assertEquals(emptyList<String>(), guideEntries("Easyland", listOf("Easyland 2.pdf", "Easyland2.txt"), emptyList()))
    }

    @Test
    fun `no guides gives an empty list`() {
        assertEquals(emptyList<String>(), guideEntries("Easyland", emptyList(), emptyList()))
    }

    private fun file(path: String, size: Long = 1) = GuideFile(path, size, "tree/$path")

    @Test
    fun `guides in a game's folder group under its gamelist name and last played`() {
        val wiki = file("Shantae/StrategyWiki - Shantae.html")
        val faqs = file("Shantae/GameFAQs - Shantae Walkthrough.html")
        assertEquals(
            listOf(GuideGame("gbc", "", "Shantae", "Shantae", "20261102T201500", listOf(faqs, wiki))),
            guideGames("gbc", listOf(wiki, faqs), listOf(GamelistGame("Shantae.zip", "Shantae", "20261102T201500"))),
        )
    }

    @Test
    fun `a guide file in a ROM subfolder groups under that game`() {
        val guide = file("02) Hacks/Easyland.pdf")
        assertEquals(
            listOf(GuideGame("snes", "02) Hacks", "Easyland", "Easyland", null, listOf(guide))),
            guideGames("snes", listOf(guide), listOf(GamelistGame("02) Hacks/Easyland.zip", "Easyland", null))),
        )
    }

    @Test
    fun `a guides folder inside a ROM subfolder groups under the game, not the subfolder`() {
        val base = "Pitfall! - Pitfall Harry's Jungle Adventure"
        val guide = file("01) All but the Best/$base/GameFAQs - Walkthrough.html")
        assertEquals(
            listOf(GuideGame("snes", "01) All but the Best", base, "Pitfall!", null, listOf(guide))),
            guideGames("snes", listOf(guide), listOf(GamelistGame("01) All but the Best/$base.zip", "Pitfall!", null))),
        )
    }

    @Test
    fun `a directory ROM matches a folder with its whole name`() {
        val guide = file("dig.scummvm/guide.pdf")
        assertEquals(
            listOf(GuideGame("scummvm", "", "dig.scummvm", "The Dig", null, listOf(guide))),
            guideGames("scummvm", listOf(guide), listOf(GamelistGame("dig.scummvm", "The Dig", null))),
        )
    }

    @Test
    fun `guides matching no game group under their folder or their own name`() {
        val inFolder = file("Not A Real Game/test.txt")
        val alone = file("Lonely.pdf")
        assertEquals(
            listOf(
                GuideGame("gbc", "", "Not A Real Game", "Not A Real Game", null, listOf(inFolder)),
                GuideGame("gbc", "", "Lonely", "Lonely", null, listOf(alone)),
            ),
            guideGames("gbc", listOf(inFolder, alone), listOf(GamelistGame("Shantae.zip", "Shantae", null))),
        )
    }

    @Test
    fun `unsupported files are left out and a game's size sums its guides`() {
        val games = guideGames(
            "gbc",
            listOf(file("Shantae/a.html", 100), file("Shantae/b.cbr", 50), file("Shantae/c.nfo", 5), file("Shantae.PDF", 20)),
            listOf(GamelistGame("Shantae.zip", "Shantae", null)),
        )
        assertEquals(listOf("Shantae.PDF", "Shantae/a.html"), games.single().files.map { it.path })
        assertEquals(120L, games.single().size)
    }

    @Test
    fun `two games with the same stem resolve to the first in the gamelist`() {
        val games = listOf(GamelistGame("Game.zip", "First", null), GamelistGame("Game.7z", "Second", null))
        assertEquals("First", guideGames("gb", listOf(file("Game.pdf")), games).single().name)
    }
}
