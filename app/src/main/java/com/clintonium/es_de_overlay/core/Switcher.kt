package com.clintonium.es_de_overlay.core

import android.view.KeyEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A played game: [path] is relative to `<ROMs>/<system>/`, [lastPlayed] ES-DE's raw `YYYYMMDDTHHMMSS`. */
data class RecentGame(val system: String, val path: String, val name: String, val lastPlayed: String) {
    /** How the ROM's path ends in ES-DE's log, whatever the ROMs folder is. */
    val logSuffix get() = "/$system/$path"
}

/** The gamelist.xml tags [recentGames] reads; a fixed-string search for these is several times faster than a pattern. */
val RECENT_TAGS = listOf("game", "folder", "path", "name", "lastplayed", "nogamecount", "hidden")

private val FIELD = Regex("""^([^/]+)/gamelist\.xml:\s*<(${RECENT_TAGS.joinToString("|")})>(?:(.*)</\2>)?""")
private val ENTITIES = mapOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&amp;" to "&")
private fun unescape(text: String) = Regex("&(lt|gt|quot|apos|amp);").replace(text) { ENTITIES.getValue(it.value) }

/**
 * ES-DE's Last Played list, newest first and at most [max] long as ES-DE keeps it, from the lines a fixed-string
 * `grep -H` for the [RECENT_TAGS] prints over every system's gamelist.xml in the gamelists folder (one tag a line,
 * prefixed `<system>/gamelist.xml:`). Folders carry their newest game's time and are left out, as are hidden
 * games and those excluded from the game count, which ES-DE keeps out of its collections.
 */
fun recentGames(lines: Sequence<String>, max: Int = 50): List<RecentGame> {
    val games = ArrayList<RecentGame>()
    var system = ""
    var fields = HashMap<String, String>()
    fun flush() {
        val path = fields["path"]?.removePrefix("./")
        val played = fields["lastplayed"]
        if ("game" in fields && path != null && played != null && fields["nogamecount"] != "true" && fields["hidden"] != "true") {
            games += RecentGame(system, path, fields["name"] ?: path.substringAfterLast('/').substringBeforeLast('.'), played)
        }
        fields = HashMap()
    }
    for (line in lines) {
        val (file, tag, value) = FIELD.find(line)?.destructured ?: continue
        if (tag == "game" || tag == "folder") {
            flush()
            system = file
        }
        fields.putIfAbsent(tag, unescape(value.trim()))
    }
    flush()
    return games.sortedWith(compareByDescending<RecentGame> { it.lastPlayed }.thenBy { it.name.lowercase() }).take(max)
}

/**
 * The ROM path in the line ES-DE logs when a game list's selection changes, else null. ES-DE logs it only with
 * custom event scripts, their browsing events and Debug Mode on, e.g.
 * `Scripting::fireEvent(): game-select "/storage/7BEF-E760/ROMs/snes/Super Mario World.zip" "Super Mario World" "snes" "…"`.
 */
fun selectedRom(line: String): String? =
    line.substringAfter("Scripting::fireEvent(): game-select \"", "").substringBefore("\" \"").ifEmpty { null }

private val SELECT_NAMES = Regex("""Scripting::fireEvent\(\): game-select ".*" "(\S+)" "([^"]+)"$""")
private val LAUNCH_NAMES = Regex("""Launching game ".*" from system "(.+) \((\S+)\)"\.\.\.$""")

/**
 * Each system's full name by its short name, from the [lines] of ES-DE's log that give both: a game list's
 * `game-select "<path>" "<name>" "<short>" "<full>"` and `Launching game "<name>" from system "<full> (<short>)"...`.
 */
fun systemNames(lines: Sequence<String>): Map<String, String> = lines.mapNotNull { line ->
    when {
        "game-select" in line -> SELECT_NAMES.find(line)?.destructured?.let { (short, full) -> short to full }
        "Launching game" in line -> LAUNCH_NAMES.find(line)?.destructured?.let { (full, short) -> short to full }
        else -> null
    }
}.toMap()

private val LAST_PLAYED = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss")

