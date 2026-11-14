package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun `fileName rejects dot dot separators and control characters`() {
        for (name in listOf("..", ".", "...", "", "  ", "\u0000")) assertEquals(name, "_", fileName(name))
        assertEquals("a_b", fileName("a/b"))
        assertEquals("a_b", fileName("a\\b"))
        assertEquals("a_.._b", fileName("a/../b"))
        assertEquals("ab", fileName("a\u0000b\n"))
        assertEquals("x".repeat(120), fileName("x".repeat(300)))
        // A character outside the BMP that the cap would cut in half goes whole.
        assertEquals("x".repeat(119), fileName("x".repeat(119) + "\uD83D\uDE00"))
    }

    @Test
    fun `map entry names keep the atlas's names but make no folders`() {
        assertEquals("038 L1: Dribble Fountain Labyrinth.png", mapEntryName(37, "L1: Dribble Fountain Labyrinth", "png"))
        assertEquals("001 .._.._x.png_y", mapEntryName(0, "../../x", "png/y"))
        assertEquals("002 ab.png", mapEntryName(1, "a\u0000b", "png"))
    }

    @Test
    fun `fileName keeps existing guide names`() {
        assertEquals("StrategyWiki - Super Metroid.html", "StrategyWiki - ${fileName("Super Metroid")}.html")
        assertEquals(
            "GameFAQs - Shantae – Guide and Walkthrough by DEngel.html",
            guideFileName(GuideSite.GAMEFAQS.label, "Shantae – Guide and Walkthrough by DEngel"),
        )
        assertEquals("003 Tail Cave.png", mapEntryName(2, "Tail Cave", "png"))
        assertEquals("Zelda - Link's Awakening", fileName("Zelda: Link's Awakening"))
    }

    private fun refusal(check: () -> Unit) = assertThrows(GuideTooLarge::class.java, check).message

    @Test
    fun `a CBZ page at the size limit loads and one byte over is refused with the size`() {
        checkCbzPage(MAX_CBZ_PAGE_BYTES)
        assertEquals("a page is larger than 64 MB", refusal { checkCbzPage(MAX_CBZ_PAGE_BYTES + 1) })
    }

    @Test
    fun `a CBZ at the page limit loads and one more page is refused`() {
        checkCbzPages(MAX_CBZ_PAGES)
        assertEquals("more than 2000 pages", refusal { checkCbzPages(MAX_CBZ_PAGES + 1) })
    }

    @Test
    fun `a picture over the pixel limit is refused from its size alone`() {
        checkImage(16_896, 14_336)
        checkImage(1_000_000, 1_000)
        assertEquals("a picture is larger than 1000 megapixels", refusal { checkImage(1_000_001, 1_000) })
        // The largest PNG: its width times its height doesn't fit in an Int.
        refusal { checkImage(Int.MAX_VALUE, Int.MAX_VALUE) }
    }

    @Test
    fun `a text guide at the limit loads and one byte over is refused`() {
        checkText(MAX_TEXT_BYTES)
        assertEquals("larger than 8 MB", refusal { checkText(MAX_TEXT_BYTES + 1) })
    }
}
