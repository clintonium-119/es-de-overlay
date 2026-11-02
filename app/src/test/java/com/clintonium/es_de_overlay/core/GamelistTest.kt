package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamelistTest {
    // Shaped like the Nova's gamelists/androidgames/gamelist.xml and gbc's Shantae entry, plus entity and duplicate cases.
    private val xml = """
        <?xml version="1.0"?>
        <gameList>
        	<game>
        		<path>./Dead Cells.app</path>
        		<name>Dead Cells</name>
        		<playcount>2</playcount>
        	</game>
        	<game>
        		<path>./TMNT Shredder&apos;s Revenge.app</path>
        		<name>TMNT Shredder&apos;s Revenge</name>
        	</game>
        	<game>
        		<path>./01) All but the Best/Tetris &amp; Dr. Mario.sfc</path>
        		<name>Tetris &amp; Dr. Mario</name>
        	</game>
        	<game>
        		<path>./02) Hacks/Tetris &amp; Dr. Mario.sfc</path>
        		<name>Tetris &amp; Dr. Mario</name>
        	</game>
        	<game>
        		<path>./Shantae.zip</path>
        		<name>Shantae</name>
        		<desc>Scuttle Town &amp; its genie.

        Risky Boots invades.</desc>
        		<rating>0.8</rating>
        		<releasedate>20020602T000000</releasedate>
        		<developer>Wayforward</developer>
        		<publisher>Capcom</publisher>
        		<genre>Platform</genre>
        		<players>1</players>
        		<playcount>29</playcount>
        	</game>
        </gameList>
    """.trimIndent()

    private fun lookup(name: String) = gamelistPaths(xml.byteInputStream(), name)

    @Test
    fun `name resolves to its stub path without the dot-slash`() {
        assertEquals(listOf("Dead Cells.app"), lookup("Dead Cells"))
    }

    @Test
    fun `xml entities decode before matching`() {
        assertEquals(listOf("TMNT Shredder's Revenge.app"), lookup("TMNT Shredder's Revenge"))
    }

    @Test
    fun `duplicate names return every match and unknown names none`() {
        assertEquals(
            listOf("01) All but the Best/Tetris & Dr. Mario.sfc", "02) Hacks/Tetris & Dr. Mario.sfc"),
            lookup("Tetris & Dr. Mario"),
        )
        assertEquals(emptyList<String>(), lookup("Super Metroid"))
    }

    @Test
    fun `metadata resolves by path with entities decoded`() {
        assertEquals(
            GameInfo("Wayforward", "Capcom", "2002-06-02", "Platform", "1", "Scuttle Town & its genie.\n\nRisky Boots invades."),
            gamelistInfo(xml.byteInputStream(), "Shantae.zip"),
        )
    }

    @Test
    fun `an entry without metadata gives nulls and an unknown path gives null`() {
        assertEquals(GameInfo(null, null, null, null, null, null), gamelistInfo(xml.byteInputStream(), "Dead Cells.app"))
        assertNull(gamelistInfo(xml.byteInputStream(), "Super Metroid.sfc"))
    }

    @Test
    fun `release dates become ISO dates or null`() {
        assertEquals("2002-06-02", isoDate("20020602T000000"))
        assertEquals("2002-06-02", isoDate("20020602"))
        assertNull(isoDate(""))
        assertNull(isoDate("2002"))
        assertNull(isoDate("not-a-date-time"))
    }
}
