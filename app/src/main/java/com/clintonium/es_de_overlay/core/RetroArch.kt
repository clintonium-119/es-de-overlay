package com.clintonium.es_de_overlay.core

// RetroArch's hotkeys, pressed as the player's controller: RetroArch takes its hotkey-enable button only from the
// physical controller, so the presses are written to the controller's input node. The combos come from the running
// RetroArch's retroarch.cfg, a file in shared storage, so nothing read from it reaches the shell but numbers.

private val SETTING = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"([^"]*)"\s*$""")

/** The `name = "value"` settings of a retroarch.cfg. */
fun retroarchSettings(cfg: String): Map<String, String> =
    cfg.lineSequence().mapNotNull { SETTING.find(it)?.destructured?.let { (k, v) -> k to v } }.toMap()

/** A hotkey press: [hold] held, if there is one, while [tap] is tapped; Android keycodes, or scan codes on the node. */
data class Combo(val hold: Int?, val tap: Int) {
    /** Whether [keyCode] going down, with the keys [held] down, presses this combo of Android keycodes. */
    fun pressedBy(keyCode: Int, held: Set<Int>) = keyCode == tap && (hold == null || hold in held)
}

/**
 * The controller combo for RetroArch's hotkey [name] (`shader_toggle`, `exit_emulator`): the hotkey-enable button held,
 * if one is bound, and the hotkey's button; null when the hotkey has no button (`"nul"`).
 */
fun hotkeyCombo(settings: Map<String, String>, name: String): Combo? {
    val tap = settings["input_${name}_btn"]?.toIntOrNull() ?: return null
    return Combo(settings["input_enable_hotkey_btn"]?.toIntOrNull(), tap)
}

/** Whether the switch [name] is on: only for `"true"`. */
fun retroarchOn(settings: Map<String, String>, name: String) = settings[name] == "true"

/** The retroarch.cfg settings [RetroArchConfig.aspect] reads. */
val ASPECT_SETTINGS = listOf(
    "aspect_ratio_index", "video_aspect_ratio", "video_aspect_ratio_auto", "custom_viewport_width", "custom_viewport_height",
    "game_specific_options", "global_core_options",
)

/**
 * RetroArch's settings for the shape it shows games at: retroarch.cfg's [main] ones, and those of each override and
 * core-options file under its config folder, by the file's path there (`Snes9x/Snes9x.opt`).
 */
data class RetroArchConfig(val main: Map<String, String>, val files: Map<String, Map<String, String>>) {
    /**
     * The shape (width over height) RetroArch shows [core]'s [width]×[height] frames of [game] (the content's file name
     * without its extension, as its state is named) from the content folder [folder] at, on a [screen]-shaped screen;
     * null where it's the frame's own. Worked out as RetroArch 1.22.2's `video_driver_set_aspect_ratio` does, with the
     * core's, folder's and game's overrides on top of retroarch.cfg, and the core's options from the game's file, else
     * the folder's, else the core's own, else the global one.
     */
    fun aspect(core: String, game: String, folder: String, width: Int, height: Int, screen: Double): Double? {
        val settings = main + listOf(core, folder, game).mapNotNull { files["$core/$it.cfg"] }.fold(emptyMap()) { a, b -> a + b }
        fun on(name: String, default: Boolean) = settings[name]?.let { it == "true" } ?: default
        val options = (
            if (on("global_core_options", false)) null
            else (if (on("game_specific_options", true)) files["$core/$game.opt"] ?: files["$core/$folder.opt"] else null) ?: files["$core/$core.opt"]
            ) ?: files["retroarch-core-options.cfg"].orEmpty()
        val provided = coreAspect(core, options, game, width, height)
        // Index 22, Core provided, is Android's default.
        return when (val index = settings["aspect_ratio_index"]?.toIntOrNull() ?: 22) {
            in FIXED_ASPECTS.indices -> FIXED_ASPECTS[index]
            // Config: its ratio; a negative one means the core's with Auto on, else the frame's.
            20 -> (settings["video_aspect_ratio"]?.toDoubleOrNull() ?: 1.3333).let { if (it >= 0) it else provided.takeIf { on("video_aspect_ratio_auto", false) } }
            // Square pixels.
            21 -> null
            22 -> provided
            23 -> {
                val w = settings["custom_viewport_width"]?.toIntOrNull() ?: 0
                val h = settings["custom_viewport_height"]?.toIntOrNull() ?: 0
                if (w > 0 && h > 0) w.toDouble() / h else provided
            }
            24 -> screen
            // RetroArch takes an index out of range as the first.
            else -> FIXED_ASPECTS[0]
        }
    }
}

