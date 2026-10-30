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
}