/**
 * [games] with ES-DE's latest launch, the ROM [path] of [system], first, as Last Played has it once the game is left:
 * a list read before the game can't show that yet. Moved up, it's played at [now]; unchanged when not there, or when
 * first unless [justPlayed] says the list was read before this play.
 */
fun playedFirst(games: List<RecentGame>, system: String, path: String, now: LocalDateTime, justPlayed: Boolean = false): List<RecentGame> {
    val at = games.indexOfFirst { it.system == system && it.path == path }
    if (at < 0 || (at == 0 && !justPlayed)) return games
    return games.toMutableList().apply { add(0, removeAt(at).copy(lastPlayed = now.format(LAST_PLAYED))) }
}

/** Which of a game's pictures the switcher shows: an emulator's save-state picture, one kept when it was quit, or the scraped image. */
enum class PictureSource { SAVED, LEFT, SCRAPED }

/**
 * The line under [game]'s title: its system's full name from [names], else its short name, then when it was last
 * played, as today or yesterday with the time, else the ISO date, then Saved or Left for the picture [source] shown.
 * Only the system and source show when ES-DE's time won't read.
 */
fun detailLine(game: RecentGame, names: Map<String, String>, today: LocalDate, source: PictureSource = PictureSource.SCRAPED): String {
    val system = names[game.system] ?: game.system
    val shown = when (source) {
        PictureSource.SAVED -> " · Saved"
        PictureSource.LEFT -> " · Left"
        PictureSource.SCRAPED -> ""
    }
    val played = runCatching { LocalDateTime.parse(game.lastPlayed, LAST_PLAYED) }.getOrNull() ?: return system + shown
    val time = played.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    val day = when (played.toLocalDate()) {
        today -> "today, $time"
        today.minusDays(1) -> "yesterday, $time"
        else -> played.toLocalDate().toString()
    }
    return "$system · Played $day$shown"
}

/**
 * [game]'s save-state picture among RetroArch's [states] (when written, in epoch seconds, and path), else null.
 * RetroArch names a state after the ROM, in a folder per core, so games of one name on two systems can find pictures
 * in two folders: then it's the one written nearest before the game was last played, which ES-DE sets, in [zone]'s
 * time, as the game ends. Pictures all in one folder are one core's, so the newest is the game's whatever its time.
 */
fun statePicture(states: List<Pair<Long, String>>, game: RecentGame, zone: ZoneId): Pair<Long, String>? {
    val base = game.path.substringAfterLast('/').substringBeforeLast('.')
    val candidates = states.filter { it.second.substringAfterLast('/').startsWith("$base.state") }
    if (candidates.distinctBy { it.second.substringBeforeLast('/') }.size <= 1) return candidates.maxByOrNull { it.first }
    val played = runCatching { LocalDateTime.parse(game.lastPlayed, LAST_PLAYED).atZone(zone).toEpochSecond() }.getOrNull() ?: return null
    return candidates.filter { it.first <= played }.maxByOrNull { it.first }
}

/**
 * Where a picture the overlay keeps for a game goes, relative to one of its own stores: `<system>/<ROM path>.jpg`,
 * [path] being relative to `<ROMs>/<system>/`. Null for a [system] or [path] that would reach outside the store (an
 * absolute path, `..`, `.` or an empty segment), since both come from files in shared storage.
 */
fun keptPicturePath(system: String, path: String): String? {
    val parts = "$system/$path".split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    return "$system/$path.jpg"
}

/**
 * The newest of [pictures] (when written, in epoch seconds, and path) written from [from] to [to], a game's session;
 * else null. For an emulator that names its save-state pictures by the disc, not the ROM.
 */
fun sessionPicture(pictures: List<Pair<Long, String>>, from: Long, to: Long): Pair<Long, String>? =
    pictures.filter { it.first in from..to }.maxByOrNull { it.first }

/**
 * Which picture a game shows: its save-state picture, written at [saved], when the emulator [resumes] from that state;
 * else the newer of it and the one kept when the game was quit, at [kept] (epoch seconds, null for none), the save
 * state at a tie; else the scraped image. A game force-stopped after the state resumes from the state, not where it
 * was left.
 */
