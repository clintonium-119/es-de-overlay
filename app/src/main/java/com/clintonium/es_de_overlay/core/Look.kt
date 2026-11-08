package com.clintonium.es_de_overlay.core

import java.io.InputStream

/**
 * Where a [Look] comes from: the active ES-DE theme (by the names ES-DE's menus show), the built-in default, or
 * overlay-theme.xml over either.
 */
sealed class LookSource {
    data class Theme(val name: String, val scheme: String) : LookSource()
    data class Default(val reason: String) : LookSource()
    data class File(val underlying: LookSource) : LookSource()
}

/**
 * Everything the overlay draws with. Colours are ARGB; font sizes are fractions of the screen height, corner radii and
 * [selectorWidth] of its width (a null width is a full-row selector, a null [artCornerRadius] the overlay's own);
 * line spacings are multiples of the font size, [selectorCornerRadius] a fraction of the row's height;
 * font paths are relative to the ES-DE folder, null for sans-serif.
 */
data class Look(
    val background: Int,
    val text: Int,
    val selectedText: Int,
    val selectedBackground: Int,
    val selectedCornerRadius: Float,
    val selector: Int,
    val selectorWidth: Float?,
    val selectorCornerRadius: Float,
    val helpBackground: Int,
    val helpIcon: Int,
    val helpText: Int,
    val fontPath: String?,
    val fontSize: Float,
    val lineSpacing: Float,
    val bodyText: Int,
    val bodyFontPath: String?,
    val bodyFontSize: Float,
    val bodyLineSpacing: Float,
    val artCornerRadius: Float?,
    val source: LookSource,
) {
    /**
     * The selector's width when it's a thin bar at the row's left, like Linear's; null when it covers the row. A wider
     * selectorWidth sizes the theme's own list, which clips its text to it, so in the overlay it covers the row.
     */
    val thinSelector: Float? get() = selectorWidth?.takeIf { it < 0.05f }
}

/** ES-DE's bundled Linear theme, dark scheme, medium font size; its fonts are inside ES-DE's APK, so sans-serif. */
val LINEAR = Look(
    background = 0xFF373737.toInt(),
    text = 0xFF777777.toInt(),
    selectedText = 0xFFFFFFFF.toInt(),
    selectedBackground = 0x00000000,
    selectedCornerRadius = 0f,
    selector = 0xFFFFFFFF.toInt(),
    selectorWidth = 0.003f,
    selectorCornerRadius = 0f,
    helpBackground = 0xFF000000.toInt(),
    helpIcon = 0xFFFAFAFA.toInt(),
    helpText = 0xFFFAFAFA.toInt(),
    fontPath = null,
    fontSize = 0.035f,
    lineSpacing = 1.498f,
    bodyText = 0xFFFAFAFA.toInt(),
    bodyFontPath = null,
    bodyFontSize = 0.032f,
    bodyLineSpacing = 1.2f,
    artCornerRadius = 0f,
    source = LookSource.Default("built in"),
)

/**
 * The look of [style], resolved from the theme folder `themes/[theme]` with colour scheme [scheme]; [LINEAR] with
 * [reason] when there is no style. A field the theme leaves out takes ES-DE's default; a gamelist without a text list
 * takes Linear's list in the theme's text colour. The guide text follows the description text, else the list.
 */
fun look(style: ThemeStyle?, theme: String, scheme: String, reason: String?): Look {
    if (style == null) return LINEAR.copy(source = LookSource.Default(reason ?: "theme.xml couldn't be read"))
    val hasList = listOf(style.primaryColor, style.selectedColor, style.selectorColor, style.fontSize).any { it != null }
    fun path(themePath: String?) = themePath?.let { "themes/$theme/$it" }
    // ES-DE draws on black; a panel under the list covers the screen background where the list is.
    val screen = blend(style.background ?: BLACK, BLACK)
    val background = style.listBackground?.let { blend(it, screen) } ?: screen
    // Without a text list, Linear's list in the theme's text colour: the selected row and its bar in it, the others
    // fainter, so it reads on a light theme as on a dark one.
    val themeText = style.descriptionColor ?: LINEAR.bodyText
    val text = style.primaryColor ?: if (hasList) ES_DE_LIST else blend(themeText and 0xFFFFFF or (0x99 shl 24), background)
    val selectedText = style.selectedColor ?: if (hasList) ES_DE_LIST else themeText
    val selectedBackground = style.selectedBackgroundColor ?: 0
    val selector = (style.selectorColor ?: if (hasList) ES_DE_SELECTOR else themeText).let {
        // With neither a selector nor a pill to see, and the selected text looking the same, nothing shows the
        // cursor; a faint bar of the text colour does.
        if (alpha(it) == 0 && alpha(selectedBackground) == 0 && selectedText == text) text and 0xFFFFFF or (0x40 shl 24)
        else it
    }
    val fontSize = style.fontSize ?: if (hasList) 0.045f else LINEAR.fontSize
    return Look(
        background = background,
        text = text,
        selectedText = selectedText,
        selectedBackground = selectedBackground,
        selectedCornerRadius = style.selectedBackgroundCornerRadius ?: 0f,
        selector = selector,
        selectorWidth = if (hasList) style.selectorWidth else LINEAR.selectorWidth,
        selectorCornerRadius = if (hasList) style.selectorCornerRadius ?: 0f else LINEAR.selectorCornerRadius,
        helpBackground = style.helpBackgroundColor ?: 0x00000000,
        helpIcon = style.helpIconColor ?: ES_DE_HELP,
        helpText = style.helpTextColor ?: ES_DE_HELP,
        fontPath = path(style.fontPath),
        fontSize = fontSize,
        lineSpacing = style.lineSpacing ?: if (hasList) 1.5f else LINEAR.lineSpacing,
        bodyText = style.descriptionColor ?: text,
        bodyFontPath = path(style.descriptionFontPath ?: style.fontPath),
        bodyFontSize = style.descriptionFontSize ?: fontSize,
        bodyLineSpacing = style.descriptionLineSpacing ?: ES_DE_TEXT_SPACING,
        artCornerRadius = style.artCornerRadius,
        source = LookSource.Theme(style.themeName ?: theme, style.colorSchemeLabel ?: scheme),
    )
}