// ponytail: integer scaling is taken to keep the aspect, as its default Y axis does; its X-axis modes and Rotation are
// left out, and so is Force aspect off (RetroArch keeps the aspect again whenever a core changes its geometry).
/** RetroArch's fixed aspect choices, `aspect_ratio_index` 0 to 19, as gfx/video_driver.c's `aspectratio_lut` has them. */
private val FIXED_ASPECTS = doubleArrayOf(
    4 / 3.0, 16 / 9.0, 16 / 10.0, 16 / 15.0, 21 / 9.0, 1.0, 2.0, 3 / 2.0, 3 / 4.0, 4.0,
    9 / 16.0, 5 / 4.0, 6 / 5.0, 7 / 9.0, 8 / 3.0, 8 / 7.0, 19 / 12.0, 19 / 14.0, 30 / 17.0, 32 / 9.0,
)

/** The core options [coreAspect] reads, so the overlay's search can pick them out of the core-options files. */
val CORE_ASPECT_OPTIONS = listOf(
    "snes9x_aspect", "snes9x_region",
    "genesis_plus_gx_aspect_ratio", "genesis_plus_gx_region_detect", "genesis_plus_gx_blargg_ntsc_filter",
    "nestopia_aspect", "nestopia_force_system",
    "picodrive_aspect", "picodrive_region",
    "stella_ntsc_aspect", "stella_pal_aspect", "stella_crop_hoverscan",
    "dosbox_pure_aspect_correction",
    "swanstation_GPU_Renderer", "swanstation_Display_AspectRatio", "swanstation_Display_CustomAspectRatioNumerator",
    "swanstation_Display_CustomAspectRatioDenominator", "swanstation_Display_CropMode",
    "beetle_saturn_horizontal_overscan",
    "mupen64plus-rdp-plugin",
) + listOf("beetle_psx_hw_", "beetle_psx_").flatMap { prefix ->
    listOf(
        "renderer", "aspect_ratio", "widescreen_hack", "widescreen_hack_aspect_ratio", "crop_overscan",
        "initial_scanline", "last_scanline", "initial_scanline_pal", "last_scanline_pal",
    ).map { prefix + it }
}

/** A ratio written `w:h`; RetroArch cores' 21:9 is really 64:27. */
private fun ratio(text: String): Double? =
    if (text == "21:9") 64 / 27.0 else text.split(':').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 2 && it[1] > 0 }?.let { it[0] / it[1] }

// Pixel shapes (a pixel's width over its height) as the cores work them out: the console's dot clock against
// 13.5 MHz NTSC (135e6/11/2 ÷ 21477272.727/4) or 14.75 MHz PAL (7375000 ÷ 21281370/4) sampling, and the Mega Drive's
// 320-dot mode at 4/5 of that.
private const val NTSC_PAR = 8 / 7.0
private const val PAL_PAR = 1.386189
private const val NTSC_PAR_H40 = 32 / 35.0
private const val PAL_PAR_H40 = 1.108951

/**
 * The shape [core] gives a [width]×[height] frame of [game] (as the content's file is named) for RetroArch's Core
 * provided aspect, from its [options], as each core's libretro source works it out; null where that's the frame's own
 * shape, or for a core not listed here.
 */
