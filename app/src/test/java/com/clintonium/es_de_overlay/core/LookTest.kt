package com.clintonium.es_de_overlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTest {
    // Shaped like the minui-menu-es-de dark-bold, Slate dark and Linear dark styles EsTheme resolves.
    private val minui = ThemeStyle(
        background = 0xFF000000.toInt(),
        fontPath = "_inc/fonts/rounded.otf",
        fontSize = 0.0417f,
        lineSpacing = 2.0f,
        primaryColor = 0xFFF7FBF7.toInt(),
        selectedColor = 0xFF000000.toInt(),
        selectorColor = 0x00000000,
        selectedBackgroundColor = 0xFFF7FBF7.toInt(),
        selectedBackgroundCornerRadius = 0.05f,
        helpTextColor = 0xFFF7FBF7.toInt(),
        helpIconColor = 0xFFA1A2A1.toInt(),
        helpBackgroundColor = 0xFF202420.toInt(),
        artCornerRadius = 0.01f,
    )
    private val slate = ThemeStyle(
        background = 0xFF404040.toInt(),
        listBackground = 0xFF6F6F6F.toInt(),
        fontPath = "core/fonts/bold.otf",
        fontSize = 0.025f,
        primaryColor = 0xFF000000.toInt(),
        selectedColor = 0xFFAFAFAF.toInt(),
        selectorColor = 0xFF161616.toInt(),
        descriptionFontPath = "core/fonts/semibold.otf",
        descriptionFontSize = 0.0195f,
        descriptionColor = 0xFF101010.toInt(),
    )
    private val linear = ThemeStyle(
        background = 0xFF373737.toInt(),
        fontPath = "assets/fonts/regular.ttf",
        fontSize = 0.035f,
        lineSpacing = 1.498f,
        primaryColor = 0xFF777777.toInt(),
        selectedColor = 0xFFFFFFFF.toInt(),
        selectorColor = 0xFFFFFFFF.toInt(),
        selectorWidth = 0.003f,
        descriptionFontPath = "assets/fonts/light.ttf",
        descriptionFontSize = 0.032f,
        descriptionColor = 0xFFFAFAFA.toInt(),
    )

    @Test
    fun `linear default values and source`() {
        assertEquals(
            Look(
                background = 0xFF373737.toInt(), text = 0xFF777777.toInt(), selectedText = 0xFFFFFFFF.toInt(),
                selectedBackground = 0, selectedCornerRadius = 0f, selector = 0xFFFFFFFF.toInt(), selectorWidth = 0.003f,
                selectorCornerRadius = 0f,
                helpBackground = 0xFF000000.toInt(), helpIcon = 0xFFFAFAFA.toInt(), helpText = 0xFFFAFAFA.toInt(),
                fontPath = null, fontSize = 0.035f, lineSpacing = 1.498f, bodyText = 0xFFFAFAFA.toInt(),
                bodyFontPath = null, bodyFontSize = 0.032f, bodyLineSpacing = 1.2f, artCornerRadius = 0f, source = LINEAR.source,
            ),
            LINEAR,
        )
        assertTrue(LINEAR.source is LookSource.Default)
        assertEquals(LINEAR.copy(source = LookSource.Default("theme folder not found")), look(null, "linear-es-de", "", "theme folder not found"))
    }

    @Test
    fun `pill from selectedBackgroundColor and radius`() {
        assertEquals(
            Look(
                background = 0xFF000000.toInt(), text = 0xFFF7FBF7.toInt(), selectedText = 0xFF000000.toInt(),
                selectedBackground = 0xFFF7FBF7.toInt(), selectedCornerRadius = 0.05f, selector = 0, selectorWidth = null,
                selectorCornerRadius = 0f,
                helpBackground = 0xFF202420.toInt(), helpIcon = 0xFFA1A2A1.toInt(), helpText = 0xFFF7FBF7.toInt(),
                fontPath = "themes/minui/_inc/fonts/rounded.otf", fontSize = 0.0417f, lineSpacing = 2.0f,
                // No description text: the guide text is the list's.
                bodyText = 0xFFF7FBF7.toInt(), bodyFontPath = "themes/minui/_inc/fonts/rounded.otf", bodyFontSize = 0.0417f,
                // ES-DE's spacing for text the theme doesn't space.
                bodyLineSpacing = 1.5f, artCornerRadius = 0.01f, source = LookSource.Theme("minui", "dark-bold"),
            ),
            look(minui, "minui", "dark-bold", null),
        )
    }

    @Test
    fun `bar from selectorColor with and without width`() {
        val bar = look(slate, "slate-es-de", "dark", null)
        assertEquals(0xFF161616.toInt(), bar.selector)
        assertNull(bar.selectorWidth)
        assertEquals(0, bar.selectedBackground)
        // The list's panel, not the screen behind it.
        assertEquals(0xFF6F6F6F.toInt(), bar.background)
        assertEquals(0xFF101010.toInt(), bar.bodyText)
        assertEquals("themes/slate-es-de/core/fonts/semibold.otf", bar.bodyFontPath)
        assertEquals(0.0195f, bar.bodyFontSize)
        assertEquals(1.3f, look(slate.copy(descriptionLineSpacing = 1.3f), "s", "", null).bodyLineSpacing)
        // A selector image's rounding; none without one.
        assertEquals(0.39f, look(slate.copy(selectorCornerRadius = 0.39f), "s", "", null).selectorCornerRadius)
        assertEquals(0f, bar.selectorCornerRadius)
        val thin = look(linear, "linear-copy", "dark", null)
        assertEquals(0xFFFFFFFF.toInt(), thin.selector)
        assertEquals(0.003f, thin.selectorWidth)
        assertEquals(0.003f, thin.thinSelector)
        // A selector as wide as the theme's list covers the row in the overlay, whose rows can run wider.
        assertNull(look(linear.copy(selectorWidth = 0.399f), "t", "", null).thinSelector)
        assertNull(bar.thinSelector)
        // The source is named as ES-DE's menus name the theme and its scheme, when the theme says.
        assertEquals(LookSource.Theme("linear-copy", "dark"), thin.source)
        assertEquals(LookSource.Theme("Linear", "Dark"), look(linear.copy(themeName = "Linear", colorSchemeLabel = "Dark"), "linear-copy", "dark", null).source)
        // A translucent panel is drawn over the screen colour; the screen colour over ES-DE's black.
        assertEquals(0xFF7F7F7F.toInt(), look(slate.copy(background = 0xFFFFFFFF.toInt(), listBackground = 0x80000000.toInt()), "s", "", null).background)
        assertEquals(0xFF808080.toInt(), look(slate.copy(background = 0x80FFFFFF.toInt(), listBackground = null), "s", "", null).background)
    }

    @Test
    fun `transparent selector with same text colours gets a visible cursor`() {
        val hidden = linear.copy(selectorColor = 0, selectedColor = linear.primaryColor)
        assertEquals(0x40777777, look(hidden, "t", "", null).selector)
        // A different selected colour already shows the cursor.
        assertEquals(0, look(linear.copy(selectorColor = 0), "t", "", null).selector)
        assertEquals(0, look(minui, "t", "", null).selector)
    }

    @Test
    fun `missing fields take es-de defaults`() {
        val bare = look(ThemeStyle(primaryColor = 0xFFEEEEEE.toInt()), "bare", "", null)
        assertEquals(0xFF000000.toInt(), bare.background)
        assertEquals(0xFF0000FF.toInt(), bare.selectedText)
        assertEquals(0xFF333333.toInt(), bare.selector)
        assertNull(bare.selectorWidth)
        assertEquals(0.045f, bare.fontSize)
        assertEquals(1.5f, bare.lineSpacing)
        assertEquals(0, bare.helpBackground)
        assertEquals(0xFF777777.toInt(), bare.helpText)
        assertEquals(0xFF777777.toInt(), bare.helpIcon)
        assertNull(bare.fontPath)
        assertNull(bare.artCornerRadius)
        assertEquals(0xFFEEEEEE.toInt(), bare.bodyText)
        // No text list at all (a carousel gamelist): Linear's list in the theme's text colour, the selected row and
        // its bar in it, the others fainter.
        val carousel = look(ThemeStyle(background = 0xFF102030.toInt(), descriptionColor = 0xFFC0C0C0.toInt()), "c", "", null)
        assertEquals(0xFF102030.toInt(), carousel.background)
        assertEquals(blend(0x99C0C0C0.toInt(), 0xFF102030.toInt()), carousel.text)
        assertEquals(0xFFC0C0C0.toInt(), carousel.selectedText)
        assertEquals(0xFFC0C0C0.toInt(), carousel.selector)
        // A light theme (Iconic's carousel) keeps its dark text readable.
        val light = look(ThemeStyle(background = 0xFFD6D6D6.toInt(), descriptionColor = 0xFF3A3A3A.toInt()), "i", "", null)
        assertEquals(0xFF3A3A3A.toInt(), light.selectedText)
        assertEquals(blend(0x993A3A3A.toInt(), 0xFFD6D6D6.toInt()), light.text)
        assertEquals(LINEAR.selectorWidth, carousel.selectorWidth)
        assertEquals(LINEAR.fontSize, carousel.fontSize)
        assertEquals(LINEAR.lineSpacing, carousel.lineSpacing)
    }

    @Test
    fun `font path rewritten under themes folder`() {
        val look = look(linear, "linear-es-de-copy", "dark", null)
        assertEquals("themes/linear-es-de-copy/assets/fonts/regular.ttf", look.fontPath)
        assertEquals("themes/linear-es-de-copy/assets/fonts/light.ttf", look.bodyFontPath)
        assertNull(look(linear.copy(fontPath = null, descriptionFontPath = null), "t", "", null).bodyFontPath)
    }

    private fun overrides(xml: String) = overlayTheme(xml.byteInputStream())

    @Test
    fun `override of one key keeps the rest`() {
        val base = look(minui, "minui", "dark-bold", null)
        val changed = base.with(overrides("<overlay><background>102030</background></overlay>"))
        assertEquals(base.copy(background = 0xFF102030.toInt(), source = LookSource.File(base.source)), changed)
        assertEquals(LookSource.File(LINEAR.source), LINEAR.with(emptyMap()).source)
    }

    @Test
    fun `override of every key`() {
        val xml = """
            <?xml version="1.0"?>
            <overlay>
                <background>010101</background>
                <text>020202</text>
                <selectedText>030303</selectedText>
                <selectedBackground>04040480</selectedBackground>
                <selectedCornerRadius>0.02</selectedCornerRadius>
                <selector>050505</selector>
                <selectorWidth>0.01</selectorWidth>
                <selectorCornerRadius>0.3</selectorCornerRadius>
                <helpBackground>060606</helpBackground>
                <helpIcon>070707</helpIcon>
                <helpText>080808</helpText>
                <fontPath>fonts/list.ttf</fontPath>
                <fontSize>0.05</fontSize>
                <lineSpacing>1.7</lineSpacing>
                <bodyText>090909</bodyText>
                <bodyFontPath>fonts/body.otf</bodyFontPath>
                <bodyFontSize>0.04</bodyFontSize>
                <bodyLineSpacing>1.6</bodyLineSpacing>
                <artCornerRadius>0.02</artCornerRadius>
            </overlay>
        """.trimIndent()
        assertEquals(
            Look(
                background = 0xFF010101.toInt(), text = 0xFF020202.toInt(), selectedText = 0xFF030303.toInt(),
                selectedBackground = 0x80040404.toInt(), selectedCornerRadius = 0.02f, selector = 0xFF050505.toInt(),
                selectorWidth = 0.01f, selectorCornerRadius = 0.3f, helpBackground = 0xFF060606.toInt(), helpIcon = 0xFF070707.toInt(),
                helpText = 0xFF080808.toInt(), fontPath = "fonts/list.ttf", fontSize = 0.05f, lineSpacing = 1.7f,
                bodyText = 0xFF090909.toInt(), bodyFontPath = "fonts/body.otf", bodyFontSize = 0.04f,
                bodyLineSpacing = 1.6f, artCornerRadius = 0.02f, source = LookSource.File(LINEAR.source),
            ),
            LINEAR.with(overrides(xml)),
        )
    }

    @Test
    fun `text scale override scales both font sizes`() {
        val scaled = LINEAR.with(overrides("<overlay><textScale>150</textScale></overlay>"))
        assertEquals(LINEAR.fontSize * 1.5f, scaled.fontSize, 0.00001f)
        assertEquals(LINEAR.bodyFontSize * 1.5f, scaled.bodyFontSize, 0.00001f)
        // On top of sizes the file sets itself.
        assertEquals(0.06f, LINEAR.with(overrides("<overlay><fontSize>0.04</fontSize><textScale>150</textScale></overlay>")).fontSize, 0.00001f)
        assertEquals(LINEAR.fontSize, LINEAR.with(overrides("<overlay><textScale>big</textScale></overlay>")).fontSize)
        assertEquals(LINEAR.fontSize, LINEAR.with(overrides("<overlay><textScale>-50</textScale></overlay>")).fontSize)
    }

    @Test
    fun `unknown keys and bad colours ignored`() {
        val xml = """
            <overlay>
                <wallpaper>FF0000</wallpaper>
                <text>red</text>
                <selector>12345</selector>
                <fontSize>big</fontSize>
                <fontPath>../../../data/x.ttf</fontPath>
                <bodyFontPath>/sdcard/x.ttf</bodyFontPath>
                <helpText>ABCDEF</helpText>
            </overlay>
        """
        assertEquals(LINEAR.copy(helpText = 0xFFABCDEF.toInt(), source = LookSource.File(LINEAR.source)), LINEAR.with(overrides(xml)))
        // A file that isn't an <overlay>, or not XML, overrides nothing.
        assertEquals(emptyMap<String, String>(), overrides("<theme><text>FFFFFF</text></theme>"))
        assertEquals(emptyMap<String, String>(), overrides("not xml"))
    }
}
