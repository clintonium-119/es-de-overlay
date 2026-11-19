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

    @Test
    fun `the detail line says today or yesterday with the time, else the date`() {
        val names = mapOf("snes" to "Nintendo SNES (Super Nintendo)")
        val today = java.time.LocalDate.of(2026, 11, 18)
        fun line(played: String) = detailLine(RecentGame("snes", "Easyland.zip", "Easyland", played), names, today)
        assertEquals("Nintendo SNES (Super Nintendo) · Played today, 13:04", line("20261118T130459"))
        assertEquals("Nintendo SNES (Super Nintendo) · Played yesterday, 23:59", line("20261117T235900"))
        assertEquals("Nintendo SNES (Super Nintendo) · Played 2026-11-16", line("20261116T090000"))
        assertEquals("Nintendo SNES (Super Nintendo) · Played 2025-11-18", line("20251118T130400"))
        assertEquals("Nintendo SNES (Super Nintendo)", line("not a time"))
    }

    @Test
    fun `the game just played goes first, played now, and the rest keep their order`() {
        val games = listOf(
            RecentGame("snes", "Super Mario World.zip", "Super Mario World", "20261118T140000"),
            RecentGame("switch", "Mario Kart 8 Deluxe.nsp", "Mario Kart 8 Deluxe", "20261118T134600"),
            RecentGame("gbc", "Zelda DX.zip", "Link's Awakening DX", "20261118T134100"),
        )
        val now = java.time.LocalDateTime.of(2026, 11, 18, 15, 37, 2)
        assertEquals(
            listOf(games[2].copy(lastPlayed = "20261118T153702"), games[0], games[1]),
            playedFirst(games, "gbc", "Zelda DX.zip", now),
        )
        assertEquals(games, playedFirst(games, "snes", "Super Mario World.zip", now))
        assertEquals(games, playedFirst(games, "gbc", "Not Played.zip", now))
    }

    @Test
    fun `even black bars are trimmed and uneven ones or a dark picture are kept`() {
        // A 128 × 96 capture of a game 80 wide, centred: 24 black columns either side.
        fun picture(left: Int, right: Int, top: Int = 0, bottom: Int = 0) = { x: Int, y: Int ->
            x < left || x >= 128 - right || y < top || y >= 96 - bottom
        }
        assertEquals(listOf(24, 0, 104, 96), contentBounds(128, 96, picture(24, 24)).toList())
        assertEquals(listOf(0, 6, 128, 90), contentBounds(128, 96, picture(0, 0, 6, 6)).toList())
        // A dark scene with black down one side only is left whole.
        assertEquals(listOf(0, 0, 128, 96), contentBounds(128, 96, picture(30, 0)).toList())
        // All black: never cut by more than a quarter each side, and both the same.
        assertEquals(listOf(32, 24, 96, 72), contentBounds(128, 96) { _, _ -> true }.toList())
        assertEquals(listOf(0, 0, 128, 96), contentBounds(128, 96) { _, _ -> false }.toList())
    }

    @Test
    fun `an unknown system shows its short name`() {
        val game = RecentGame("tg-cd", "Ys.chd", "Ys", "20261118T080000")
        assertEquals("tg-cd · Played today, 08:00", detailLine(game, emptyMap(), java.time.LocalDate.of(2026, 11, 18)))
    }

    @Test
    fun `game-select and launch lines name systems and other lines don't`() {
        val lines = sequenceOf(
            """Nov 18 11:56:30 Debug:  Scripting::fireEvent(): game-select "/storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip" "Easyland" "snes" "Nintendo SNES (Super Nintendo)"""",
            """Nov 18 12:01:02 Info:   Launching game "Pokémon (Blue)" from system "Nintendo Game Boy (gb)"...""",
            """Nov 18 12:02:00 Debug:  Scripting::fireEvent(): game-select "/storage/ROMs/tg-cd/Ys.chd" "Ys" "tg-cd" "NEC TurboGrafx-CD"""",
            """Nov 18 11:56:39 Debug:  Window::logInput(Xbox Wireless Controller): Button 14, isMappedTo=right, value=1""",
            """Nov 17 22:05:45 Debug:  Scripting::fireEvent(): game-end "/storage/ROMs/snes/Super\ Mario\ World.zip" "Super Mario World" "snes" "x"""",
        )
        assertEquals(
            mapOf("snes" to "Nintendo SNES (Super Nintendo)", "gb" to "Nintendo Game Boy", "tg-cd" to "NEC TurboGrafx-CD"),
            systemNames(lines),
        )
        assertEquals(emptyMap<String, String>(), systemNames(lines.drop(3)))
    }

    /** The Nova's es_settings.xml as read on 2026-11-18, browsing events off. */
    private val nova = """
        <bool name="CustomEventScripts" value="true" />
        <bool name="CustomEventScriptsBrowsing" value="false" />
        <bool name="DebugMode" value="true" />
        <bool name="NavigationSounds" value="false" />
        <string name="CollectionSystemsAuto" value="recent" />
        <string name="QuickSystemSelect" value="leftrightshoulders" />
        <string name="SaveGamelistsMode" value="always" />
    """.trimIndent()
    private val ready = nova.replace("""Browsing" value="false""", """Browsing" value="true""")

    @Test
    fun `the Nova's settings lack only the browsing events and with them lack nothing`() {
        assertEquals(listOf(SwitcherNeed.BROWSING_EVENTS), switcherNeeds(nova))
        assertEquals(emptyList<SwitcherNeed>(), switcherNeeds(ready))
    }

    @Test
    fun `each setting off or absent is the one need reported`() {
        for (need in SwitcherNeed.entries - SwitcherNeed.QUICK_SYSTEM_SELECT) {
            val line = ready.lines().single { """name="${need.setting}"""" in it }
            assertEquals(listOf(need), switcherNeeds(ready.replace(line, "")))
            if ("<bool" in line) assertEquals(listOf(need), switcherNeeds(ready.replace(line, line.replace("true", "false"))))
        }
    }

    @Test
    fun `Last Played counts only as one of the automatic collections`() {
        val with = { names: String -> ready.replace("""value="recent"""", """value="$names"""") }
        assertEquals(emptyList<SwitcherNeed>(), switcherNeeds(with("favorites,recent")))
        assertEquals(listOf(SwitcherNeed.LAST_PLAYED), switcherNeeds(with("favorites")))
        assertEquals(listOf(SwitcherNeed.LAST_PLAYED), switcherNeeds(with("")))
    }

    @Test
    fun `Quick System Select is a need only when disabled`() {
        val with = { mode: String -> ready.replace("leftrightshoulders", mode) }
        assertEquals(listOf(SwitcherNeed.QUICK_SYSTEM_SELECT), switcherNeeds(with("disabled")))
        assertEquals(emptyList<SwitcherNeed>(), switcherNeeds(with("shoulders")))
        assertEquals(emptyList<SwitcherNeed>(), switcherNeeds(ready))
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
