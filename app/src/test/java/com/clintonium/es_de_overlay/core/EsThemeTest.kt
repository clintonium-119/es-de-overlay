package com.clintonium.es_de_overlay.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EsThemeTest {
    private fun fixture(shape: String): (String) -> String? =
        { path -> File("../fixtures/themes/$shape/$path").takeIf { it.isFile }?.readText() }

    private fun resolve(
        shape: String,
        settings: ThemeSettings,
        system: String = "snes",
        aspect: Float = 16 / 9f,
        mediaType: String = "screenshots",
    ) = resolveTheme(fixture(shape), settings, system, aspect, mediaType)!!

    // A theme of [files] by path, for the rules the fixture shapes don't show.
    private fun inline(vararg files: Pair<String, String>, system: String = "snes") =
        resolveTheme(mapOf(*files)::get, ThemeSettings(theme = "inline"), system, 16 / 9f, "screenshots")

    @Test
    fun `settings read with defaults`() {
        val all = """
            <?xml version="1.0"?>
            <bool name="InputSwapButtons" value="true" />
            <bool name="ThemeTransitions" value="true" />
            <string name="Theme" value="minui-menu-es-de-custom" />
            <string name="ThemeAspectRatio" value="16:9" />
            <string name="ThemeColorScheme" value="dark-bold" />
            <string name="ThemeFontSize" value="small" />
            <string name="ThemeVariant" value="gamelist-list-screenshot" />
        """.trimIndent()
        assertEquals(
            ThemeSettings("minui-menu-es-de-custom", "gamelist-list-screenshot", "dark-bold", "small", "16:9", swapButtons = true),
            themeSettings(all.byteInputStream()),
        )
        assertEquals(
            false,
            themeSettings("""<bool name="InputSwapButtons" value="false" />""".byteInputStream()).swapButtons,
        )
        assertEquals(
            ThemeSettings("linear-es-de", "", "", "", "automatic"),
            themeSettings("""<?xml version="1.0"?><int name="ScreenWidth" value="0" />""".byteInputStream()),
        )
    }

    @Test
    fun `minui shape resolves pill, font, help colours and listFontSize for small`() {
        val dark = ThemeSettings("minui-shape", colorScheme = "dark", fontSize = "small")
        assertEquals(
            ThemeStyle(
                background = 0xFF000000.toInt(),
                fontPath = "fonts/rounded-bold.otf",
                fontSize = 0.0417f,
                lineSpacing = 2.0f,
                primaryColor = 0xFFF0F0F0.toInt(),
                selectedColor = 0xFF000000.toInt(),
                selectorColor = 0x00000000,
                selectedBackgroundColor = 0xFFF0F0F0.toInt(),
                selectedBackgroundCornerRadius = 0.025f,
                helpTextColor = 0xFFF0F0F0.toInt(),
                helpIconColor = 0xFFA0A0A0.toInt(),
                helpBackgroundColor = 0xFF202020.toInt(),
                helpFontPath = "fonts/rounded-bold.otf",
                artCornerRadius = 0.025f,
                themeName = "MinUI Shape",
                colorSchemeLabel = "Dark",
            ),
            resolve("minui-shape", dark),
        )
        // The custom scheme's file sits in a folder, its font path is written for theme.xml, and its pill is RRGGBBAA.
        val custom = resolve("minui-shape", dark.copy(colorScheme = "custom"))
        assertEquals("custom/fonts/own.ttf", custom.fontPath)
        assertEquals(0xFF102030.toInt(), custom.background)
        assertEquals("Custom", custom.colorSchemeLabel)
        assertEquals(0xCCE0E0C0.toInt(), custom.selectedBackgroundColor)
        assertEquals(0.01f, custom.selectedBackgroundCornerRadius)
        // The 4:3 file comes after the font size block and overrides its line spacing.
        assertEquals(1.6f, resolve("minui-shape", dark, aspect = 4 / 3f).lineSpacing)
        assertEquals(1.6f, resolve("minui-shape", dark.copy(aspectRatio = "4:3")).lineSpacing)
    }

    @Test
    fun `slate shape resolves selector bar and per-system colours include`() {
        val style = resolve("slate-shape", ThemeSettings("slate-shape", colorScheme = "dark"), system = "gbc")
        assertEquals(
            ThemeStyle(
                // gbc/colors.xml's colour under the half-black shade drawn above it; the band isn't full-screen.
                background = 0xFF0C180C.toInt(),
                // The frame under the list; not the one beside it, nor the glare drawn above the list.
                listBackground = 0xFF707070.toInt(),
                fontPath = "fonts/slate-bold.ttf",
                fontSize = 0.04f,
                lineSpacing = 1.5f,
                primaryColor = 0xFFD0D4D8.toInt(),
                selectedColor = 0xFFFFFFFF.toInt(),
                selectorColor = 0xFF3A4048.toInt(),
                descriptionFontPath = "fonts/slate-regular.ttf",
                descriptionFontSize = 0.03f,
                descriptionColor = 0xFFD0D4D8.toInt(),
                descriptionLineSpacing = 1.3f,
                helpTextColor = 0xFFD0D4D8.toInt(),
                helpIconColor = 0xFF3A4048.toInt(),
                artCornerRadius = 0.008f,
                themeName = "Slate Shape",
                // A scheme without a label is called by its name.
                colorSchemeLabel = "dark",
            ),
            style,
        )
        // A system without its own colours keeps the scheme's (202428, under the same shade).
        assertEquals(0xFF101214.toInt(), resolve("slate-shape", ThemeSettings("slate-shape")).background)
    }

    @Test
    fun `a system's own theme xml replaces the root one`() {
        val n64 = resolve("slate-shape", ThemeSettings("slate-shape"), system = "n64")
        assertEquals(0.5f, n64.selectorWidth)
        assertEquals(0xFF101214.toInt(), n64.background)
        assertEquals("fonts/slate-bold.ttf", n64.fontPath)
        assertNull(resolve("slate-shape", ThemeSettings("slate-shape"), system = "gbc").selectorWidth)
    }

    @Test
    fun `modern shape resolves background colour and variant all aspect ratio include`() {
        val settings = ThemeSettings("modern-shape", colorScheme = "dark")
        assertEquals(
            ThemeStyle(
                background = 0xFF1A1A1A.toInt(),
                fontPath = "fonts/modern.otf",
                fontSize = 0.036f,
                lineSpacing = 1.5f,
                primaryColor = 0xFFA8A8A8.toInt(),
                selectedColor = 0xFF1A1A1A.toInt(),
                selectorColor = 0xFFE8E8E8.toInt(),
                descriptionFontSize = 0.03f,
                descriptionColor = 0xFFA8A8A8.toInt(),
                // No screenshot art in this theme, so the miximage box's.
                artCornerRadius = 0.01f,
                themeName = "Modern Shape",
                colorSchemeLabel = "Dark",
            ),
            resolve("modern-shape", settings),
        )
        assertEquals(1.3f, resolve("modern-shape", settings, aspect = 4 / 3f).lineSpacing)
        assertEquals(0.042f, resolve("modern-shape", settings.copy(fontSize = "large")).fontSize)
        assertEquals(0xFFEFEFEF.toInt(), resolve("modern-shape", settings.copy(colorScheme = "light")).background)
        assertEquals(0, resolve("modern-shape", settings.copy(variant = "noGameMedia")).selectorColor)
    }

    @Test
    fun `linear shape resolves thin selector and description text`() {
        assertEquals(
            ThemeStyle(
                background = 0xFF363636.toInt(),
                fontPath = "fonts/regular.ttf",
                fontSize = 0.035f,
                lineSpacing = 1.498f,
                primaryColor = 0xFF787878.toInt(),
                selectedColor = 0xFFFEFEFE.toInt(),
                selectorColor = 0xFFFEFEFE.toInt(),
                selectorWidth = 0.003f,
                descriptionFontPath = "fonts/light.ttf",
                descriptionFontSize = 0.032f,
                descriptionColor = 0xFFF9F9F9.toInt(),
                helpTextColor = 0xFFF9F9F9.toInt(),
                helpIconColor = 0xFFF9F9F9.toInt(),
                // No background of its own: the panel image under the help prompts.
                helpBackgroundColor = 0xFF010101.toInt(),
                helpFontPath = "fonts/light.ttf",
                themeName = "Linear Shape",
                // The English one of its labels.
                colorSchemeLabel = "Dark",
            ),
            resolve("linear-shape", ThemeSettings("linear-shape", colorScheme = "dark", fontSize = "medium")),
        )
    }

    @Test
    fun `full-screen images layer in draw order with their opacity`() {
        fun screen(vararg images: String) = inline("theme.xml" to """<theme><view name="gamelist">${images.joinToString("")}</view></theme>""")!!.background
        fun image(name: String, color: String?, z: Int, opacity: String? = null) =
            """<image name="$name"><size>1 1</size>${color?.let { "<color>$it</color>" }.orEmpty()}<zIndex>$z</zIndex>${opacity?.let { "<opacity>$it</opacity>" }.orEmpty()}</image>"""
        // Shaped like TechDweeb: a faint black shadow, then the panel drawn after it at the same zIndex.
        assertEquals(0xFF171717.toInt(), screen(image("shadow", "000000", 2, "0.2"), image("panel", "171717", 2)))
        // The other way round, the shadow darkens the panel a fifth of the way to black.
        assertEquals(0xFF121212.toInt(), screen(image("panel", "171717", 2), image("shadow", "000000", 2, "0.2")))
        // zIndex decides before the theme's order; a translucent colour is drawn over ES-DE's black.
        assertEquals(0xFF171717.toInt(), screen(image("panel", "171717", 9), image("shadow", "000000", 1, "0.2")))
        assertEquals(0xFF808080.toInt(), screen(image("grey", "FFFFFF80", 1)))
        // A picture without a colour gives no colour, and no full-screen image no background.
        assertEquals(0xFF171717.toInt(), screen(image("photo", null, 5), image("panel", "171717", 1)))
        assertNull(screen(image("photo", null, 5)))
    }

    @Test
    fun `svg pictures give a panel colour and a selector shape`() {
        // Shaped like Canvas: an Inkscape rounded rect for the selector, a translucent rounded panel without a color.
        val selector = """<svg height="26.67mm" viewBox="0 0 365.5 26.67"><inkscape:path-effect radius="150"/>
            <g><path style="fill:#ffffff;stroke-width:0" width="1381.7" height="100.8" d="m -7249.8,5345.8 h 1302.3 a 39.6875,39.6875 45 0 1 39.6,39.6 v 21.4" /></g></svg>"""
        val panel = """<svg viewBox="0 0 1590 1818"><path d="M 1590,23 C 1590,10 0,0 z" style="fill:#080808;fill-opacity:0.75;stroke-width:1.05"/>
            <rect height="1" style="fill:#494949;opacity:0.6"/></svg>"""
        val theme = """
            <theme><view name="gamelist">
                <textlist name="list"><pos>0.5 0.1</pos><size>0.4 0.8</size><primaryColor>EEEEEE</primaryColor>
                    <selectorColor>777777</selectorColor><selectorImagePath>./images/selector.svg</selectorImagePath></textlist>
                <image name="panel"><pos>0.48 0.08</pos><size>0.44 0.84</size><path>./images/panel.svg</path><zIndex>7</zIndex></image>
            </view></theme>
        """
        val style = inline("theme.xml" to theme, "images/selector.svg" to selector, "images/panel.svg" to panel)!!
        assertEquals(0xBF080808.toInt(), style.listBackground)
        assertEquals(39.6875f / 100.8f, style.selectorCornerRadius!!, 0.0001f)
        // A rect's rx over its height, short colours, and what gives nothing.
        assertEquals(0.25f, svgCornerRadius("""<svg><rect width="100" height="40" rx="10" fill="#fff"/></svg>""")!!, 0.0001f)
        assertEquals(0.5f, svgCornerRadius("""<svg viewBox="0 0 100 20"><rect width="100" rx="30"/></svg>""")!!, 0.0001f)
        assertEquals(0xFFFFFFFF.toInt(), svgFill("""<svg><rect width="100" height="40" rx="10" fill="#fff"/></svg>"""))
        assertEquals(0x80123456.toInt(), svgFill("""<svg><circle fill="#123456" opacity="0.5"/></svg>"""))
        assertNull(svgFill("""<svg><rect fill="url(#gradient)"/></svg>"""))
        assertNull(svgFill("""<svg><rect fill="none"/></svg>"""))
        assertNull(svgCornerRadius("""<svg><rect width="100" height="40"/></svg>"""))
        // A drawing that starts with an unfilled box marking out its page, as Canvas's medium selector does.
        val boxed = """<svg><rect x="0" y="0" width="1590" height="2042" style="fill:none;stroke-width:1"/>
            <path style="fill:#ffffff" width="1423" height="122.7" d="M 93,1902 H 1437 a 39.6875,39.6875 45 0 1 39.6,39.6" /></svg>"""
        assertEquals(39.6875f / 122.7f, svgCornerRadius(boxed)!!, 0.0001f)
        assertEquals(0xFFFFFFFF.toInt(), svgFill(boxed))
        // A raster picture without a color gives nothing.
        assertNull(inline("theme.xml" to theme.replace("panel.svg", "panel.png"), "images/selector.svg" to selector)!!.listBackground)
    }

    @Test
    fun `empty settings pick the first capability entries`() {
        val style = resolve("minui-shape", ThemeSettings("minui-shape"))
        // The first scheme (dark), the first variant (its art box), and medium: ES-DE offers font sizes medium first.
        assertEquals(0xFF000000.toInt(), style.background)
        assertEquals(0.025f, style.artCornerRadius)
        assertEquals(0.05f, style.fontSize)
        assertEquals(1.9f, style.lineSpacing)
    }

    @Test
    fun `unknown colour scheme falls back to the first`() {
        assertEquals(0xFF000000.toInt(), resolve("minui-shape", ThemeSettings("minui-shape", colorScheme = "gone")).background)
        assertEquals(0.05f, resolve("minui-shape", ThemeSettings("minui-shape", fontSize = "x-large")).fontSize)
        // An unknown variant is the first one, textlist; carousel has no textlist and its own description colour.
        assertEquals(0.003f, resolve("linear-shape", ThemeSettings("linear-shape", variant = "gone")).selectorWidth)
        val carousel = resolve("linear-shape", ThemeSettings("linear-shape", variant = "carousel"))
        assertNull(carousel.fontSize)
        assertEquals(0xFFFF0000.toInt(), carousel.descriptionColor)
    }

    @Test
    fun `nested variables and system theme substitute`() {
        val theme = """
            <theme>
                <variables>
                    <base>1A2B3C</base>
                    <text>${'$'}{base}</text>
                    <font>./fonts/${'$'}{system.name}.ttf</font>
                </variables>
                <include>./${'$'}{system.theme}/colors.xml</include>
                <view name="gamelist">
                    <textlist name="list">
                        <primaryColor>${'$'}{text}</primaryColor>
                        <selectedColor>${'$'}{selected}</selectedColor>
                        <fontPath>${'$'}{font}</fontPath>
                    </textlist>
                </view>
            </theme>
        """
        val gbc = """<theme><variables><selected>${'$'}{base}80</selected></variables></theme>"""
        val style = inline("theme.xml" to theme, "gbc/colors.xml" to gbc, system = "gbc")!!
        assertEquals(0xFF1A2B3C.toInt(), style.primaryColor)
        assertEquals(0x801A2B3C.toInt(), style.selectedColor)
        assertEquals("fonts/gbc.ttf", style.fontPath)
        // Undefined, the variable is empty and the property is left out.
        assertNull(inline("theme.xml" to theme, system = "snes")!!.selectedColor)
        // The fixture's per-system include, after colors.xml, wins for gbc.
        val linear = ThemeSettings("linear-shape")
        assertEquals(0xFFC0E0C0.toInt(), resolve("linear-shape", linear, system = "gbc").descriptionColor)
        assertEquals(0xFFF9F9F9.toInt(), resolve("linear-shape", linear, system = "snes").descriptionColor)
    }

    @Test
    fun `missing include is skipped and an include loop stops`() {
        fun list(color: String) = """<view name="gamelist"><textlist name="list"><primaryColor>$color</primaryColor></textlist></view>"""
        val style = inline(
            "theme.xml" to "<theme><include>./missing.xml</include><include>./a.xml</include></theme>",
            "a.xml" to "<theme><include>./sub/b.xml</include>${list("111111")}</theme>",
            "sub/b.xml" to "<theme><include>./../a.xml</include><include>./../theme.xml</include>${list("222222")}</theme>",
        )!!
        assertEquals(0xFF111111.toInt(), style.primaryColor)
        // In a chain of includes, the eighth is read and the ninth isn't.
        val chain = (0..9).map { i ->
            val view = when (i) {
                8 -> list("000008")
                9 -> """<view name="gamelist"><textlist name="list"><selectedColor>000009</selectedColor></textlist></view>"""
                else -> ""
            }
            (if (i == 0) "theme.xml" else "$i.xml") to "<theme><include>./${i + 1}.xml</include>$view</theme>"
        }
        val deep = inline(*chain.toTypedArray())!!
        assertEquals(0xFF000008.toInt(), deep.primaryColor)
        assertNull(deep.selectedColor)
        // Paths outside the theme folder aren't read.
        val outside = inline("theme.xml" to "<theme><include>./../../es_settings.xml</include><include>/sdcard/x.xml</include></theme>")
        assertEquals(ThemeStyle(), outside)
    }

    @Test
    fun `later property wins`() {
        fun list(color: String) = """<view name="gamelist"><textlist name="list"><primaryColor>$color</primaryColor></textlist></view>"""
        val capabilities = "<themeCapabilities><aspectRatio>16:9</aspectRatio><variant name=\"one\"/></themeCapabilities>"
        assertEquals(0xFF222222.toInt(), inline("theme.xml" to "<theme>${list("111111")}${list("222222")}</theme>")!!.primaryColor)
        // ES-DE parses variants after views and aspect ratios after variants, wherever they sit in the file.
        val ordered = """
            <theme>
                <aspectRatio name="16:9">${list("333333")}</aspectRatio>
                <variant name="one">${list("222222")}</variant>
                ${list("111111")}
            </theme>
        """
        assertEquals(0xFF333333.toInt(), inline("capabilities.xml" to capabilities, "theme.xml" to ordered)!!.primaryColor)
        // Without capabilities, no variant or aspect ratio block applies.
        assertEquals(0xFF111111.toInt(), inline("theme.xml" to ordered)!!.primaryColor)
    }

    @Test
    fun `six and eight digit colours parse, others are null`() {
        assertEquals(0xFF1A2B3C.toInt(), themeColor("1A2B3C"))
        assertEquals(0x801A2B3C.toInt(), themeColor("1a2b3c80"))
        assertEquals(0, themeColor("00000000"))
        listOf("", "FFF", "FFFFFFF", "GGGGGG", "-12345", "0x1234", "FFFFFFFFFF").forEach { assertNull(it, themeColor(it)) }
    }

    @Test
    fun `missing theme xml returns null`() {
        assertNull(resolveTheme({ null }, ThemeSettings(), "snes", 16 / 9f, "screenshots"))
        assertNull(inline("theme.xml" to "<notATheme/>"))
        assertNull(inline("theme.xml" to "<theme>"))
    }

    private fun unpacked(stream: ByteArray) = mutableMapOf<String, String>().apply {
        unpackFiles(stream.inputStream()) { path, bytes -> put(path, bytes.decodeToString()) }
    }

    // A tar entry as toybox's `tar -c` writes one: a 512-byte ustar header, then the body padded to whole blocks.
    private fun entry(name: String, body: String, type: Char = '0', prefix: String = ""): ByteArray {
        val bytes = body.toByteArray()
        val header = ByteArray(512)
        name.toByteArray().copyInto(header)
        "%011o".format(bytes.size).toByteArray().copyInto(header, 124)
        header[156] = type.code.toByte()
        "ustar".toByteArray().copyInto(header, 257)
        prefix.toByteArray().copyInto(header, 345)
        return header + bytes + ByteArray((512 - bytes.size % 512) % 512)
    }

    private fun tar(vararg files: Pair<String, String>) =
        files.fold(ByteArray(0)) { all, (path, text) -> all + entry(path, text) } + ByteArray(1024)

    @Test
    fun `a tar stream unpacks into its paths`() {
        // Sizes are bytes, so a multi-byte character doesn't end a file early; a body over a block keeps its padding.
        val files = arrayOf("./theme.xml" to "<theme>\n</theme>", "./colors/dark.xml" to "é".repeat(400), "./fonts/x.ttf" to "")
        assertEquals(mapOf(*files), unpacked(tar(*files)))
        // A long path, as a GNU long-name entry or in the ustar prefix field.
        val long = "./_inc/" + "deep/".repeat(25) + "font.otf"
        assertEquals(mapOf(long to "f"), unpacked(entry("././@LongLink", long + "\u0000", 'L') + entry(long.take(100), "f") + ByteArray(1024)))
        assertEquals(mapOf("./_inc/fonts/x.otf" to "g"), unpacked(entry("x.otf", "g", prefix = "./_inc/fonts") + ByteArray(1024)))
        // A folder entry is no file.
        assertEquals(mapOf("./a.xml" to "a"), unpacked(entry("./_inc/", "", '5') + tar("./a.xml" to "a")))
    }

    @Test
    fun `tar paths outside the folder are skipped`() {
        val stream = tar("../../evil.xml" to "x", "/data/evil.xml" to "y", "./a/../b.xml" to "z", "./ok.xml" to "ok")
        assertEquals(mapOf("./ok.xml" to "ok"), unpacked(stream))
    }

    @Test
    fun `a truncated tar entry is not written`() {
        val whole = tar("./a.xml" to "aaaa", "./b.xml" to "b".repeat(600))
        assertEquals(mapOf("./a.xml" to "aaaa"), unpacked(whole.copyOf(512 * 3 + 100)))
        assertEquals(mapOf("./a.xml" to "aaaa"), unpacked(whole.copyOf(512 * 2 + 300)))
        assertEquals(emptyMap<String, String>(), unpacked("not a tar".toByteArray()))
    }

    @Test
    fun `a doctype is refused`() {
        val entity = """<?xml version="1.0"?><!DOCTYPE theme [<!ENTITY c "1A2B3C">]><theme><view name="gamelist"><textlist name="l"><primaryColor>&c;</primaryColor></textlist></view></theme>"""
        assertNull(inline("theme.xml" to entity))
        assertNull(xmlDocument("""<!doctype x SYSTEM "file:///etc/passwd"><x/>"""))
        assertEquals("x", xmlDocument("<x/>")!!.documentElement.tagName)
    }
}