/** The `<overlay>` children of an overlay-theme.xml by tag; empty when the file isn't one. */
fun overlayTheme(xml: InputStream): Map<String, String> =
    xmlDocument(xml.reader().readText())?.documentElement?.takeIf { it.tagName == "overlay" }?.let { root ->
        (0 until root.childNodes.length).map { root.childNodes.item(it) }
            .filterIsInstance<org.w3c.dom.Element>()
            .associate { it.tagName to it.textContent.trim() }
    }.orEmpty()

/**
 * This look with the overlay-theme.xml values in [overrides] in place of its own, keyed by field name: colours as
 * ES-DE writes them, numbers, and font paths relative to the ES-DE folder. Unknown keys and values that don't parse
 * are left out.
 */
fun Look.with(overrides: Map<String, String>): Look {
    fun color(key: String, now: Int) = overrides[key]?.let(::themeColor) ?: now
    fun number(key: String, now: Float) = overrides[key]?.toFloatOrNull() ?: now
    // Below the ES-DE folder only: no absolute paths and no way up out of it.
    fun path(key: String, now: String?) =
        overrides[key]?.takeIf { p -> p.isNotEmpty() && !p.startsWith("/") && p.split("/").none { it == ".." } } ?: now
    return copy(
        background = color("background", background),
        text = color("text", text),
        selectedText = color("selectedText", selectedText),
        selectedBackground = color("selectedBackground", selectedBackground),
        selectedCornerRadius = number("selectedCornerRadius", selectedCornerRadius),
        selector = color("selector", selector),
        selectorWidth = overrides["selectorWidth"]?.toFloatOrNull() ?: selectorWidth,
        selectorCornerRadius = number("selectorCornerRadius", selectorCornerRadius),
        helpBackground = color("helpBackground", helpBackground),
        helpIcon = color("helpIcon", helpIcon),
        helpText = color("helpText", helpText),
        fontPath = path("fontPath", fontPath),
        fontSize = number("fontSize", fontSize),
        lineSpacing = number("lineSpacing", lineSpacing),
        bodyText = color("bodyText", bodyText),
        bodyFontPath = path("bodyFontPath", bodyFontPath),
        bodyFontSize = number("bodyFontSize", bodyFontSize),
        bodyLineSpacing = number("bodyLineSpacing", bodyLineSpacing),
        artCornerRadius = overrides["artCornerRadius"]?.toFloatOrNull() ?: artCornerRadius,
        source = LookSource.File(source),
    ).let { look ->
        // A percentage on top of the sizes above, as the Text size setting is.
        overrides["textScale"]?.toFloatOrNull()?.takeIf { it > 0f }?.let { look.copy(fontSize = look.fontSize * it / 100f, bodyFontSize = look.bodyFontSize * it / 100f) }
            ?: look
    }
}

private const val BLACK = 0xFF000000.toInt()
// ES-DE's textlist colours when a theme leaves them out (TextListComponent.h), and its help system's
// (HelpComponent.cpp).
private const val ES_DE_LIST = 0xFF0000FF.toInt()
private const val ES_DE_SELECTOR = 0xFF333333.toInt()
private const val ES_DE_HELP = 0xFF777777.toInt()
// ES-DE's line spacing for text a theme doesn't space (TextComponent.cpp).
private const val ES_DE_TEXT_SPACING = 1.5f

private fun alpha(color: Int) = color ushr 24
