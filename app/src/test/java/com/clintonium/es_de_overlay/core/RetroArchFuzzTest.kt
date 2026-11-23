package com.clintonium.es_de_overlay.core

import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Timeout

/** RetroArch's config, read from shared storage, so any text; the input list and key layouts too, to be safe. */
class RetroArchFuzzTest {
    /** Any text read as each of the three never throws, and the press built from it is only sendevent and sleep. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun hotkeyPress(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        val settings = retroarchSettings(text)
        retroarchOn(settings, "savestate_auto_save")
        val pad = gamepad(text)
        val codes = scanCodes(text)
        for (name in listOf("shader_toggle", "exit_emulator")) {
            val combo = hotkeyCombo(settings, name) ?: continue
            // Keycodes straight from the config, any Int, when the layout has no line for them.
            val keys = comboScanCodes(combo, codes) { "KEYCODE_$it" } ?: combo
            val holdMs = settings["input_hotkey_block_delay"]?.toIntOrNull() ?: 0
            for (node in listOfNotNull(pad?.node, text.lineSequence().firstOrNull())) {
                pressCommand(node, keys, holdMs, holdMs)?.let { assertTrue(isPressCommand(it), it) }
            }
        }
    }

    /** Any text as the state search's lines never throws working out a shape, for any core and frame. */
    @FuzzTest(maxDuration = "10m")
    @Timeout(10)
    fun aspect(input: ByteArray) {
        val text = String(input, Charsets.UTF_8)
        val lines = text.lines()
        val look = retroarchConfig(lines)
        val size = (input.firstOrNull()?.toInt() ?: 0) and 0x3ff
        for (core in listOf("Snes9x", "Genesis Plus GX", "Nestopia", "PicoDrive", "Stella", "FinalBurn Neo", "DOSBox-pure", "Beetle PSX HW", "SwanStation", "Beetle Saturn", "Mupen64Plus-Next", lines.first())) {
            look.aspect(core, lines.last(), lines.getOrElse(1) { "" }, size + 1, (size xor 0x155) + 1, 4 / 3.0)
        }
    }
}
