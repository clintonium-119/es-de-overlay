package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwitcherTest {
    @Test
    fun `recent games are newest first, without folders, hidden or uncounted entries`() {
        val lines = """
            snes/gamelist.xml:	<folder>
            snes/gamelist.xml:		<path>./01) All but the Best</path>
            snes/gamelist.xml:		<lastplayed>20261117T220545</lastplayed>
            snes/gamelist.xml:	<game>
            snes/gamelist.xml:		<path>./Super Mario World.zip</path>
            snes/gamelist.xml:		<name>Super Mario World</name>
            snes/gamelist.xml:		<lastplayed>20261117T220545</lastplayed>
            snes/gamelist.xml:	<game>
            snes/gamelist.xml:		<path>./Never Played.zip</path>
            snes/gamelist.xml:		<name>Never Played</name>
            androidapps/gamelist.xml:	<game>
            androidapps/gamelist.xml:		<path>./Obtainium.app</path>
            androidapps/gamelist.xml:		<name>Obtainium</name>
            androidapps/gamelist.xml:		<nogamecount>true</nogamecount>
            androidapps/gamelist.xml:		<lastplayed>20261117T160209</lastplayed>
            gbc/gamelist.xml:	<game>
            gbc/gamelist.xml:		<path>./Hidden.zip</path>
            gbc/gamelist.xml:		<hidden>true</hidden>
            gbc/gamelist.xml:		<lastplayed>20261117T120000</lastplayed>
            gbc/gamelist.xml:	<game>
            gbc/gamelist.xml:		<path>./02) Hacks/Tom &amp; Jerry.zip</path>
            gbc/gamelist.xml:		<lastplayed>20261118T090000</lastplayed>
        """.trimIndent().lineSequence()
        assertEquals(
            listOf(
                RecentGame("gbc", "02) Hacks/Tom & Jerry.zip", "Tom & Jerry", "20261118T090000"),
                RecentGame("snes", "Super Mario World.zip", "Super Mario World", "20261117T220545"),
            ),
            recentGames(lines),
        )
        assertEquals(1, recentGames(lines, max = 1).size)
    }

    @Test
    fun `a game-select line gives the ROM path and other lines nothing`() {
        val line = """Nov 18 11:56:30 Debug:  Scripting::fireEvent(): game-select "/storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip" "Easyland" "snes" "Nintendo SNES (Super Nintendo)""""
        assertEquals("/storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip", selectedRom(line))
        assertNull(selectedRom("""Nov 18 11:56:39 Debug:  Window::logInput(Xbox Wireless Controller): Button 14, isMappedTo=right, value=1"""))
        assertNull(selectedRom("""Nov 17 22:05:45 Debug:  Scripting::fireEvent(): game-end "/storage/ROMs/snes/Super\ Mario\ World.zip" "Super Mario World" "snes" "x""""))
    }

    private val recents = listOf("/snes/Super Mario World.zip", "/gbc/Shantae.zip", "/gb/Dr. Mario.zip", "/snes/02) Hacks/Easyland.zip")

    /** ES-DE as [Steering] sees it: a ring of game lists, each with its own cursor, logging the entry a press lands on. */
    private class FakeEsde(val lists: List<List<String>>, var list: Int, val cursors: IntArray) {
        var launched: String? = null
        val selected get() = "/storage/ROMs" + lists[list][cursors[list]]

        fun run(steering: Steering): Step {
            var logged: String? = null
            while (true) {
                when (val step = steering.next(logged)) {
                    Step.DOWN -> cursors[list] = (cursors[list] + 1) % lists[list].size
                    Step.UP -> cursors[list] = Math.floorMod(cursors[list] - 1, lists[list].size)
                    Step.NEXT_SYSTEM -> list = (list + 1) % lists.size
                    Step.LAUNCH -> return step.also { launched = selected }
                    Step.FAIL -> return step
                }
                logged = selected
            }
        }
    }

    private val snes = listOf("/snes/01) All but the Best", "/snes/Super Mario World.zip", "/snes/Zelda.zip")
    private val gb = listOf("/gb/Dr. Mario.zip", "/gb/Tetris.zip")

    @Test
    fun `from the top of Last Played it goes down to the target and launches it`() {
        val esde = FakeEsde(listOf(recents, snes, gb), 0, intArrayOf(0, 0, 0))
        assertEquals(Step.LAUNCH, esde.run(Steering(recents, 3)))
        assertEquals("/storage/ROMs/snes/02) Hacks/Easyland.zip", esde.launched)
    }

    @Test
    fun `from the bottom of Last Played it goes round and back up to the first`() {
        val esde = FakeEsde(listOf(recents, snes, gb), 0, intArrayOf(3, 0, 0))
        assertEquals(Step.LAUNCH, esde.run(Steering(recents, 0)))
        assertEquals("/storage/ROMs/snes/Super Mario World.zip", esde.launched)
    }

    @Test
    fun `from another system it walks to Last Played, past a list whose cursor is on a recent game`() {
        // The SNES list's cursor is on Super Mario World and the Game Boy list's on Dr. Mario: both recent, neither list is Last Played.
        val esde = FakeEsde(listOf(gb, snes, recents), 0, intArrayOf(0, 1, 2))
        assertEquals(Step.LAUNCH, esde.run(Steering(recents, 1)))
        assertEquals(2, esde.list)
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
    }

    @Test
    fun `it fails without launching when Last Played isn't found or nothing is logged`() {
        val esde = FakeEsde(listOf(gb, snes), 0, intArrayOf(0, 0))
        assertEquals(Step.FAIL, esde.run(Steering(recents, 1)))
        assertNull(esde.launched)
        val steering = Steering(recents, 1)
        assertEquals(Step.DOWN, steering.next(null))
        assertEquals(Step.FAIL, steering.next(null))
    }

    @Test
    fun `a short list of recent games that passes for Last Played is left at the first unexpected row`() {
        // Found on the Nova: three Android games, all recent, two of them neighbours in both lists.
        val played = listOf("/snes/Super Mario World.zip", "/gbc/Shantae.zip", "/androidgames/TMNT.app", "/androidgames/Dead Cells.app", "/androidgames/Metroid.app")
        val android = listOf("/androidgames/Dead Cells.app", "/androidgames/Metroid.app", "/androidgames/TMNT.app")
        val esde = FakeEsde(listOf(android, gb, played), 0, intArrayOf(2, 0, 0))
        assertEquals(Step.LAUNCH, esde.run(Steering(played, 1)))
        assertEquals(2, esde.list)
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
    }

    @Test
    fun `the target is launched from its own system's list when the cursor lands on it there`() {
        val esde = FakeEsde(listOf(gb, snes, recents), 0, intArrayOf(0, 1, 0))
        assertEquals(Step.LAUNCH, esde.run(Steering(recents, 0)))
        assertEquals(1, esde.list)
        assertEquals("/storage/ROMs/snes/Super Mario World.zip", esde.launched)
    }
}