fun pictureSource(saved: Long?, kept: Long?, resumes: Boolean = false): PictureSource = when {
    saved != null && (resumes || kept == null || saved >= kept) -> PictureSource.SAVED
    kept != null -> PictureSource.LEFT
    else -> PictureSource.SCRAPED
}

/**
 * The part of a [width] × [height] picture inside plain-black bars baked into it, as a screen capture of a game drawn
 * narrower or shorter than the screen has, as `[left, top, right, bottom]` (right and bottom exclusive). [dark] says
 * whether the pixel at (x, y) is plain black; a line of pixels is a bar when every sampled one is. Bars count only when
 * even on both sides, within 1% of the size, so a dark scene isn't cut, and never take more than half the picture.
 */
fun contentBounds(width: Int, height: Int, dark: (Int, Int) -> Boolean): IntArray {
    // How many lines from one edge are bars, sampling about 64 points along each.
    fun bars(lines: Int, along: Int, line: (Int) -> Int, isDark: (Int, Int) -> Boolean): Int {
        val step = maxOf(1, along / 64)
        return (0 until lines / 4).firstOrNull { n -> (0 until along step step).any { !isDark(line(n), it) } } ?: (lines / 4)
    }
    fun even(a: Int, b: Int, size: Int) = maxOf(a, b) - minOf(a, b) <= maxOf(2, size / 100)
    val left = bars(width, height, { it }) { x, y -> dark(x, y) }
    val right = bars(width, height, { width - 1 - it }) { x, y -> dark(x, y) }
    val top = bars(height, width, { it }) { y, x -> dark(x, y) }
    val bottom = bars(height, width, { height - 1 - it }) { y, x -> dark(x, y) }
    val (l, r) = if (even(left, right, width)) left to right else 0 to 0
    val (t, b) = if (even(top, bottom, height)) top to bottom else 0 to 0
    return intArrayOf(l, t, width - r, height - b)
}

/**
 * The size to show a [width] × [height] picture at: one over twice as wide as tall is a frame of an emulator's wide
 * pixels (RetroArch saves Nestopia's NTSC-filtered NES frames 602 × 224), shown at 4:3 with its height stretched;
 * any other as it is.
 */
// ponytail: RetroArch saves no aspect with a state's picture; reading its aspect setting and the core's would fix the narrower ones too (SNES's 256 × 224 shown 4:3).
fun displaySize(width: Int, height: Int): Pair<Int, Int> = if (width > 2 * height) width to width * 3 / 4 else width to height

/** A setting in ES-DE's es_settings.xml the switcher needs, by its name there. */
enum class SwitcherNeed(val setting: String) {
    EVENT_SCRIPTS("CustomEventScripts"),
    BROWSING_EVENTS("CustomEventScriptsBrowsing"),
    DEBUG_MODE("DebugMode"),
    LAST_PLAYED("CollectionSystemsAuto"),
}

/**
 * What [settingsXml], ES-DE's es_settings.xml, still lacks for the switcher: the three settings that make ES-DE log
 * each game it selects, and the Last Played collection among its automatic ones. Missing settings count as off.
 */
fun switcherNeeds(settingsXml: String): List<SwitcherNeed> = SwitcherNeed.entries.filterNot {
    val value = esSetting(settingsXml, it.setting)
    if (it == SwitcherNeed.LAST_PLAYED) value?.split(',')?.contains("recent") == true else value == "true"
}

/**
 * Keys for steering ES-DE, as Android key codes from ES-DE's keyboard map: one game on and back in a game list, one
 * system on and back from a game list (null where Quick System Select gives none), and one system on and back in
 * the system view.
 */
