package com.clintonium.es_de_overlay.core

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

/** A setting in ES-DE's es_settings.xml the switcher needs, by its name there. */
enum class SwitcherNeed(val setting: String) {
    EVENT_SCRIPTS("CustomEventScripts"),
    BROWSING_EVENTS("CustomEventScriptsBrowsing"),
    DEBUG_MODE("DebugMode"),
    LAST_PLAYED("CollectionSystemsAuto"),
    QUICK_SYSTEM_SELECT("QuickSystemSelect"),
}

/**
 * What [settingsXml], ES-DE's es_settings.xml, still lacks for the switcher: the three settings that make ES-DE log
 * each game it selects, the Last Played collection among its automatic ones, and a Quick System Select that isn't
 * disabled, so [Step.NEXT_SYSTEM] has a press. Missing settings count as off, but a missing Quick System Select keeps
 * ES-DE's default, which isn't disabled.
 */
fun switcherNeeds(settingsXml: String): List<SwitcherNeed> = SwitcherNeed.entries.filterNot {
    val value = esSetting(settingsXml, it.setting)
    when (it) {
        SwitcherNeed.LAST_PLAYED -> value?.split(',')?.contains("recent") == true
        SwitcherNeed.QUICK_SYSTEM_SELECT -> value != "disabled"
        else -> value == "true"
    }
}

/** One press [Steering] asks for, or how it ended. */
enum class Step { DOWN, UP, NEXT_SYSTEM, LAUNCH, FAIL }

/**
 * Gets ES-DE's cursor onto [recents]' game [target], one press at a time, by what ES-DE logs after each press, and
 * gives [Step.LAUNCH] only with that game's own path just logged, whichever list it's in.
 * A game list logs only its selected ROM, with no word on which list it is, so Last Played is taken to be the list
 * where two of [recents] (ROM path endings, in list order) turn up one after the other on a press down; any other
 * list is left for the next system. A short list of recent games can pass for it (three Android games played in a
 * row), so the first press there that lands off the expected row moves on as well.
 */
class Steering(private val recents: List<String>, private val target: Int, private val maxPresses: Int = 120) {
    /** Row of Last Played the cursor should be on, once the list looks like Last Played. */
    private var row = -1
    /** Row of [recents] last seen in the list ES-DE is on, or -1. */
    private var seen = -1
    private var last = Step.FAIL
    private var presses = 0

    /** The step after ES-DE logged [selected] for the last one; null to start. [Step.FAIL] when it logged nothing. */
    fun next(selected: String?): Step {
        if (selected == null) return if (presses == 0) press(Step.DOWN) else Step.FAIL
        val at = recents.indexOfFirst(selected::endsWith)
        when {
            at == target -> return Step.LAUNCH
            row >= 0 && at == row -> {}
            last == Step.DOWN && row < 0 && seen >= 0 && at == seen + 1 -> row = at
            row < 0 && at >= 0 -> {
                seen = at
                return press(Step.DOWN)
            }
            else -> {
                row = -1
                seen = -1
                return press(Step.NEXT_SYSTEM)
            }
        }
        val step = if (target > row) Step.DOWN else Step.UP
        row += if (step == Step.DOWN) 1 else -1
        return press(step)
    }

    private fun press(step: Step): Step {
        if (++presses > maxPresses) return Step.FAIL
        last = step
        return step
    }
}
