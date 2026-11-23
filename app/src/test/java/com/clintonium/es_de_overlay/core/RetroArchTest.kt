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

    // The state search's lines as the Nova gave them on 2026-11-20: Core provided, Snes9x on 4:3, the others on auto.
    private val novaLook = retroarchConfig(
        listOf(
            "autoload",
            "1763650000 /storage/emulated/0/RetroArch/states/Snes9x/Easyland.state.auto.png",
            """cfg aspect_ratio_index = "22"""",
            """cfg custom_viewport_height = "960"""",
            """cfg custom_viewport_width = "1280"""",
            """cfg video_aspect_ratio = "1.333300"""",
            """cfg video_aspect_ratio_auto = "false"""",
            """override Genesis Plus GX/Genesis Plus GX.opt:genesis_plus_gx_aspect_ratio = "auto"""",
            """override Nestopia/Nestopia.opt:nestopia_aspect = "auto"""",
            """override PicoDrive/PicoDrive.opt:picodrive_aspect = "PAR"""",
            """override Snes9x/Snes9x.opt:snes9x_aspect = "4:3"""",
        ),
    )

    private fun RetroArchConfig.at(core: String, game: String, width: Int, height: Int, folder: String = "snes") =
        aspect(core, game, folder, width, height, screen = 4 / 3.0)

    private fun assertShape(expected: Double?, actual: Double?) =
        if (expected == null) assertNull(actual) else assertEquals(expected, actual!!, 1e-6)

    @Test
    fun `with Core provided the Nova's state pictures take their cores' shapes`() {
        assertShape(4 / 3.0, novaLook.at("Snes9x", "Easyland", 256, 224))
        assertShape(1.3061224, novaLook.at("Genesis Plus GX", "Aladdin", 320, 224))
        // Nestopia's NTSC filter, on by default, makes the frame 602 wide.
        assertShape(1.3163265, novaLook.at("Nestopia", "Dr. Mario", 602, 224))
        // PicoDrive's PAR is square pixels, and an unlisted core keeps its frame.
        assertShape(null, novaLook.at("PicoDrive", "Aladdin", 320, 224))
        assertShape(null, novaLook.at("Some Core", "Game", 320, 224))
    }

    @Test
    fun `each aspect setting gives the shape RetroArch shows`() {
        fun with(vararg settings: Pair<String, String>) = novaLook.copy(main = novaLook.main + settings).at("Snes9x", "Easyland", 256, 224)
        assertShape(8 / 7.0, with("aspect_ratio_index" to "15"))
        assertShape(1.3333, with("aspect_ratio_index" to "20"))
        // Config with a negative ratio: the core's with Auto on, else the frame's.
        assertShape(4 / 3.0, with("aspect_ratio_index" to "20", "video_aspect_ratio" to "-1.0", "video_aspect_ratio_auto" to "true"))
        assertShape(null, with("aspect_ratio_index" to "20", "video_aspect_ratio" to "-1.0"))
        assertShape(null, with("aspect_ratio_index" to "21"))
        assertShape(4 / 3.0, with("aspect_ratio_index" to "23"))
        assertShape(5 / 4.0, with("aspect_ratio_index" to "23", "custom_viewport_width" to "1200"))
        // Custom with no viewport set: the core's.
        assertShape(4 / 3.0, with("aspect_ratio_index" to "23", "custom_viewport_width" to "0"))
        assertShape(4 / 3.0, novaLook.copy(main = novaLook.main + ("aspect_ratio_index" to "24")).aspect("Snes9x", "Easyland", "snes", 256, 224, 4 / 3.0))
        assertShape(16 / 9.0, novaLook.copy(main = novaLook.main + ("aspect_ratio_index" to "24")).aspect("Snes9x", "Easyland", "snes", 256, 224, 16 / 9.0))
        assertShape(4 / 3.0, with("aspect_ratio_index" to "99"))
    }

    @Test
    fun `overrides stack core, then content folder, then game`() {
        val look = novaLook.copy(
            files = novaLook.files + mapOf(
                "Snes9x/Snes9x.cfg" to mapOf("aspect_ratio_index" to "15"),
                "Snes9x/02) Hacks.cfg" to mapOf("aspect_ratio_index" to "21"),
                "Snes9x/Easyland.cfg" to mapOf("aspect_ratio_index" to "0"),
            ),
        )
        assertShape(4 / 3.0, look.at("Snes9x", "Easyland", 256, 224, folder = "02) Hacks"))
        assertShape(null, look.at("Snes9x", "Other Hack", 256, 224, folder = "02) Hacks"))
        assertShape(8 / 7.0, look.at("Snes9x", "Super Mario World", 256, 224))
        // Another core's override doesn't apply.
        assertShape(1.3061224, look.at("Genesis Plus GX", "Aladdin", 320, 224))
    }

    @Test
    fun `core options come from the game's file, else the folder's, else the core's, else the global one`() {
        val look = RetroArchConfig(
            mapOf("aspect_ratio_index" to "22"),
            mapOf(
                "Snes9x/Easyland.opt" to mapOf("snes9x_aspect" to "uncorrected"),
                "Snes9x/02) Hacks.opt" to mapOf("snes9x_aspect" to "ntsc"),
                "Snes9x/Snes9x.opt" to mapOf("snes9x_aspect" to "4:3"),
                "retroarch-core-options.cfg" to mapOf("snes9x_aspect" to "pal"),
            ),
        )
        assertShape(8 / 7.0, look.at("Snes9x", "Easyland", 256, 224, folder = "02) Hacks"))
        assertShape(1.3061224, look.at("Snes9x", "Other Hack", 256, 224, folder = "02) Hacks"))
        assertShape(4 / 3.0, look.at("Snes9x", "Super Mario World", 256, 224))
        val noGameOptions = look.copy(main = look.main + ("game_specific_options" to "false"))
        assertShape(4 / 3.0, noGameOptions.at("Snes9x", "Easyland", 256, 224, folder = "02) Hacks"))
        val global = look.copy(main = look.main + ("global_core_options" to "true"))
        assertShape(1.5842160, global.at("Snes9x", "Easyland", 256, 224, folder = "02) Hacks"))
        // No options file: the core's default (Snes9x's 4:3).
        assertShape(4 / 3.0, RetroArchConfig(emptyMap(), emptyMap()).at("Snes9x", "Easyland", 256, 224))
    }

    @Test
    fun `cores work out their shapes as their sources do`() {
        fun snes(aspect: String, game: String = "Game", w: Int = 256, h: Int = 224) = coreAspect("Snes9x", mapOf("snes9x_aspect" to aspect), game, w, h)
        assertShape(1.3229167, snes("4:3 scaled"))
        // A hi-res, interlaced frame is still 256 dots over one field.
        assertShape(256 / 224.0, snes("uncorrected", w = 512, h = 448))
        assertShape(1.5842160, snes("auto", "Super Mario World (Europe)"))
        assertShape(1.3061224, snes("auto", "Super Mario World (USA, Europe)"))
        fun gpgx(aspect: String, w: Int, h: Int, game: String = "Game") = coreAspect("Genesis Plus GX", mapOf("genesis_plus_gx_aspect_ratio" to aspect), game, w, h)
        assertShape(1.5842160, gpgx("auto", 320, 224, "Aladdin (Europe)"))
        assertShape(1.3061224, gpgx("auto", 256, 224))
        assertShape(4 / 3.0, gpgx("auto", 160, 144))
        assertShape(null, gpgx("Uncorrected", 320, 224))
        assertShape(null, coreAspect("Genesis Plus GX", mapOf("genesis_plus_gx_blargg_ntsc_filter" to "composite"), "Game", 640, 224))
        fun nes(aspect: String, w: Int) = coreAspect("Nestopia", mapOf("nestopia_aspect" to aspect), "Game", w, 224)
        assertShape(1.3061224, nes("auto", 256))
        assertShape(1.34375, nes("uncorrected", 602))
        assertShape(1.25, nes("5:4", 602))
        assertShape(4 / 3.0, coreAspect("PicoDrive", mapOf("picodrive_aspect" to "4/3"), "Game", 320, 224))
        assertShape(4 / 3.0 * 240 / 224, coreAspect("PicoDrive", mapOf("picodrive_aspect" to "4/3"), "Game (Europe)", 320, 224))
        assertShape(1.3061224, coreAspect("Stella", emptyMap(), "Pitfall!", 160, 210))
        assertShape(1.3307414, coreAspect("Stella 2023", emptyMap(), "Pitfall! (Europe)", 160, 250))
        assertShape(3 / 4.0, coreAspect("FinalBurn Neo", emptyMap(), "1942", 224, 256))
        assertShape(1.6, coreAspect("DOSBox-pure", emptyMap(), "Game", 640, 200))
        assertShape(null, coreAspect("DOSBox-pure", emptyMap(), "Game", 320, 200))
        assertShape(4 / 3.0, coreAspect("DOSBox-pure", mapOf("dosbox_pure_aspect_correction" to "true"), "Game", 320, 200))
        assertShape(null, coreAspect("ScummVM", emptyMap(), "Game", 320, 200))
        // Drawn in hardware, the state picture is already the screen's shape; in software, the core's sums apply.
        assertShape(null, coreAspect("Beetle PSX HW", mapOf("beetle_psx_hw_aspect_ratio" to "4:3"), "Crash Bandicoot", 320, 240))
        val psx = mapOf("beetle_psx_hw_renderer" to "software")
        assertShape(4 / 3.0, coreAspect("Beetle PSX HW", psx + ("beetle_psx_hw_aspect_ratio" to "4:3"), "Crash Bandicoot", 320, 240))
        assertShape(1.2190476, coreAspect("Beetle PSX HW", psx, "Crash Bandicoot", 292, 240))
        assertShape(64 / 27.0, coreAspect("Beetle PSX HW", psx + mapOf("beetle_psx_hw_widescreen_hack" to "enabled", "beetle_psx_hw_widescreen_hack_aspect_ratio" to "21:9"), "Game", 292, 240))
        assertShape(null, coreAspect("SwanStation", emptyMap(), "Game", 320, 240))
        val swan = mapOf("swanstation_GPU_Renderer" to "Software")
        assertShape(4 / 3.0, coreAspect("SwanStation", swan, "Game", 320, 240))
        assertShape(1.6, coreAspect("SwanStation", swan + mapOf("swanstation_Display_AspectRatio" to "Custom", "swanstation_Display_CustomAspectRatioNumerator" to "16", "swanstation_Display_CustomAspectRatioDenominator" to "10"), "Game", 320, 240))
        assertShape(1.3061224, coreAspect("SwanStation", swan + mapOf("swanstation_Display_AspectRatio" to "Auto", "swanstation_Display_CropMode" to "Overscan"), "Game", 320, 224))
        assertShape(1.3469388, coreAspect("Beetle Saturn", emptyMap(), "Game", 352, 224))
        assertShape(1.3258929, coreAspect("Beetle Saturn", emptyMap(), "Game (Europe)", 352, 256))
        assertShape(4 / 3.0, coreAspect("Mupen64Plus-Next", mapOf("mupen64plus-rdp-plugin" to "angrylion"), "Game", 640, 240))
        assertShape(null, coreAspect("Mupen64Plus-Next", emptyMap(), "Game", 640, 480))
        assertShape(null, coreAspect("Gambatte", emptyMap(), "Dr. Mario", 160, 144))
    }

    @Test
    fun `a PAL release is told by its name's region tags`() {
        assertTrue(palByName("Aladdin (Europe)"))
        assertTrue(palByName("Sonic (Germany) (Rev 1)"))
        assertTrue(palByName("Pitfall (E)"))
        assertFalse(palByName("Aladdin (USA, Europe)"))
        assertFalse(palByName("Aladdin (UE)"))
        assertFalse(palByName("Easyland"))
    }

    @Test
    fun `the state search's lines are read per file, a colon in a name included`() {
        val look = retroarchConfig(
            listOf(
                """cfg aspect_ratio_index = "22"""",
                """override Snes9x/Zelda: A Link to the Past.cfg:aspect_ratio_index = "1"""",
                """override Snes9x/Snes9x.opt:snes9x_aspect = "4:3"""",
                "1763650000 /storage/emulated/0/RetroArch/states/Snes9x/Easyland.state.auto.png",
            ),
        )
        assertEquals(mapOf("aspect_ratio_index" to "22"), look.main)
        assertEquals(mapOf("aspect_ratio_index" to "1"), look.files["Snes9x/Zelda: A Link to the Past.cfg"])
        assertEquals(setOf("Snes9x/Zelda: A Link to the Past.cfg", "Snes9x/Snes9x.opt"), look.files.keys)
    }
}

private val COMMAND_PART = Regex("""sendevent /dev/input/event\d+ \d+ \d+ \d+|sleep \d+\.\d{3}""")

/** Whether [command] is only `sendevent` on an input node and `sleep`, with numbers. */
fun isPressCommand(command: String) = command.split("; ").all(COMMAND_PART::matches)