data class SteerKeys(
    val nextGame: Int,
    val previousGame: Int,
    val nextSystem: Int?,
    val previousSystem: Int?,
    val nextInSystemView: Int,
    val previousInSystemView: Int,
) {
    /** The key for [step]: Backspace is ES-DE's B and Enter its A. [Step.NEXT_SYSTEM] and [Step.PREVIOUS_SYSTEM] only when there's one. */
    fun key(step: Step): Int = when (step) {
        Step.NEXT_GAME -> nextGame
        Step.PREVIOUS_GAME -> previousGame
        Step.NEXT_SYSTEM -> nextSystem!!
        Step.PREVIOUS_SYSTEM -> previousSystem!!
        Step.SYSTEM_VIEW -> KeyEvent.KEYCODE_DEL
        Step.NEXT_IN_SYSTEM_VIEW -> nextInSystemView
        Step.PREVIOUS_IN_SYSTEM_VIEW -> previousInSystemView
        Step.OPEN, Step.LAUNCH, Step.FAIL -> KeyEvent.KEYCODE_ENTER
    }
}

// One entry on and back in a view drawn as [layout]: a grid's right runs on to the next row, so it walks in order.
private fun moves(layout: Layout) =
    if (layout == Layout.TEXT_LIST || layout == Layout.VERTICAL_CAROUSEL) KeyEvent.KEYCODE_DPAD_DOWN to KeyEvent.KEYCODE_DPAD_UP
    else KeyEvent.KEYCODE_DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_LEFT

/**
 * The [SteerKeys] for a game list drawn as [gamelist] and a system view drawn as [systemView], with ES-DE's Quick
 * System Select setting [quickSystemSelect] (null when unset). ES-DE gives left/right to system changes only where a
 * game list doesn't move with them; the shoulders are Page Up and Down, the right trigger End. The left trigger is
 * Home, which `input keyevent` doesn't get to ES-DE, so the triggers give no previous system. ES-DE's default and its
 * fallback for a value it doesn't know is left/right, else the shoulders.
 */
fun steerKeys(gamelist: Layout, systemView: Layout, quickSystemSelect: String?): SteerKeys {
    val (nextGame, previousGame) = moves(gamelist)
    val free = nextGame == KeyEvent.KEYCODE_DPAD_DOWN
    val sides: Pair<Int?, Int?> = KeyEvent.KEYCODE_DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_LEFT
    val shoulders: Pair<Int?, Int?> = KeyEvent.KEYCODE_PAGE_DOWN to KeyEvent.KEYCODE_PAGE_UP
    val triggers: Pair<Int?, Int?> = KeyEvent.KEYCODE_MOVE_END to null
    val none: Pair<Int?, Int?> = null to null
    val (nextSystem, previousSystem) = when (quickSystemSelect) {
        "disabled" -> none
        "leftright" -> if (free) sides else none
        "shoulders" -> shoulders
        "triggers" -> triggers
        "leftrighttriggers" -> if (free) sides else triggers
        else -> if (free) sides else shoulders
    }
    val (nextInSystemView, previousInSystemView) = moves(systemView)
    return SteerKeys(nextGame, previousGame, nextSystem, previousSystem, nextInSystemView, previousInSystemView)
}

/**
 * What ES-DE logs on selecting: a game list's ROM path with the short name of the game's own system, or the short name
 * of the system the system view is on.
 */
sealed interface Selection {
    data class Game(val path: String, val system: String? = null) : Selection
    data class System(val name: String) : Selection
}

/** Last Played's short name in ES-DE's system view. */
const val LAST_PLAYED_SYSTEM = "recent"

/**
 * The selection in a line of ES-DE's log, else null: a `game-select "<path>" "<name>" "<system>" "<full name>"`, or the
 * system view's `Scripting::fireEvent(): system-select "recent" "last played" "recent" ""`.
 */
fun selection(line: String): Selection? = selectedRom(line)?.let { Selection.Game(it, line.substringAfter("game-select \"").split("\" \"").getOrNull(2)) }
    ?: line.substringAfter("Scripting::fireEvent(): system-select \"", "").substringBefore('"').ifEmpty { null }?.let(Selection::System)

/**
 * How many steps ES-DE's lists are from Last Played by the next-system key ([ahead], by the system a list's selection
 * names), and how many lists there are ([size]), as walks that reached Last Played found them. Without a size the
 * longest distance known stands for it.
 */
