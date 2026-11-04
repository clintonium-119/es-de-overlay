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
        		<lastplayed>20261102T201500</lastplayed>
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
    fun `games list paths without the dot-slash with names and last played`() {
        val games = gamelistGames(xml.byteInputStream())
        assertEquals(5, games.size)
        assertEquals(GamelistGame("TMNT Shredder's Revenge.app", "TMNT Shredder's Revenge", null), games[1])
        assertEquals(GamelistGame("Shantae.zip", "Shantae", "20261102T201500"), games[4])
    }

    @Test
    fun `a game without a name gives null and one without a path is left out`() {
        val xml = "<gameList><game><path>./Untitled.zip</path><name></name></game><game><name>No Path</name></game></gameList>"
        assertEquals(listOf(GamelistGame("Untitled.zip", null, null)), gamelistGames(xml.byteInputStream()))
    }

    @Test
    fun `a gamelist after an alternative emulator block still parses`() {
        // As ES-DE writes it once a system's alternative emulator is set (the Nova's nes gamelist).
        val xml = """
            <?xml version="1.0"?>
            <alternativeEmulator>
            	<label>Nestopia UE</label>
            </alternativeEmulator>
            <gameList>
            	<game>
            		<path>./Dragon Warrior.nes</path>
            		<name>Dragon Warrior</name>
            		<lastplayed>20261031T141608</lastplayed>
            	</game>
            </gameList>
        """.trimIndent()
        assertEquals(listOf(GamelistGame("Dragon Warrior.nes", "Dragon Warrior", "20261031T141608")), gamelistGames(xml.byteInputStream()))
        assertEquals(listOf("Dragon Warrior.nes"), gamelistPaths(xml.byteInputStream(), "Dragon Warrior"))
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