fun coreAspect(core: String, options: Map<String, String>, game: String, width: Int, height: Int): Double? {
    fun option(name: String, default: String) = options[name] ?: default
    fun number(name: String, default: Int) = options[name]?.toIntOrNull() ?: default
    // ponytail: the cores choose NTSC or PAL from the ROM's header; the file name's region tags stand in for it.
    fun pal(forced: Boolean?) = forced ?: palByName(game)
    return when (core) {
        "Snes9x" -> {
            // Its sums use the 256-dot width and a single field's height, whatever the frame's size.
            val h = if (height > 239) height / 2.0 else height.toDouble()
            val pal = pal(mapOf("pal" to true, "ntsc" to false)[option("snes9x_region", "auto")])
            when (option("snes9x_aspect", "4:3")) {
                "4:3" -> 4 / 3.0
                "4:3 scaled" -> 4 * (478 - h) / (3 * 256)
                "uncorrected" -> 256 / h
                "ntsc" -> 256 * NTSC_PAR / h
                "pal" -> 256 * PAL_PAR / h
                "auto" -> 256 * (if (pal) PAL_PAR else NTSC_PAR) / h
                else -> null
            }
        }
        "Genesis Plus GX" -> {
            // ponytail: its NTSC filter widens the frame by an amount not worked back here; the frame's shape stands.
            if (option("genesis_plus_gx_blargg_ntsc_filter", "disabled") != "disabled") return null
            val h = if (height > 240) height / 2.0 else height.toDouble()
            val pal = pal(mapOf("pal" to true, "ntsc-u" to false, "ntsc-j" to false)[option("genesis_plus_gx_region_detect", "auto")])
            fun par(pal: Boolean) = width * (if (width >= 320) (if (pal) PAL_PAR_H40 else NTSC_PAR_H40) else if (pal) PAL_PAR else NTSC_PAR) / h
            when (option("genesis_plus_gx_aspect_ratio", "auto")) {
                // ponytail: a Game Gear's 160 × 144 screen is 6:5 pixels only with its overscan and extra lines off, the defaults.
                "auto" -> if (width == 160 && height == 144) 4 / 3.0 else par(pal)
                "NTSC PAR" -> par(false)
                "PAL PAR" -> par(true)
                "4:3" -> 4 / 3.0
                else -> null
            }
        }
        "Nestopia" -> {
            // Its NTSC filter, on by default, makes frames 602 wide, less any side crop.
            val filtered = width > 300
            val w = if (filtered) width * 3 / 7.0 else width.toDouble()
            val pal = pal(mapOf("pal" to true, "dendy" to true, "ntsc" to false, "famicom" to false)[option("nestopia_force_system", "auto")])
            when (option("nestopia_aspect", "auto")) {
                "auto" -> w * (if (pal) PAL_PAR else NTSC_PAR) / height
                "ntsc" -> w * NTSC_PAR / height
                "pal" -> w * PAL_PAR / height
                "4:3" -> 4 / 3.0
                "5:4" -> 5 / 4.0
                "uncorrected" -> (if (filtered) width / 2.0 else width.toDouble()) / height
                else -> null
            }
        }
        "PicoDrive" -> {
            val pal = pal(mapOf("Europe" to true, "Japan PAL" to true, "US" to false, "Japan NTSC" to false)[option("picodrive_region", "Auto")])
            val tvHeight = if (height <= 144) 144 else if (pal) 240 else 224
            when (option("picodrive_aspect", "PAR")) {
                "4/3" -> 4 / 3.0 * tvHeight / height
                "CRT" -> 1.29911 * tvHeight / height
                else -> null
            }
        }
        "Stella", "Stella 2023" -> {
            // ponytail: Stella takes PAL from the game's frame rate; the name stands in for it.
            val pal = palByName(game)
            val par = option(if (pal) "stella_pal_aspect" else "stella_ntsc_aspect", "par")
                .let { if (it == "par") (if (pal) 1.0396417 else 6 / 7.0) else it.toDoubleOrNull()?.div(100) } ?: return null
            val crop = if (option("stella_crop_hoverscan", "disabled") in setOf("enabled", "true")) 8 else 0
            par * 2 * (160 - crop) / height
        }
        // ponytail: each game's own aspect comes from FinalBurn Neo's driver table; nearly all are 4:3 or, upright, 3:4.
        // RetroArch 1.22.2 saves an upright game's frame unturned, so the switcher shows the screen read as it quit instead.
        "FinalBurn Neo" -> if (height > width) 3 / 4.0 else 4 / 3.0
        "DOSBox-pure" -> when (option("dosbox_pure_aspect_correction", "false")) {
            // Square pixels, doubled or halved where the frame is far from the mode's shape, taken here as 4:3.
            "false" -> (width.toDouble() / height).let { square -> (4 / 3.0 / square).let { if (it > 1.66) square * 2 else if (it < 0.6) square / 2 else null } }
            // 4:3 for the standard modes below 640 × 480, which are square.
            "true", "doublescan" -> if (width >= 640 && height >= 480) null else 4 / 3.0
            else -> 4 / 3.0
        }
        "Beetle PSX HW", "Beetle PSX" -> {
            val prefix = if (core == "Beetle PSX HW") "beetle_psx_hw_" else "beetle_psx_"
            if (core == "Beetle PSX HW" && option("${prefix}renderer", "hardware") != "software") return null
            val pal = palByName(game)
            val lines = if (pal) number("${prefix}last_scanline_pal", 287) - number("${prefix}initial_scanline_pal", 0) + 1
            else number("${prefix}last_scanline", 239) - number("${prefix}initial_scanline", 0) + 1
            // ponytail: each game sets how many GPU clocks of a line it shows, 2560 to 2800, and the crop follows it;
            // the common 2560 is taken.
            val crop = if (option("${prefix}crop_overscan", "smart") == "disabled") 1.0 else 2560 / 2800.0
            if (option("${prefix}widescreen_hack", "disabled") == "enabled") ratio(option("${prefix}widescreen_hack_aspect_ratio", "16:9"))
            else when (option("${prefix}aspect_ratio", "corrected")) {
                "4:3" -> 4 / 3.0
                "16:9" -> 16 / 9.0
                "corrected" -> 4 / 3.0 * crop * (if (pal) 288 else 240) / lines
                "ntsc" -> 4 / 3.0 * crop * 240 / lines
                else -> null
            }
        }
        "SwanStation" -> {
            if (option("swanstation_GPU_Renderer", "Auto") != "Software") return null
            when (val setting = option("swanstation_Display_AspectRatio", "4:3")) {
                "Custom" -> number("swanstation_Display_CustomAspectRatioNumerator", 4).toDouble() /
                    number("swanstation_Display_CustomAspectRatioDenominator", 3).coerceAtLeast(1)
                "PAR 1:1" -> null
                "Auto", "Native" -> {
                    val pal = setting == "Native" && palByName(game)
                    val h = if (height > 288) height / 2.0 else height.toDouble()
                    // ponytail: the game's own display window, taken as the common 2560 clocks wide, and the frame's lines.
                    if (option("swanstation_Display_CropMode", "Borders") == "None") 4 / 3.0
                    else 4 / 3.0 * (2560 / (if (pal) 2795.0 else 2800.0)) / (h / (if (pal) 288 else 240))
                }
                else -> ratio(setting)
            }
        }
        "Beetle Saturn" -> {
            val h = if (height > 288) height / 2.0 else height.toDouble()
            (if (palByName(game)) 27 / 28.0 else 6 / 7.0) * (352 - number("beetle_saturn_horizontal_overscan", 0)) / h
        }
        // Angrylion draws in software, 640 wide whatever the lines; GLideN64 and ParaLLEl are the hardware ones below.
        "Mupen64Plus-Next" -> if (option("mupen64plus-rdp-plugin", "gliden64") == "angrylion") 4 / 3.0 else null
        // The rest: cores whose frame is its own shape (Gambatte, mGBA, Beetle Lynx, Beetle NeoPop, ScummVM), and those
        // drawn in hardware (Flycast), whose state picture RetroArch reads off the screen in the shape shown.
        else -> null
    }
}