data class SystemRing(val ahead: Map<String, Int> = emptyMap(), val size: Int? = null) {
    /** Whether Last Played is nearer from [system]'s list by the previous-system key; false when unknown. */
    fun backward(system: String?): Boolean {
        val distance = system?.let(ahead::get) ?: return false
        return distance * 2 > (size ?: (ahead.values.max() + 1))
    }

    /**
     * This ring once a walk ([backward] or not) reached Last Played after leaving lists of the systems [passed], in
     * order (null for one whose system wasn't logged). A walk back from a known place gives the size. A system seen
     * at two distances, in the walk or against what was known, is dropped as unknown, and the size with it.
     */
    fun learned(passed: List<String?>, backward: Boolean): SystemRing {
        val moves = passed.size
        val lists = if (backward) passed.firstOrNull()?.let(ahead::get)?.plus(moves) ?: return this else size
        val seen = HashMap<String, Int?>()
        passed.forEachIndexed { i, system ->
            if (system == null) return@forEachIndexed
            val distance = if (backward) lists!! - (moves - i) else moves - i
            seen[system] = if (system in seen && seen[system] != distance) null else distance
        }
        val known = ahead.toMutableMap()
        var changed = false
        for ((system, distance) in seen) {
            val was = known[system]
            when {
                distance == null -> known.remove(system)
                was != null && was != distance -> {
                    known.remove(system)
                    changed = true
                }
                else -> known[system] = distance
            }
        }
        return SystemRing(known, lists.takeUnless { changed || it != null && !backward && moves >= it })
    }
}

/**
 * A press [Steering] asks for, by what it does in ES-DE: one game on or back, one system on or back from a game list,
 * B to the system view, one system on or back there, A there to open the list; or how it ended.
 */
enum class Step {
    NEXT_GAME, PREVIOUS_GAME, NEXT_SYSTEM, PREVIOUS_SYSTEM, SYSTEM_VIEW, NEXT_IN_SYSTEM_VIEW, PREVIOUS_IN_SYSTEM_VIEW, OPEN, LAUNCH, FAIL,
}

/**
 * Gets ES-DE's cursor onto [recents]' game [target], one press at a time, by what ES-DE logs after each press, and
 * gives [Step.LAUNCH] only with that game's own path just logged, whichever list it's in.
 * A game list logs only its selected ROM, with no word on which list it is, so Last Played is taken to be the list
 * where two of [recents] (ROM path endings, in list order) turn up one after the other on a press to the next game;
 * any other list is left: for the next system with [canChangeSystem], else by B for the system view. A short list of
 * recent games can pass for Last Played (three Android games played in a row), so the first press there that lands off
 * the expected row moves on as well. The system view names its system, so a list opened on Last Played's name is
 * known to be it, and is opened only with that name just logged for a press of the walk's own.
 * Lists are walked the way [ring] has Last Played nearer, back only with [canGoBack]; [learned] is the ring as this
 * walk found it, once it launches from Last Played.
 */
