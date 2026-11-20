package com.clintonium.es_de_overlay.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // When a picture was written, in epoch seconds, from the same `YYYYMMDDTHHMMSS` ES-DE uses, in UTC.
    private fun written(time: String) = java.time.LocalDateTime.parse(time, java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss"))
        .toEpochSecond(java.time.ZoneOffset.UTC)

    @Test
    fun `two games of one name each get the picture written nearest before their own last play`() {
        val states = listOf(
            written("20261117T215343") to "/states/Snes9x/Super Mario World.state.png",
            written("20261117T212521") to "/states/Snes9x/Super Mario World.state1.png",
            written("20261118T135900") to "/states/bsnes/Super Mario World.state.auto.png",
            written("20261117T200000") to "/states/Snes9x/Super Mario World 2.state.png",
        )
        val snes = RecentGame("snes", "Super Mario World.zip", "Super Mario World", "20261117T220000")
        val sfc = RecentGame("sfc", "hacks/Super Mario World.sfc", "Super Mario World", "20261118T140000")
        assertEquals(states[0], statePicture(states, snes, java.time.ZoneOffset.UTC))
        assertEquals(states[2], statePicture(states, sfc, java.time.ZoneOffset.UTC))
        // Played before any of them: none is its own.
        assertNull(statePicture(states, snes.copy(lastPlayed = "20261117T120000"), java.time.ZoneOffset.UTC))
    }

    @Test
    fun `pictures in one folder give the newest whatever its time, and no picture gives none`() {
        val states = listOf(
            written("20261117T212521") to "/states/Snes9x/Super Mario World.state1.png",
            written("20261118T140000") to "/states/Snes9x/Super Mario World.state.auto.png",
        )
        val game = RecentGame("snes", "Super Mario World.zip", "Super Mario World", "20261101T090000")
        assertEquals(states[1], statePicture(states, game, java.time.ZoneOffset.UTC))
        assertEquals(states[0], statePicture(states.take(1), game, java.time.ZoneOffset.UTC))
        assertNull(statePicture(states, RecentGame("snes", "Easyland.zip", "Easyland", "20261118T140000"), java.time.ZoneOffset.UTC))
        assertNull(statePicture(emptyList(), game, java.time.ZoneOffset.UTC))
    }

    @Test
    fun `a kept picture's path stays inside the store, and one that would leave it is refused`() {
        val store = java.io.File("/data/user/0/app/files/kept")
        fun inside(path: String) = java.io.File(store, path).canonicalPath.startsWith(store.canonicalPath + "/")
        assertEquals("snes/02) Hacks/Easyland.zip.jpg", keptPicturePath("snes", "02) Hacks/Easyland.zip"))
        assertEquals("scummvm/dig.scummvm.jpg", keptPicturePath("scummvm", "dig.scummvm"))
        for (path in listOf("Easyland.zip", "02) Hacks/Easyland.zip", "a b/c..d/Game (USA).7z")) {
            assertTrue(path, inside(keptPicturePath("snes", path)!!))
        }
        for ((system, path) in listOf(
            "snes" to "../../shared_prefs/prefs.xml",
            "snes" to "02) Hacks/../../x.zip",
            "snes" to "/storage/7BEF-E760/ROMs/snes/Easyland.zip",
            "snes" to "./Easyland.zip",
            "snes" to "02) Hacks//Easyland.zip",
            ".." to "Easyland.zip",
            "../shared_prefs" to "prefs.xml",
            "" to "Easyland.zip",
        )) {
            assertNull("$system/$path", keptPicturePath(system, path))
        }
    }

    @Test
    fun `a picture written inside the session's times is taken, the newest, and one outside is not`() {
        // Tekken 6 on the Nova: launched 11:09:27, its state saved 11:11:55, ES-DE back 11:12:03.
        val launched = written("20261119T160927")
        val back = written("20261119T161203")
        val inside = written("20261119T161155") to "/PSP/PPSSPP_STATE/ULUS10466_1.00_0.jpg"
        val earlier = written("20261119T160500") to "/PSP/PPSSPP_STATE/ULUS10466_1.00_1.jpg"
        val later = written("20261119T161500") to "/PSP/PPSSPP_STATE/NPUH10197_1.00_0.jpg"
        assertEquals(inside, sessionPicture(listOf(earlier, inside, later), launched, back))
        assertEquals(inside, sessionPicture(listOf(inside, inside.first - 60 to "/PSP/PPSSPP_STATE/ULUS10466_1.00_2.jpg"), launched, back))
        assertNull(sessionPicture(listOf(earlier, later), launched, back))
        assertEquals(launched to "edge.jpg", sessionPicture(listOf(launched to "edge.jpg"), launched, back))
    }

    @Test
    fun `the newer of the save-state and kept pictures is shown, the save state at a tie, else the scraped image`() {
        assertEquals(PictureSource.SAVED, pictureSource(saved = 200, kept = 100))
        assertEquals(PictureSource.LEFT, pictureSource(saved = 100, kept = 200))
        assertEquals(PictureSource.SAVED, pictureSource(saved = 100, kept = 100))
        assertEquals(PictureSource.SAVED, pictureSource(saved = 100, kept = null))
        assertEquals(PictureSource.LEFT, pictureSource(saved = null, kept = 100))
        assertEquals(PictureSource.SCRAPED, pictureSource(saved = null, kept = null))
    }

    @Test
    fun `a save state the emulator resumes from beats a newer kept picture`() {
        assertEquals(PictureSource.SAVED, pictureSource(saved = 100, kept = 200, resumes = true))
        assertEquals(PictureSource.LEFT, pictureSource(saved = null, kept = 200, resumes = true))
        assertEquals(PictureSource.SCRAPED, pictureSource(saved = null, kept = null, resumes = true))
    }

    @Test
    fun `the detail line says Saved or Left for the picture shown`() {
        val names = mapOf("snes" to "Nintendo SNES (Super Nintendo)")
        val game = RecentGame("snes", "Easyland.zip", "Easyland", "20261118T130459")
        val today = java.time.LocalDate.of(2026, 11, 18)
        assertEquals("Nintendo SNES (Super Nintendo) · Played today, 13:04 · Saved", detailLine(game, names, today, PictureSource.SAVED))
        assertEquals("Nintendo SNES (Super Nintendo) · Played today, 13:04 · Left", detailLine(game, names, today, PictureSource.LEFT))
        assertEquals("Nintendo SNES (Super Nintendo) · Played today, 13:04", detailLine(game, names, today, PictureSource.SCRAPED))
        assertEquals("Nintendo SNES (Super Nintendo) · Left", detailLine(game.copy(lastPlayed = "?"), names, today, PictureSource.LEFT))
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
    fun `a frame over twice as wide as tall is shown at 4 to 3, others as they are`() {
        // Nestopia's NTSC filter on the Nova, a Game Boy, a SNES, a kept screen.
        assertEquals(602 to 451, displaySize(602, 224))
        assertEquals(160 to 144, displaySize(160, 144))
        assertEquals(256 to 224, displaySize(256, 224))
        assertEquals(1280 to 960, displaySize(1280, 960))
        assertEquals(448 to 224, displaySize(448, 224))
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
        for (need in SwitcherNeed.entries) {
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
    fun `Quick System Select is no need, disabled or not`() {
        assertEquals(emptyList<SwitcherNeed>(), switcherNeeds(ready.replace("leftrightshoulders", "disabled")))
        assertEquals(emptyList<SwitcherNeed>(), switcherNeeds(ready.lines().filterNot { "QuickSystemSelect" in it }.joinToString("\n")))
    }

    @Test
    fun `each layout and Quick System Select value gives the keys ES-DE moves with, or none for a system`() {
        val down = KeyEvent.KEYCODE_DPAD_DOWN
        val up = KeyEvent.KEYCODE_DPAD_UP
        val right = KeyEvent.KEYCODE_DPAD_RIGHT
        val left = KeyEvent.KEYCODE_DPAD_LEFT
        val pageDown = KeyEvent.KEYCODE_PAGE_DOWN
        val pageUp = KeyEvent.KEYCODE_PAGE_UP
        val end = KeyEvent.KEYCODE_MOVE_END
        // Next and previous system from a game list, by Quick System Select value, for a game list moved by up/down
        // (text list, vertical carousel) and by left/right (horizontal carousel, grid), as docs/VERIFIED.md has it.
        val systems = mapOf(
            "leftrightshoulders" to listOf(right to left, pageDown to pageUp),
            "leftrighttriggers" to listOf(right to left, end to null),
            "shoulders" to listOf(pageDown to pageUp, pageDown to pageUp),
            "triggers" to listOf(end to null, end to null),
            "leftright" to listOf(right to left, null to null),
            "disabled" to listOf(null to null, null to null),
            // Unset, and a value ES-DE doesn't know, act as its default.
            null to listOf(right to left, pageDown to pageUp),
            "sideways" to listOf(right to left, pageDown to pageUp),
        )
        for ((qss, expected) in systems) {
            val (upDownLists, leftRightLists) = expected
            for (layout in Layout.entries) {
                val upDown = layout == Layout.TEXT_LIST || layout == Layout.VERTICAL_CAROUSEL
                val keys = steerKeys(layout, Layout.TEXT_LIST, qss)
                assertEquals("$layout $qss", if (upDown) down to up else right to left, keys.nextGame to keys.previousGame)
                assertEquals("$layout $qss", if (upDown) upDownLists else leftRightLists, keys.nextSystem to keys.previousSystem)
            }
        }
        // The system view moves as its own layout does, whatever the game list's.
        assertEquals(down to up, steerKeys(Layout.GRID, Layout.TEXT_LIST, null).let { it.nextInSystemView to it.previousInSystemView })
        assertEquals(down to up, steerKeys(Layout.GRID, Layout.VERTICAL_CAROUSEL, null).let { it.nextInSystemView to it.previousInSystemView })
        assertEquals(right to left, steerKeys(Layout.TEXT_LIST, Layout.HORIZONTAL_CAROUSEL, null).let { it.nextInSystemView to it.previousInSystemView })
        assertEquals(right to left, steerKeys(Layout.TEXT_LIST, Layout.GRID, null).let { it.nextInSystemView to it.previousInSystemView })
    }

    @Test
    fun `the Nova's setup changes system with left and right`() {
        val keys = steerKeys(Layout.TEXT_LIST, Layout.TEXT_LIST, esSetting(nova, "QuickSystemSelect"))
        assertEquals(SteerKeys(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP), keys)
    }

    private val recents = listOf("/snes/Super Mario World.zip", "/gbc/Shantae.zip", "/gb/Dr. Mario.zip", "/snes/02) Hacks/Easyland.zip")

    /**
     * ES-DE as [Steering] sees it: a ring of game lists, each with its own cursor, and the system view over them, as the
     * Nova logged them: a press logs the entry it lands on, or nothing where it doesn't move (a game list's keys in the
     * system view and back). A list with games of several systems is Last Played (`recent`); [depth] is how many
     * folders deep each list's cursor is. [nudges] move the cursor by their amount when steering looks again before A.
     */
    private class FakeEsde(val lists: List<List<String>>, var list: Int, val cursors: IntArray, var systemView: Boolean = false) {
        val names = lists.map { l -> l.map { it.split('/')[1] }.distinct().singleOrNull() ?: LAST_PLAYED_SYSTEM }
        val depth = IntArray(lists.size)
        val nudges = ArrayDeque<Int>()
        val pressed = mutableListOf<Step>()
        var launched: String? = null
        val game get() = lists[list][cursors[list]].let { Selection.Game("/storage/ROMs$it", it.split('/')[1]) }
        val logged get() = if (systemView) Selection.System(names[list]) else game

        private fun move(by: Int) {
            cursors[list] = Math.floorMod(cursors[list] + by, lists[list].size)
        }

        fun press(step: Step): Selection? {
            pressed += step
            when (step) {
                Step.NEXT_GAME, Step.PREVIOUS_GAME -> if (systemView) return null else move(if (step == Step.NEXT_GAME) 1 else -1)
                Step.NEXT_SYSTEM, Step.PREVIOUS_SYSTEM ->
                    if (systemView) return null else list = Math.floorMod(list + if (step == Step.NEXT_SYSTEM) 1 else -1, lists.size)
                Step.SYSTEM_VIEW -> when {
                    systemView -> return null
                    depth[list] > 0 -> return Selection.Game("/storage/ROMs/${names[list]}/01) Folder ${depth[list]--}")
                    else -> systemView = true
                }
                Step.NEXT_IN_SYSTEM_VIEW, Step.PREVIOUS_IN_SYSTEM_VIEW ->
                    if (!systemView) return null else list = Math.floorMod(list + if (step == Step.NEXT_IN_SYSTEM_VIEW) 1 else -1, lists.size)
                // A in a game list launches its game.
                Step.OPEN -> if (systemView) systemView = false else return null.also { launched = game.path }
                Step.LAUNCH, Step.FAIL -> error("$step isn't a press")
            }
            return logged
        }

        fun run(steering: Steering, start: Selection? = null) = steer(
            steering,
            start,
            ::press,
            settle = { nudges.removeFirstOrNull()?.let { move(it); game } },
            launch = { launched = game.path; true },
        )
    }

    private val snes = listOf("/snes/01) All but the Best", "/snes/Super Mario World.zip", "/snes/Zelda.zip")
    private val gb = listOf("/gb/Dr. Mario.zip", "/gb/Tetris.zip")

    @Test
    fun `from the top of Last Played it goes down to the target and launches it`() {
        val esde = FakeEsde(listOf(recents, snes, gb), 0, intArrayOf(0, 0, 0))
        assertTrue(esde.run(Steering(recents, 3)))
        assertEquals("/storage/ROMs/snes/02) Hacks/Easyland.zip", esde.launched)
    }

    @Test
    fun `from the bottom of Last Played it goes round and back up to the first`() {
        val esde = FakeEsde(listOf(recents, snes, gb), 0, intArrayOf(3, 0, 0))
        assertTrue(esde.run(Steering(recents, 0)))
        assertEquals("/storage/ROMs/snes/Super Mario World.zip", esde.launched)
    }

    @Test
    fun `from another system it walks to Last Played, past a list whose cursor is on a recent game`() {
        // The SNES list's cursor is on Super Mario World and the Game Boy list's on Dr. Mario: both recent, neither list is Last Played.
        val esde = FakeEsde(listOf(gb, snes, recents), 0, intArrayOf(0, 1, 2))
        assertTrue(esde.run(Steering(recents, 1)))
        assertEquals(2, esde.list)
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
    }

    @Test
    fun `it fails without launching when Last Played isn't found or nothing is logged`() {
        val esde = FakeEsde(listOf(gb, snes), 0, intArrayOf(0, 0))
        assertFalse(esde.run(Steering(recents, 1)))
        assertNull(esde.launched)
        // In the system view with no line to tell it so, a game list's press logs nothing.
        val lost = FakeEsde(listOf(gb, snes, recents), 0, intArrayOf(0, 0, 0), systemView = true)
        assertFalse(lost.run(Steering(recents, 1)))
        assertEquals(listOf(Step.NEXT_GAME), lost.pressed)
        assertNull(lost.launched)
    }

    @Test
    fun `a short list of recent games that passes for Last Played is left at the first unexpected row`() {
        // Found on the Nova: three Android games, all recent, two of them neighbours in both lists.
        val played = listOf("/snes/Super Mario World.zip", "/gbc/Shantae.zip", "/androidgames/TMNT.app", "/androidgames/Dead Cells.app", "/androidgames/Metroid.app")
        val android = listOf("/androidgames/Dead Cells.app", "/androidgames/Metroid.app", "/androidgames/TMNT.app")
        val esde = FakeEsde(listOf(android, gb, played), 0, intArrayOf(2, 0, 0))
        assertTrue(esde.run(Steering(played, 1)))
        assertEquals(2, esde.list)
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
    }

    @Test
    fun `the target is launched from its own system's list when the cursor lands on it there`() {
        val esde = FakeEsde(listOf(gb, snes, recents), 0, intArrayOf(0, 1, 0))
        assertTrue(esde.run(Steering(recents, 0)))
        assertEquals(1, esde.list)
        assertEquals("/storage/ROMs/snes/Super Mario World.zip", esde.launched)
    }

    @Test
    fun `from the system view on another system it walks to Last Played by name and opens it`() {
        val esde = FakeEsde(listOf(gb, snes, recents), 0, intArrayOf(0, 1, 2), systemView = true)
        assertTrue(esde.run(Steering(recents, 1), start = Selection.System("gb")))
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
        assertEquals(
            listOf(Step.NEXT_IN_SYSTEM_VIEW, Step.NEXT_IN_SYSTEM_VIEW, Step.OPEN, Step.PREVIOUS_GAME),
            esde.pressed,
        )
    }

    @Test
    fun `from the system view on Last Played it steps off and back before opening it`() {
        val esde = FakeEsde(listOf(gb, snes, recents), 2, intArrayOf(0, 1, 0), systemView = true)
        assertTrue(esde.run(Steering(recents, 2), start = Selection.System(LAST_PLAYED_SYSTEM)))
        assertEquals("/storage/ROMs/gb/Dr. Mario.zip", esde.launched)
        assertEquals(Step.OPEN, esde.pressed[2])
        // A line naming Last Played while ES-DE shows a game list: the first press logs nothing, and no A is sent.
        val stale = FakeEsde(listOf(gb, snes, recents), 2, intArrayOf(0, 1, 0))
        assertFalse(stale.run(Steering(recents, 2), start = Selection.System(LAST_PLAYED_SYSTEM)))
        assertNull(stale.launched)
        assertEquals(listOf(Step.NEXT_IN_SYSTEM_VIEW), stale.pressed)
    }

    @Test
    fun `a nudge after the target's line is walked back from, and A follows the target's own line`() {
        // Last Played known by its neighbours.
        val esde = FakeEsde(listOf(recents, snes, gb), 0, intArrayOf(0, 0, 0))
        esde.nudges += listOf(1, -1)
        assertTrue(esde.run(Steering(recents, 2)))
        assertEquals("/storage/ROMs/gb/Dr. Mario.zip", esde.launched)
        assertEquals(listOf(Step.NEXT_GAME, Step.NEXT_GAME, Step.PREVIOUS_GAME, Step.NEXT_GAME), esde.pressed)
        // Last Played opened by name.
        val opened = FakeEsde(listOf(gb, recents), 0, intArrayOf(0, 1), systemView = true)
        opened.nudges += -1
        assertTrue(opened.run(Steering(recents, 1, canChangeSystem = false), start = Selection.System("gb")))
        assertEquals("/storage/ROMs/gbc/Shantae.zip", opened.launched)
        assertEquals(Step.NEXT_GAME, opened.pressed.last())
    }

    @Test
    fun `without a next-system key it leaves other lists through the system view, out of a folder too`() {
        // A grid under Quick System Select's Left/Right: next game is right, and there's no key to change system.
        val keys = steerKeys(Layout.GRID, Layout.TEXT_LIST, "leftright")
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, keys.key(Step.NEXT_GAME))
        assertEquals(KeyEvent.KEYCODE_DEL, keys.key(Step.SYSTEM_VIEW))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, keys.key(Step.NEXT_IN_SYSTEM_VIEW))
        assertEquals(KeyEvent.KEYCODE_ENTER, keys.key(Step.OPEN))
        val esde = FakeEsde(listOf(snes, gb, recents), 0, intArrayOf(2, 0, 3))
        esde.depth[0] = 2
        assertTrue(esde.run(Steering(recents, 1, canChangeSystem = keys.nextSystem != null)))
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
        assertEquals(
            listOf(Step.NEXT_GAME, Step.SYSTEM_VIEW, Step.SYSTEM_VIEW, Step.SYSTEM_VIEW) +
                listOf(Step.NEXT_IN_SYSTEM_VIEW, Step.NEXT_IN_SYSTEM_VIEW, Step.OPEN) +
                listOf(Step.PREVIOUS_GAME, Step.PREVIOUS_GAME),
            esde.pressed,
        )
        assertTrue(Step.NEXT_SYSTEM !in esde.pressed)
    }

    @Test
    fun `a game-select line names its game's system`() {
        val line = """Nov 18 11:56:30 Debug:  Scripting::fireEvent(): game-select "/storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip" "Easyland" "snes" "Nintendo SNES (Super Nintendo)""""
        assertEquals(Selection.Game("/storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip", "snes"), selection(line))
        assertEquals(Selection.System("recent"), selection("""Nov 18 20:49:03 Debug:  Scripting::fireEvent(): system-select "recent" "last played" "recent" """""))
    }

    // Four one-system lists and Last Played, each list's cursor on a game that isn't recent.
    private val a = listOf("/atari2600/Pitfall.zip", "/atari2600/Adventure.zip")
    private val b = listOf("/nes/Metroid.zip", "/nes/Kid Icarus.zip")
    private val c = listOf("/n64/Turok.z64", "/n64/F-Zero X.z64")
    private val d = listOf("/psx/Descent.chd", "/psx/Wipeout.chd")
    private fun ring(vararg lists: List<String>) = lists.toList() + listOf(recents)
    private val lap = SystemRing(mapOf("atari2600" to 4, "nes" to 3, "n64" to 2, "psx" to 1))

    @Test
    fun `a walk learns each list's distance, and a lap from just past Last Played the ring`() {
        val walk = Steering(recents, 1, canGoBack = true)
        val esde = FakeEsde(ring(a, b, c, d), 0, IntArray(5))
        assertTrue(esde.run(walk))
        assertEquals(lap, walk.learned)
        // The longest distance stands for the size, so from there it walks back one list, not round.
        val back = Steering(recents, 1, canGoBack = true, ring = lap)
        val again = FakeEsde(ring(a, b, c, d), 0, IntArray(5))
        assertTrue(again.run(back))
        assertEquals("/storage/ROMs/gbc/Shantae.zip", again.launched)
        assertEquals(1, again.pressed.count { it == Step.PREVIOUS_SYSTEM })
        assertTrue(Step.NEXT_SYSTEM !in again.pressed)
        // A walk back from a known place gives the size.
        assertEquals(lap.copy(size = 5), back.learned)
    }

    @Test
    fun `it takes the shorter way, by the system view too, and only when it can go back`() {
        val known = lap.copy(size = 5)
        fun systemsPressed(start: Int, canGoBack: Boolean = true, canChangeSystem: Boolean = true): List<Step> {
            val esde = FakeEsde(ring(a, b, c, d), start, IntArray(5))
            assertTrue(esde.run(Steering(recents, 1, canChangeSystem, canGoBack, known)))
            assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
            return esde.pressed.filter { it in setOf(Step.NEXT_SYSTEM, Step.PREVIOUS_SYSTEM, Step.NEXT_IN_SYSTEM_VIEW, Step.PREVIOUS_IN_SYSTEM_VIEW) }
        }
        assertEquals(listOf(Step.NEXT_SYSTEM, Step.NEXT_SYSTEM), systemsPressed(2))
        // From the second of five lists, back past the first is two; on would be three.
        assertEquals(List(2) { Step.PREVIOUS_SYSTEM }, systemsPressed(1))
        assertEquals(List(2) { Step.PREVIOUS_IN_SYSTEM_VIEW }, systemsPressed(1, canChangeSystem = false))
        assertEquals(List(3) { Step.NEXT_SYSTEM }, systemsPressed(1, canGoBack = false))
    }

    @Test
    fun `a system seen at two distances is dropped, and an unknown start walks on and arrives`() {
        // A favourites list on a SNES game, then the Game Boy list, then the SNES list itself.
        val favourites = listOf("/snes/Zelda.zip", "/snes/F-Zero.zip")
        val walk = Steering(recents, 1, canGoBack = true, ring = lap)
        val esde = FakeEsde(listOf(favourites, gb, snes, recents), 0, intArrayOf(0, 0, 2, 0))
        assertTrue(esde.run(walk))
        assertEquals("/storage/ROMs/gbc/Shantae.zip", esde.launched)
        assertTrue(Step.PREVIOUS_SYSTEM !in esde.pressed)
        assertEquals(lap.ahead + ("gb" to 2), walk.learned!!.ahead)
    }

    @Test
    fun `a ring that has changed is learned again`() {
        val known = lap.copy(size = 5)
        // A list joins between the N64's and the PlayStation's: from the N64 it's now three to Last Played.
        val grown = known.learned(listOf("n64", "gba", "psx"), backward = false)
        assertEquals(SystemRing(mapOf("atari2600" to 4, "nes" to 3, "gba" to 2, "psx" to 1)), grown)
        assertEquals(3, grown.learned(listOf("n64", "gba", "psx"), backward = false).ahead["n64"])
        // A forward walk longer than the size known also drops it.
        assertNull(known.learned(listOf("x", "atari2600", "nes", "n64", "psx"), backward = false).size)
    }

    /** Aladdin's launch from the Nova's log, as the switcher pressed it, and its end. */
    private val aladdin = listOf(
        """Nov 19 16:52:03 Debug:  Window::logInput(Keyboard): Key Return, isMappedTo=a, value=1""",
        """Nov 19 16:52:06 Info:   Launching game "Aladdin" from system "Sega Genesis (genesis)"...""",
        """Nov 19 16:52:06 Debug:  FileData::launchGame(): Found emulator package "com.retroarch.aarch64"""",
        """Nov 19 16:52:06 Info:   Package: com.retroarch.aarch64""",
        """Nov 19 16:52:06 Info:   Activity: com.retroarch.browser.retroactivity.RetroActivityFuture""",
        """Nov 19 16:52:07 Debug:  Scripting::fireEvent(): game-select "/storage/7BEF-E760/ROMs/genesis/Aladdin.zip" "Aladdin" "genesis" "Sega Genesis"""",
        """Nov 19 16:52:14 Debug:  FileData::setPlayMetadata(): Play time was 8 seconds""",
        """Nov 19 16:52:14 Debug:  Scripting::fireEvent(): game-end "/storage/7BEF-E760/ROMs/genesis/Aladdin.zip" "Aladdin" "genesis" "Sega Genesis"""",
    )
    private val launched = aladdin.dropLast(2)
    private val ended = aladdin.takeLast(2)

    /** Folds [lines], read [readAfter] ms after the pick, into [pick]; gives the pick left and whether the switcher reopens. */
    private fun follow(pick: Pick?, lines: List<String>, readAfter: Long = 5_000): Pair<Pick?, Boolean> {
        var p = pick
        var reopens = false
        for (line in lines) {
            val (next, reopen) = followPick(p ?: break, line, (pick?.pressed ?: 0) + readAfter)
            p = next
            reopens = reopens || reopen
        }
        return p to reopens
    }

    @Test
    fun `a pick follows its own launch, with its package and when it was read`() {
        val (left, reopens) = follow(Pick(1_000), launched)
        assertEquals("Aladdin", left?.launch?.name)
        assertEquals("com.retroarch.aarch64", left?.launch?.pkg)
        assertEquals(6_000L, left?.launchedAt)
        assertFalse(reopens)
    }

    @Test
    fun `the end of a game the pick launched and that came up opens the switcher again`() {
        val (playing, _) = follow(Pick(1_000), launched)
        val (left, reopens) = follow(playing!!.copy(played = true), ended)
        assertNull(left)
        assertTrue(reopens)
    }

    @Test
    fun `the end of a launch whose game never came up, logged at the next key, ends following it with no reopening`() {
        val (playing, _) = follow(Pick(1_000), launched)
        val (left, reopens) = follow(playing, listOf("""Nov 19 21:34:44 Debug:  Window::logInput(Keyboard): Key Return, isMappedTo=a, value=1""") + ended)
        assertNull(left)
        assertFalse(reopens)
    }

    @Test
    fun `a launch after the pick's, by hand, ends following it with no reopening`() {
        val hand = """Nov 19 16:53:30 Info:   Launching game "Sonic Rush" from system "Nintendo DS (nds)"..."""
        val (playing, _) = follow(Pick(1_000), launched)
        val (left, reopens) = follow(playing!!.copy(played = true), listOf(hand) + ended)
        assertNull(left)
        assertFalse(reopens)
    }

    @Test
    fun `a launch logged long after the pick isn't its`() {
        assertNull(follow(Pick(1_000), launched, readAfter = 15_001).first)
        assertEquals("Aladdin", follow(Pick(1_000), launched, readAfter = 15_000).first?.launch?.name)
    }

    @Test
    fun `a game-end before the pick's launch is logged, the failed launch's, is passed over`() {
        val retry = Pick(1_000, retry = true)
        val (left, reopens) = follow(retry, ended + launched)
        assertEquals("Aladdin", left?.launch?.name)
        assertTrue(left!!.retry)
        assertFalse(reopens)
    }

    @Test
    fun `a launch whose game didn't come up and whose RetroArch quit is pressed once more`() {
        val running = setOf("com.retroarch.aarch64")
        val failed = follow(Pick(1_000), launched).first!!
        val at = failed.launchedAt
        val retry = retryPick(failed, at + 1_000, { running }, { emptySet() })
        assertTrue(retry!!.retry)
        assertNull(retry.launch)
        assertEquals(at + 1_000, retry.pressed)
        // Too soon, or too late, to tell.
        assertNull(retryPick(failed, at + 999, { running }, { emptySet() }))
        assertNull(retryPick(failed, at + 5_001, { running }, { emptySet() }))
        // The retry is never pressed again.
        val retried = follow(retry, launched, readAfter = 4_000).first!!
        assertNull(retryPick(retried, retried.launchedAt + 1_000, { running }, { emptySet() }))
    }

    @Test
    fun `a launch isn't pressed again if its game came up, its RetroArch still runs or wasn't running before`() {
        val running = setOf("com.retroarch.aarch64")
        val failed = follow(Pick(1_000), launched).first!!
        val at = failed.launchedAt + 2_000
        assertNull(retryPick(failed.copy(played = true), at, { running }, { emptySet() }))
        assertNull(retryPick(failed, at, { running }, { running }))
        assertNull(retryPick(failed, at, { running }, { null }))
        assertNull(retryPick(failed, at, { emptySet() }, { emptySet() }))
        assertNull(retryPick(failed, at, { setOf("com.retroarch") }, { emptySet() }))
        assertNull(retryPick(Pick(1_000), 3_000, { running }, { emptySet() }))
    }
}
