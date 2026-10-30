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
}