class Steering(
    private val recents: List<String>,
    private val target: Int,
    private val canChangeSystem: Boolean = true,
    private val canGoBack: Boolean = false,
    private val ring: SystemRing = SystemRing(),
    private val maxPresses: Int = 120,
) {
    var learned: SystemRing? = null
        private set
    /** Row of Last Played the cursor should be on, once the list is taken for Last Played. */
    private var row = -1
    /** Row of [recents] last seen in the list ES-DE is on, or -1. */
    private var seen = -1
    /** The list was opened on Last Played's name. */
    private var known = false
    /** B has been sent for the system view, which a folder's B doesn't reach yet. */
    private var leaving = false
    /** Started on Last Played in the system view: one system on, then back to open it. */
    private var returning = false
    private val systems = HashSet<String>()
    /** The system of the list ES-DE is on, as its last selection named it. */
    private var current: String? = null
    /** The systems of the lists left on the way, in order. */
    private val passed = mutableListOf<String?>()
    /** Walking back, once the first list is left. */
    private var backward: Boolean? = null
    private var last = Step.FAIL
    private var presses = 0

    /** The first step, from [start], the selection ES-DE logged last before steering, if any. It is never A. */
    fun begin(start: Selection?): Step {
        if (start !is Selection.System) return press(Step.NEXT_GAME)
        returning = start.name == LAST_PLAYED_SYSTEM
        if (returning) return press(Step.NEXT_IN_SYSTEM_VIEW)
        systems += start.name
        current = start.name
        return onward(inSystemView = true)
    }

    /** The step after ES-DE logged [selected] for the last one. */
    fun next(selected: Selection): Step = when (selected) {
        is Selection.System -> inSystemView(selected.name.also { current = it })
        is Selection.Game -> inGameList(recents.indexOfFirst(selected.path::endsWith).also { current = selected.system })
    }

    // One list on toward Last Played, the way chosen on leaving the first: from a game list by its key, else B for
    // the system view, which doesn't change the list.
    private fun onward(inSystemView: Boolean): Step {
        val back = backward ?: (canGoBack && ring.backward(current)).also { backward = it }
        if (!inSystemView && !canChangeSystem) {
            leaving = true
            return press(Step.SYSTEM_VIEW)
        }
        passed += current
        return press(
            when {
                inSystemView -> if (back) Step.PREVIOUS_IN_SYSTEM_VIEW else Step.NEXT_IN_SYSTEM_VIEW
                else -> if (back) Step.PREVIOUS_SYSTEM else Step.NEXT_SYSTEM
            },
        )
    }

    private fun inSystemView(name: String): Step {
        row = -1
        seen = -1
        leaving = false
        known = false
        return when {
            name == LAST_PLAYED_SYSTEM -> press(Step.OPEN).also { known = true }
            // Round the systems once without Last Played.
            !systems.add(name) -> Step.FAIL
            returning -> press(Step.PREVIOUS_IN_SYSTEM_VIEW)
            else -> onward(inSystemView = true)
        }
    }

    private fun inGameList(at: Int): Step {
        // Two of the recent games one after the other on a press to the next: taken for Last Played.
        val confirmed = last == Step.NEXT_GAME && row < 0 && seen >= 0 && at == seen + 1
        when {
            at == target -> {
                if (known || row >= 0 || confirmed) learned = ring.learned(passed, backward == true)
                row = at
                last = Step.LAUNCH
                return Step.LAUNCH
            }
            // B from a folder went up a folder.
            leaving -> return press(Step.SYSTEM_VIEW)
            known -> if (at >= 0) row = at
            // A nudge off the target: the list's neighbours are Last Played's.
            last == Step.LAUNCH && row >= 0 && abs(at - target) == 1 -> row = at
            row >= 0 && at == row -> {}
            confirmed -> row = at
            row < 0 && at >= 0 -> {
                seen = at
                return press(Step.NEXT_GAME)
            }
            else -> {
                row = -1
                seen = -1
                return onward(inSystemView = false)
            }
        }
        val step = if (target > row) Step.NEXT_GAME else Step.PREVIOUS_GAME
        row += if (step == Step.NEXT_GAME) 1 else -1
        return press(step)
    }

    private fun press(step: Step): Step {
        if (++presses > maxPresses) return Step.FAIL
        last = step
        return step
    }
}

/**
 * Walks ES-DE with [walk] from [start], the selection ES-DE logged last before steering: [press] sends a step's key
 * and gives what ES-DE logged for it, null for nothing; [settle] gives a selection ES-DE logs a moment after the
 * target's, as a nudged D-pad makes, else null; [launch] sends A on the target. False when the walk fails.
 */
fun steer(walk: Steering, start: Selection?, press: (Step) -> Selection?, settle: () -> Selection?, launch: () -> Boolean): Boolean {
    var step = walk.begin(start)
    while (true) {
        val logged = when (step) {
            Step.FAIL -> return false
            Step.LAUNCH -> settle() ?: return launch()
            else -> press(step) ?: return false
        }
        step = walk.next(logged)
    }
}

