package com.clintonium.esderecorder.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GamelistTest {
    // Shaped like the Nova's gamelists/androidgames/gamelist.xml, plus entity and duplicate cases.
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
}
