package com.clintonium.es_de_overlay

import android.util.Log
import android.view.KeyEvent
import com.clintonium.es_de_overlay.core.Combo
import com.clintonium.es_de_overlay.core.PACKAGE_NAME
import com.clintonium.es_de_overlay.core.comboScanCodes
import com.clintonium.es_de_overlay.core.gamepad
import com.clintonium.es_de_overlay.core.hotkeyCombo
import com.clintonium.es_de_overlay.core.pressCommand
import com.clintonium.es_de_overlay.core.retroarchOn
import com.clintonium.es_de_overlay.core.retroarchSettings
import com.clintonium.es_de_overlay.core.scanCodes

/**
 * The hotkeys of the RetroArch [pkg] that ES-DE launched a game in, as its retroarch.cfg had them at the launch, pressed
 * as the player's controller on [node] through Shizuku: RetroArch ignores Android's `input` command for them. The
 * presses go to whatever has the keys, so they're only made with RetroArch in front and no overlay window focused.
 */
class RetroArchHotkeys private constructor(
    val pkg: String,
    settings: Map<String, String>,
    private val node: String,
    codes: Map<String, Int>,
) {
    private val shaderToggle = hotkeyCombo(settings, "shader_toggle")?.let { comboScanCodes(it, codes, KeyEvent::keyCodeToString) }
    private val exit = hotkeyCombo(settings, "exit_emulator")?.let { comboScanCodes(it, codes, KeyEvent::keyCodeToString) }
    private val exitPresses = if (retroarchOn(settings, "quit_press_twice")) 2 else 1
    // ponytail: twice RetroArch's hotkey block delay at 60 fps, and at least the 150 ms the Nova test held; the knob if a
    // slow core misses a hotkey.
    private val holdMs = maxOf(150, (settings["input_hotkey_block_delay"]?.toIntOrNull() ?: 5).coerceIn(0, 60) * 2000 / 60)

    /** Whether Exit is on the controller. */
    val exits = exit != null
    /** Whether the overlay turns the shader off and on: it was on as RetroArch started, and Shader toggle is on the controller. */
    val togglesShader = retroarchOn(settings, "video_shader_enable") && shaderToggle != null
    /**
     * Whether RetroArch saves a state, with its own picture, when the overlay ends it: Auto Save State is on and Exit
     * is on the controller. Without Exit it's force-stopped, and saves nothing.
     */
    val savesOnExit = exits && retroarchOn(settings, "savestate_auto_save")

    /** Off the main thread: presses Exit (twice when RetroArch asks for that); whether the presses were made. */
    fun pressExit() = exit?.let { press(it, exitPresses) } == true

    /** Off the main thread: presses Shader toggle; whether the press was made. */
    fun toggleShader() = shaderToggle?.let { press(it, 1) } == true

    private fun press(keys: Combo, times: Int): Boolean {
        val command = pressCommand(node, keys, holdMs, times) ?: return false
        return ShizukuShell.output(command, timeoutSeconds = 5) != null
    }

    companion object {
        // ponytail: waits set from the Nova; the knobs if another device's RetroArch is slower.
        /** After the menu closes, until RetroArch takes the controller's keys again. */
        const val KEYS_BACK_MS = 300L
        /** After a shader toggle, until RetroArch's frames are drawn without it. */
        const val CLEAN_MS = 100L
        /** After Exit, for RetroArch to save and quit before it's force-stopped. */
        const val EXIT_MS = 5_000L

        /**
         * Off the main thread: [pkg]'s hotkeys, read through Shizuku from the retroarch.cfg ES-DE passes it, Android's
         * input device list and the gamepad's key layout; null when [pkg] isn't RetroArch or one of them can't be read.
         */
        fun read(pkg: String): RetroArchHotkeys? {
            if (!pkg.startsWith("com.retroarch") || !PACKAGE_NAME.matches(pkg) || !ShizukuShell.ready) return null
            fun text(command: String) = ShizukuShell.output(command)?.decodeToString()
            val cfg = text("cat " + ShizukuShell.quote("/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg"))
            val pad = text("dumpsys input")?.let(::gamepad)
            val layout = pad?.let { text("cat " + ShizukuShell.quote(it.keyLayout)) }
            if (cfg == null || pad == null || layout == null) {
                Log.i(TAG, "retroarch: no hotkeys (config read: ${cfg != null}, gamepad: $pad)")
                return null
            }
            return RetroArchHotkeys(pkg, retroarchSettings(cfg), pad.node, scanCodes(layout)).also {
                Log.i(TAG, "retroarch: exit ${it.exit}, shader toggle ${it.shaderToggle} on ${pad.node}, hold ${it.holdMs} ms, " +
                    "toggles shader ${it.togglesShader}, saves on exit ${it.savesOnExit}")
            }
        }
    }
}