/**
 * A launch the switcher picked or pressed at [pressed] (uptime ms), followed through ES-DE's log: [retry] when it's
 * the launch pressed again, [launch] once ES-DE has logged it (at [launchedAt]), [played] once its game has been seen
 * in front, [backAt] once ES-DE has been seen in front again after it, [ended] once ES-DE has logged that game's end.
 * ES-DE logs the end anywhere from a tenth to over half a second after it's back in front.
 */
data class Pick(
    val pressed: Long,
    val retry: Boolean = false,
    val launch: Launch? = null,
    val launchedAt: Long = 0,
    val played: Boolean = false,
    val backAt: Long = 0,
    val ended: Boolean = false,
)

// Steering and then ES-DE's launch screen take a few seconds each; a launch logged later than this after the pick isn't its.
private const val CLAIM_MS = 15_000L

/**
 * Folds one es_log.txt line, read at [now], into [pick], and says whether a game it launched has just ended. The
 * first launch ES-DE logs soon after the press is the pick's; any other is a hand launch, which ends following the pick
 * (null). At the pick's game-end ES-DE has written the game to its gamelist, and it's an end if the game was
 * [Pick.played]: a launch whose game never came up ES-DE takes to be running until a key is pressed, which logs its end.
 */
fun followPick(pick: Pick, line: String, now: Long): Pair<Pick?, Boolean> {
    nextLaunch(null, line)?.let { logged ->
        return (if (pick.launch == null && now - pick.pressed <= CLAIM_MS) pick.copy(launch = logged, launchedAt = now) else null) to false
    }
    val launch = pick.launch ?: return pick to false
    if (pick.ended) return pick to false
    if (!gameEnd(line)) return pick.copy(launch = nextLaunch(launch, line)) to false
    return (if (pick.played) pick.copy(ended = true) else null) to pick.played
}

// RetroArch, running when it's sent another game, quits within a fraction of a second instead, and ES-DE logs nothing;
// a RetroArch started afresh for the game, had it quit before, is up well within the first figure, and one that's
// loading the game stays.
private const val GONE_FROM_MS = 300L
private const val GONE_UNTIL_MS = 5_000L

/**
 * At [now], with ES-DE in front: the pick pressed again (a new pick, the retry) when its launch failed, else null. A
 * launch failed when, a moment after ES-DE logged it, its game hasn't come up and its RetroArch, which was running as
 * the switcher's game was picked ([runningBefore]), has quit: it's not among RetroArch's packages running now
 * ([runningNow], null when they can't be read). Only once: the retry is never pressed again.
 */
fun retryPick(pick: Pick, now: Long, runningBefore: () -> Set<String>, runningNow: () -> Set<String>?): Pick? {
    val pkg = pick.launch?.pkg ?: return null
    if (pick.retry || pick.played || now - pick.launchedAt !in GONE_FROM_MS..GONE_UNTIL_MS || pkg !in runningBefore()) return null
    val running = runningNow() ?: return null
    return if (pkg in running) null else Pick(now, retry = true)
}

// ES-DE logged a game's end up to 0.57 s after it was back in front on the Nova.
private const val END_MS = 3_000L

/**
 * At [now]: whether [pick] is to be looked at each moment. Once ES-DE is back in front after its game, until ES-DE
 * logs the game's end, for a while. Before, while [retryPick] may still judge it: it isn't the retry, its game hasn't come up,
 * and one of RetroArch's packages was running as it was picked ([runningBefore]); not yet launched but soon after the
 * pick, or launched in that RetroArch no longer ago than a failed launch is told in.
 */
fun watchesPick(pick: Pick, now: Long, runningBefore: () -> Set<String>): Boolean {
    // ES-DE back in front: its log of the game's end, which can come over half a second after, is waited for.
    if (pick.played) return pick.backAt > 0 && !pick.ended && now - pick.backAt <= END_MS
    if (pick.retry) return false
    val before = runningBefore().ifEmpty { return false }
    val launch = pick.launch ?: return now - pick.pressed <= CLAIM_MS
    return launch.pkg in before && now - pick.launchedAt <= GONE_UNTIL_MS
}
