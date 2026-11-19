package com.clintonium.es_de_overlay.core

// RetroArch's hotkeys, pressed as the player's controller: RetroArch takes its hotkey-enable button only from the
// physical controller, so the presses are written to the controller's input node. The combos come from the running
// RetroArch's retroarch.cfg, a file in shared storage, so nothing read from it reaches the shell but numbers.

private val SETTING = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"([^"]*)"\s*$""")

/** The `name = "value"` settings of a retroarch.cfg. */
fun retroarchSettings(cfg: String): Map<String, String> =
    cfg.lineSequence().mapNotNull { SETTING.find(it)?.destructured?.let { (k, v) -> k to v } }.toMap()

/** A hotkey press: [hold] held, if there is one, while [tap] is tapped; Android keycodes, or scan codes on the node. */
data class Combo(val hold: Int?, val tap: Int)

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
