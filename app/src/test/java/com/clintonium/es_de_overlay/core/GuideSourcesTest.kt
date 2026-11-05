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
        val maps = vgmapsMaps(atlas, "Shantae").map { it.href }
        assertEquals(56, maps.size)
        assertEquals(listOf("Shantae-Info.png", "Shantae-Overworld.png"), maps.take(2))
        assertTrue(maps.all { it.startsWith("Shantae-") })
    }

    @Test
    fun `neighbouring sections keep their own maps`() {
        val before = vgmapsMaps(atlas, "Sea-Doo HydroCross").map { it.href }
        val after = vgmapsMaps(atlas, "Skate Or Die : Tour De Thrash").map { it.href }
        assertTrue(before.isNotEmpty() && after.isNotEmpty())
        assertTrue((before + after).none { it.startsWith("Shantae-") })
        assertTrue(vgmapsMaps(atlas, "Shantae").none { it.href in before || it.href in after })
    }

    @Test
    fun `a game not on the page has no maps`() {
        assertEquals(emptyList<VgMap>(), vgmapsMaps(atlas, "Super Metroid"))
        assertEquals(emptyList<VgMap>(), vgmapsMaps(atlas, "(USA)"))
    }

    @Test
    fun `only the exact Link's Awakening matches`() {
        val dx = vgmapsMaps(atlas, "The Legend of Zelda : Link's Awakening DX").map { it.href }
        val original = vgmapsMaps(atlas, "Legend of Zelda : Link's Awakening, The (USA)").map { it.href }
        assertEquals("LegendOfZelda-Link'sAwakeningDX-Koholint.png", dx.first())
        assertEquals("LegendOfZelda-Link'sAwakening-Koholint.png", original.first())
        assertTrue(dx.none { it in original })
    }

    @Test
    fun `maps are named by their link text, after a group the text doesn't say`() {
        val shantae = vgmapsMaps(atlas, "Shantae").map { it.name }
        assertEquals(listOf("Info", "Overworld", "Start - Part 1", "Start - Part 2"), shantae.take(4))
        assertTrue("L1: Dribble Fountain Labyrinth" in shantae)
        assertTrue("Mini Games: Dance Parlor & Gecko Match" in shantae)
        assertEquals("T4: Rain Storm (Day)", shantae[shantae.indexOf("L4: Twinkle Palace Labyrinth") + 1])
        val links = vgmapsMaps(atlas, "Legend of Zelda : Link's Awakening, The (USA)").map { it.name }
        assertEquals(listOf("Koholint", "Tail Cave", "Bottle Grotto", "Key Cavern", "Angler's Tunnel"), links.take(5))
        assertEquals("Koholint (In-Game Map)", vgmapsMaps(atlas, "The Legend of Zelda : Link's Awakening DX")[1].name)
        assertEquals("American Circuit - Niagara Falls", vgmapsMaps(atlas, "Sea-Doo HydroCross").first().name)
    }

    @Test
    fun `character references decode`() {
        assertEquals("Dance & Gecko's <\"Match\"> é", unescapeHtml("Dance &amp; Gecko&#39;s &lt;&quot;Match&quot;&gt; &#xE9;"))
        assertEquals("&copy; &#xZZ;", unescapeHtml("&copy; &#xZZ;"))
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

    private val linksAwakening = "The Legend of Zelda : Link's Awakening DX"

    @Test
    fun `each site opens on the game, as the sites' own forms wrote it`() {
        assertEquals(
            "https://gamefaqs.gamespot.com/search?game=The+Legend+of+Zelda%3A+Link%27s+Awakening+DX",
            startUrl(GuideSite.GAMEFAQS, linksAwakening),
        )
        assertEquals(
            "https://www.neoseeker.com/seeker.php?q=The+Legend+of+Zelda+Link%27s+Awakening+DX&searchby=product&area=all",
            startUrl(GuideSite.NEOSEEKER, linksAwakening),
        )
        assertEquals("https://www.zeldadungeon.net/links-awakening-walkthrough/", startUrl(GuideSite.ZELDADUNGEON, linksAwakening))
    }

    @Test
    fun `ZeldaDungeon is only for games it has a walkthrough for`() {
        assertNull(startUrl(GuideSite.ZELDADUNGEON, "Shantae"))
        assertNull(startUrl(GuideSite.ZELDADUNGEON, "The Legend of Zelda : Four Swords Anniversary Edition"))
        fun walkthrough(name: String) = startUrl(GuideSite.ZELDADUNGEON, name)?.removePrefix("https://www.zeldadungeon.net/")
        assertEquals("the-legend-of-zelda-walkthrough/", walkthrough("Legend of Zelda, The (USA)"))
        assertEquals("the-adventure-of-link-walkthrough/", walkthrough("Zelda II : The Adventure of Link (USA)"))
        assertEquals("a-link-to-the-past-walkthrough/", walkthrough("The Legend of Zelda : A Link to the Past & Four Swords"))
        assertEquals("ocarina-of-time-walkthrough/", walkthrough("The Legend of Zelda : Ocarina of Time 3D"))
        assertEquals("echoes-of-wisdom-walkthrough-guide/", walkthrough("The Legend of Zelda : Echoes of Wisdom"))
    }

    @Test
    fun `guide pages are told from the sites' other pages`() {
        val guides = listOf(
            "https://gamefaqs.gamespot.com/gameboy/563277-the-legend-of-zelda-links-awakening/faqs/18445",
            "https://gamefaqs.gamespot.com/ps4/222462-shantae-half-genie-hero-ultimate-edition/faqs/74267/main-walkthrough",
            "https://www.neoseeker.com/linkdx/faqs/182906-zelda-links-awakening-h.html",
            "https://www.neoseeker.com/metroid-samus-returns/walkthrough/Collectathon_-_Surface",
            "https://www.neoseeker.com/animal-well/Path_of_the_Seahorse_(B._Flame)",
            "https://www.zeldadungeon.net/links-awakening-walkthrough/",
            "https://www.zeldadungeon.net/links-awakening-walkthrough/key-cavern-game-boy-color/",
            "https://www.zeldadungeon.net/echoes-of-wisdom-walkthrough-guide/",
        )
        val others = listOf(
            "https://gamefaqs.gamespot.com/search?game=Shantae",
            "https://gamefaqs.gamespot.com/gbc/550864-shantae",
            "https://gamefaqs.gamespot.com/gbc/550864-shantae/faqs",
            "https://gamefaqs.gamespot.com/boards/563277-the-legend-of-zelda-links-awakening",
            "https://www.neoseeker.com/",
            "https://www.neoseeker.com/seeker.php?q=Shantae&searchby=product&area=all",
            "https://www.neoseeker.com/linkdx/",
            "https://www.neoseeker.com/linkdx/faqs/",
            "https://www.neoseeker.com/linkdx/cheats/gbc/",
            "https://www.neoseeker.com/forums/2062/t105346-please-help-im-desperate/",
            "https://www.zeldadungeon.net/",
            "https://www.zeldadungeon.net/?s=Link",
            "https://www.zeldadungeon.net/wiki/Link's_Awakening_Items",
            "https://www.zeldadungeon.net/retro-remake-rumble-links-awakening/",
            "file:///sdcard/guide.html",
            "about:blank",
            "https://duckduckgo.com/?q=Shantae+guide",
        )
        // Pages elsewhere are up to Reader View's rules.
        val elsewhere = listOf("https://strategywiki.org/wiki/Super_Metroid/Walkthrough", "http://example.com/guide")
        assertEquals(guides + elsewhere, (guides + elsewhere).filter(::isGuidePage))
        assertEquals(emptyList<String>(), others.filter(::isGuidePage))
        assertEquals(GuideSite.NEOSEEKER, siteOf("https://neoseeker.com/linkdx/faqs/"))
        assertNull(siteOf("https://zelda.neoseeker.com/wiki/Link"))
    }

    @Test
    fun `the web search looks for the game's guides`() {
        assertEquals(
            "https://duckduckgo.com/?q=Skate+Or+Die%3A+Tour+De+Thrash+guide",
            webSearchUrl("Skate Or Die : Tour De Thrash (USA) [!]"),
        )
    }

    @Test
    fun `bot checks are told by their titles`() {
        for (title in listOf("Just a moment...", "Security check", "Attention Required! | Cloudflare")) {
            assertTrue(title, isBotCheck(title))
        }
        assertTrue(!isBotCheck("Game Search - GameFAQs"))
    }

    @Test
    fun `guide file names drop reserved characters and stay short`() {
        assertEquals("GameFAQs - Shantae - FAQ_Walkthrough _.html", guideFileName(GuideSite.GAMEFAQS.label, "Shantae: FAQ/Walkthrough ?"))
        assertEquals("ign.com - Shantae Guide.html", guideFileName("ign.com", "Shantae Guide"))
        val long = guideFileName(GuideSite.NEOSEEKER.label, "x".repeat(200))
        assertEquals(120, long.length)
        assertTrue(long.startsWith("Neoseeker - xxx") && long.endsWith("x.html"))
    }
}
