package com.clintonium.es_de_overlay.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchTest {
    private fun fixture(name: String) = File("../fixtures/retroarch/$name").readText()
    private val nova = retroarchSettings(fixture("retroarch.cfg"))
    private val keyNames = mapOf(97 to "KEYCODE_BUTTON_B", 108 to "KEYCODE_BUTTON_START", 109 to "KEYCODE_BUTTON_SELECT")
    private fun keyName(code: Int) = keyNames[code] ?: code.toString()

    @Test
    fun `the Nova's config gives Select+B for Shader toggle and Select+Start for Exit`() {
        assertEquals(Combo(109, 97), hotkeyCombo(nova, "shader_toggle"))
        assertEquals(Combo(109, 108), hotkeyCombo(nova, "exit_emulator"))
    }

    @Test
    fun `an unbound hotkey gives no combo`() {
        assertNull(hotkeyCombo(nova + ("input_exit_emulator_btn" to "nul"), "exit_emulator"))
        assertNull(hotkeyCombo(nova - "input_shader_toggle_btn", "shader_toggle"))
        // A hat direction isn't a button.
        assertNull(hotkeyCombo(nova + ("input_shader_toggle_btn" to "h0up"), "shader_toggle"))
    }

    @Test
    fun `with no enable button the hotkey's button alone`() {
        assertEquals(Combo(null, 97), hotkeyCombo(nova + ("input_enable_hotkey_btn" to "nul"), "shader_toggle"))
    }

    @Test
    fun `quit_press_twice and save state on exit are on only for true`() {
        assertFalse(retroarchOn(nova, "quit_press_twice"))
        assertTrue(retroarchOn(nova + ("quit_press_twice" to "true"), "quit_press_twice"))
        assertTrue(retroarchOn(nova, "savestate_auto_save"))
        assertFalse(retroarchOn(nova + ("savestate_auto_save" to "false"), "savestate_auto_save"))
        assertFalse(retroarchOn(nova - "savestate_auto_save", "savestate_auto_save"))
        assertFalse(retroarchOn(nova + ("savestate_auto_save" to "TRUE"), "savestate_auto_save"))
    }

    @Test
    fun `settings read the quoted value, last line winning, other lines skipped`() {
        val settings = retroarchSettings("# a comment\nvideo_shader_enable = \"false\"\nnot a setting\nvideo_shader_enable = \"true\"\n")
        assertEquals(mapOf("video_shader_enable" to "true"), settings)
    }

    @Test
    fun `the Nova's dumpsys input gives the gamepad's node and Generic kl`() {
        assertEquals(Gamepad("/dev/input/event7", "/system/usr/keylayout/Generic.kl"), gamepad(fixture("dumpsys_input.txt")))
    }

    @Test
    fun `a list with only keyboards gives no gamepad`() {
        val keyboards = fixture("dumpsys_input.txt").replace("KEYBOARD | GAMEPAD | JOYSTICK", "KEYBOARD")
        assertNull(gamepad(keyboards))
    }

    @Test
    fun `a gamepad without a node or a key layout is passed over`() {
        val list = """
            Devices:
              3: Pad without layout
                Classes: GAMEPAD
                Path: /dev/input/event3
                KeyLayoutFile:
              4: Pad on a virtual path
                Classes: GAMEPAD
                Path: <virtual>
                KeyLayoutFile: /system/usr/keylayout/Generic.kl
              5: Real pad
                Classes: KEYBOARD | GAMEPAD
                Path: /dev/input/event5
                KeyLayoutFile: /vendor/usr/keylayout/Pad.kl
        """.trimIndent()
        assertEquals(Gamepad("/dev/input/event5", "/vendor/usr/keylayout/Pad.kl"), gamepad(list))
    }

    @Test
    fun `Generic kl gives 314, 305 and 315 for Select, B and Start`() {
        val codes = scanCodes(fixture("Generic.kl"))
        assertEquals(314, codes["BUTTON_SELECT"])
        assertEquals(305, codes["BUTTON_B"])
        assertEquals(315, codes["BUTTON_START"])
        // The first of two lines naming a key.
        assertEquals(256, codes["BUTTON_1"])
    }

    @Test
    fun `key layouts skip comments, HID usages and FUNCTION lines`() {
        val codes = scanCodes("# key 1 BUTTON_A\nkey usage 0x0c0067 BUTTON_B\nkey 465   ESCAPE    FUNCTION\nkey 1     ESCAPE\n")
        assertEquals(mapOf("ESCAPE" to 1), codes)
    }

    @Test
    fun `combos map to the controller's scan codes`() {
        val codes = scanCodes(fixture("Generic.kl"))
        assertEquals(Combo(314, 305), comboScanCodes(Combo(109, 97), codes, ::keyName))
        assertEquals(Combo(null, 315), comboScanCodes(Combo(null, 108), codes, ::keyName))
        // A keycode the layout has no line for.
        assertNull(comboScanCodes(Combo(109, 4242), codes, ::keyName))
        assertNull(comboScanCodes(Combo(4242, 97), codes, ::keyName))
    }

    @Test
    fun `the command holds the enable button, taps the hotkey's and lets go, in that order`() {
        val n = "/dev/input/event7"
        assertEquals(
            listOf(
                "sendevent $n 1 314 1", "sendevent $n 0 0 0", "sleep 0.150",
                "sendevent $n 1 305 1", "sendevent $n 0 0 0", "sleep 0.050",
                "sendevent $n 1 305 0", "sendevent $n 0 0 0",
                "sendevent $n 1 314 0", "sendevent $n 0 0 0",
            ),
            pressCommand(n, Combo(314, 305), holdMs = 150)!!.split("; "),
        )
    }

    @Test
    fun `with no hold the tap alone, and twice with a pause between`() {
        val n = "/dev/input/event7"
        val once = "sendevent $n 1 315 1; sendevent $n 0 0 0; sleep 0.050; sendevent $n 1 315 0; sendevent $n 0 0 0"
        assertEquals(once, pressCommand(n, Combo(null, 315), holdMs = 150))
        assertEquals("$once; sleep 0.300; $once", pressCommand(n, Combo(null, 315), holdMs = 150, times = 2))
    }

    @Test
    fun `a node path that isn't an input node gives no command`() {
        for (node in listOf("/dev/input/event7; reboot", "/dev/input/mouse0", "/dev/input/event", "<virtual>", "")) {
            assertNull(node, pressCommand(node, Combo(314, 305), holdMs = 150))
        }
        assertNull(pressCommand("/dev/input/event7", Combo(-1, 305), holdMs = 150))
        assertNull(pressCommand("/dev/input/event7", Combo(null, -305), holdMs = 150))
    }

    @Test
    fun `every command holds only sendevent, sleep, numbers and the node`() {
        assertTrue(isPressCommand(pressCommand("/dev/input/event7", Combo(314, 305), holdMs = 1_234_567, times = 9)!!))
    }

    @Test
    fun `RetroArch's packages come from a process list, its other processes too`() {
        val ps = """
            NAME
            init
            org.es_de.frontend
            com.retroarch.aarch64
            com.retroarch.aarch64:remote
            com.retroarchish
            me.magnum.melonds.nightly
            com.retroarch.ra32:x
        """.trimIndent()
        assertEquals(setOf("com.retroarch.aarch64", "com.retroarchish", "com.retroarch.ra32"), retroarchProcesses(ps))
        assertEquals(emptySet<String>(), retroarchProcesses("NAME\ninit\n"))
    }
}

private val COMMAND_PART = Regex("""sendevent /dev/input/event\d+ \d+ \d+ \d+|sleep \d+\.\d{3}""")

/** Whether [command] is only `sendevent` on an input node and `sleep`, with numbers. */
fun isPressCommand(command: String) = command.split("; ").all(COMMAND_PART::matches)