private val TAGS = Regex("""\(([^)]*)\)""")
private val PAL_TAGS = setOf("Europe", "Australia", "Germany", "France", "Spain", "Italy", "Sweden", "Netherlands", "UK", "Scandinavia", "E", "PAL")
private val NTSC_TAGS = setOf("USA", "Japan", "World", "Korea", "Brazil", "Asia", "Canada", "U", "J", "JU", "UE", "JE", "JUE", "NTSC")

/** Whether the file name [game] says it's a PAL release: a European region tag and no American or Japanese one. */
fun palByName(game: String): Boolean {
    val regions = TAGS.findAll(game).flatMap { it.groupValues[1].split(',').map(String::trim) }.toSet()
    return regions.any { it in PAL_TAGS } && regions.none { it in NTSC_TAGS }
}

private val OVERRIDE = Regex("""^override (.+\.(?:cfg|opt)):(.*)$""")

/**
 * [RetroArchConfig] from lines of the overlay's own state search: `cfg <setting line>` from retroarch.cfg, and
 * `override <file>:<setting line>` from `grep -H` over the files under its config folder.
 */
fun retroarchConfig(lines: List<String>): RetroArchConfig = RetroArchConfig(
    retroarchSettings(lines.filter { it.startsWith("cfg ") }.joinToString("\n") { it.removePrefix("cfg ") }),
    lines.mapNotNull { OVERRIDE.find(it)?.destructured }.groupBy({ it.component1() }, { it.component2() })
        .mapValues { (_, settings) -> retroarchSettings(settings.joinToString("\n")) },
)

