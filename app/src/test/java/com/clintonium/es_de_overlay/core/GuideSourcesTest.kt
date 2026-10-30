package com.clintonium.es_de_overlay.core

import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideSourcesTest {
    // The real GB/GBC atlas page around Shantae, plus both Link's Awakening sections.
    private val atlas = File("../fixtures/guides/vgmaps-gb-gbc-excerpt.html").readText(Charset.forName("windows-1252"))

    @Test
    fun `Shantae's section gives its 56 maps in page order`() {
        assertEquals("GB-GBC", VGMAPS_SYSTEMS["gbc"])
        val maps = vgmapsMaps(atlas, "Shantae")
        assertEquals(56, maps.size)
        assertEquals(listOf("Shantae-Info.png", "Shantae-Overworld.png"), maps.take(2))
        assertTrue(maps.all { it.startsWith("Shantae-") })
    }

    @Test
    fun `neighbouring sections keep their own maps`() {
        val before = vgmapsMaps(atlas, "Sea-Doo HydroCross")
        val after = vgmapsMaps(atlas, "Skate Or Die : Tour De Thrash")
        assertTrue(before.isNotEmpty() && after.isNotEmpty())
        assertTrue((before + after).none { it.startsWith("Shantae-") })
        assertTrue(vgmapsMaps(atlas, "Shantae").none { it in before || it in after })
    }

    @Test
    fun `a game not on the page has no maps`() {
        assertEquals(emptyList<String>(), vgmapsMaps(atlas, "Super Metroid"))
        assertEquals(emptyList<String>(), vgmapsMaps(atlas, "(USA)"))
    }

    @Test
    fun `only the exact Link's Awakening matches`() {
        val dx = vgmapsMaps(atlas, "The Legend of Zelda : Link's Awakening DX")
        val original = vgmapsMaps(atlas, "Legend of Zelda : Link's Awakening, The (USA)")
        assertEquals("LegendOfZelda-Link'sAwakeningDX-Koholint.png", dx.first())
        assertEquals("LegendOfZelda-Link'sAwakening-Koholint.png", original.first())
        assertTrue(dx.none { it in original })
    }

    @Test
    fun `names are cleaned and keyed like the sites write them`() {
        assertEquals(
            "The Legend Of Zelda III: Parallel Worlds",
            cleanName("Legend Of Zelda III : Parallel Worlds, The (USA)"),
        )
        assertEquals("Pokemon Pinball", cleanName("Pokemon Pinball [!] (Europe) (En,Fr)"))
        assertEquals(matchKey("Pokémon & Friends"), matchKey("Pok&eacute;mon &amp; Friends"))
        assertEquals("billtedsexcellent", matchKey("Bill &#38; Ted&#x27;s Excellent"))
    }

    @Test
    fun `an unmapped system has no atlas folder`() {
        assertNull(VGMAPS_SYSTEMS["switch"])
        assertEquals("https://www.vgmaps.com/Atlas/SuperNES/index.htm", vgmapsUrl(VGMAPS_SYSTEMS.getValue("snes")))
    }

    private fun parsed(name: String) = File("../fixtures/guides/$name").inputStream().use(::parsedText)!!
    private val pages = guidePages("Super Metroid", parsed("strategywiki-super-metroid-toc.xml"))

    @Test
    fun `StrategyWiki contents give the main page then the contents order`() {
        assertEquals(
            listOf(
                "Super Metroid", "Gameplay", "Controls", "Allies", "Enemies", "Bosses", "Walkthrough", "Space Colony Ceres",
                "Crateria", "Brinstar", "Norfair", "Wrecked Ship", "Maridia", "Tourian", "Items", "Objects",
                "Secrets, Tricks and Glitches",
            ).mapIndexed { i, p -> if (i == 0) p else "Super Metroid/$p" },
            pages,
        )
    }

    @Test
    fun `contents drop duplicates, the contents page, red links and other games`() {
        val toc = """<a href="/wiki/Super_Metroid/Bosses">Bosses</a>
            <a href="/w/index.php?title=Super_Metroid/Speedruns&amp;action=edit&amp;redlink=1" class="new">Speedruns</a>
            <a href="/wiki/Super_Metroid/Table_of_Contents">Table of Contents</a>
            <a href="/wiki/Metroid_Fusion/Walkthrough">Metroid Fusion</a>
            <a href="/wiki/File:ZericsSuperMetroidMaps.png">Complete Map</a>
            <a href="/wiki/Super_Metroid/Bosses#Kraid">Kraid</a>"""
        assertEquals(listOf("Super Metroid", "Super Metroid/Bosses"), guidePages("Super Metroid", toc))
    }

    @Test
    fun `a wiki page becomes a section without site chrome`() {
        val (section, images) = wikiSection("Crateria", parsed("strategywiki-super-metroid-page.xml"), pages)
        val html = section.html
        for (gone in listOf("<script", "<style", "<link", "mw-editsection", "nav_box", "section-ad", "id=\"toc\"", "/wiki/")) {
            assertTrue(gone, !html.contains(gone, ignoreCase = true))
        }
        val links = Regex("<a\\b[^>]*>").findAll(html).map { it.value }.toList()
        assertTrue(links.isNotEmpty())
        assertTrue(links.all { Regex("<a href=\"#s\\d+\">").matches(it) })
        assertTrue(images.isNotEmpty() && images.all { it.startsWith("https://") })
        val srcs = Regex("<img [^>]*src=\"([^\"]*)\"").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(images, srcs.distinct())
        assertTrue(!html.contains("srcset"))
    }

    @Test
    fun `a missing page gives null`() {
        val error = """<?xml version="1.0"?><api><error code="missingtitle" info="The page you specified doesn&#039;t exist." xml:space="preserve">See https://strategywiki.org/w/api.php for API usage.</error></api>"""
        assertNull(parsedText(error.byteInputStream()))
    }

    @Test
    fun `the parse URL encodes the page title`() {
        assertEquals(
            "https://strategywiki.org/w/api.php?action=parse&page=The_Legend_of_Zelda%3A_Link%27s_Awakening_DX" +
                "&prop=text&disableeditsection=1&redirects=1&format=xml",
            strategyWikiParseUrl("The Legend of Zelda: Link's Awakening DX"),
        )
    }
}