/** RetroArch's packages among the process names `ps -A -o NAME` lists, where an app's other processes end in `:name`. */
fun retroarchProcesses(ps: String): Set<String> =
    ps.lineSequence().map { it.trim().substringBefore(':') }.filter { it.startsWith("com.retroarch") && PACKAGE_NAME.matches(it) }.toSet()

/** A controller's input node and its key-layout file. */
data class Gamepad(val node: String, val keyLayout: String)

private val NODE = Regex("/dev/input/event\\d{1,4}")
private val DEVICE = Regex("""^-?\d+: .*""")

/** The first gamepad in `dumpsys input`'s device list that has an input node and a key-layout file. */
fun gamepad(dumpsysInput: String): Gamepad? {
    var classes = emptyList<String>()
    var path = ""
    for (line in dumpsysInput.lineSequence().map(String::trim)) {
        when {
            DEVICE.matches(line) -> {
                classes = emptyList()
                path = ""
            }
            line.startsWith("Classes:") -> classes = line.substringAfter(':').split('|').map(String::trim)
            line.startsWith("Path:") -> path = line.substringAfter(':').trim()
            line.startsWith("KeyLayoutFile:") -> {
                val layout = line.substringAfter(':').trim()
                if ("GAMEPAD" in classes && NODE.matches(path) && layout.isNotEmpty()) return Gamepad(path, layout)
            }
        }
    }
    return null
}

private val KEY = Regex("""^key\s+(\d{1,5})\s+(\S+)\s*(.*)$""")

/**
 * The scan code of each key in a key-layout (.kl) file, by Android's name for it (`BUTTON_SELECT`), the first line
 * naming it winning. Lines for a key only with the function key held (`FUNCTION`) and HID usages are skipped.
 */
fun scanCodes(keyLayout: String): Map<String, Int> {
    val codes = mutableMapOf<String, Int>()
    for (line in keyLayout.lineSequence()) {
        val (code, name, flags) = KEY.find(line.trim())?.destructured ?: continue
        if ("FUNCTION" in flags.split(Regex("\\s+"))) continue
        codes.putIfAbsent(name, code.toInt())
    }
    return codes
}

/** [combo]'s keycodes as scan codes on the controller; [keyName] is `KeyEvent.keyCodeToString`. Null if one has none. */
fun comboScanCodes(combo: Combo, scanCodes: Map<String, Int>, keyName: (Int) -> String): Combo? {
    fun scan(keyCode: Int) = scanCodes[keyName(keyCode).removePrefix("KEYCODE_")]
    val tap = scan(combo.tap) ?: return null
    val hold = combo.hold?.let { scan(it) ?: return null }
    return Combo(hold, tap)
}

// ponytail: a tap of 50 ms spans three frames at 60 fps, so RetroArch's once-a-frame read of the controller sees it.
private const val TAP_MS = 50
private const val BETWEEN_MS = 300

/**
 * The `sh` command that presses [keys] (scan codes) on [node] [times] times: [keys]' hold down, a wait of [holdMs]
 * (longer than RetroArch's hotkey block delay), the tap down and up, the hold up, each followed by a sync event. Null
 * when [node] isn't an input node or a code is negative.
 */
fun pressCommand(node: String, keys: Combo, holdMs: Int, times: Int = 1): String? {
    if (!NODE.matches(node) || keys.tap < 0 || (keys.hold ?: 0) < 0) return null
    fun event(code: Int, value: Int) = listOf("sendevent $node 1 $code $value", "sendevent $node 0 0 0")
    fun sleep(ms: Int) = "sleep ${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"
    val press = buildList {
        keys.hold?.let { addAll(event(it, 1)); add(sleep(holdMs.coerceIn(0, 5_000))) }
        addAll(event(keys.tap, 1))
        add(sleep(TAP_MS))
        addAll(event(keys.tap, 0))
        keys.hold?.let { addAll(event(it, 0)) }
    }
    return (1..times.coerceIn(1, 2)).joinToString("; ${sleep(BETWEEN_MS)}; ") { press.joinToString("; ") }
}
