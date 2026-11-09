package com.clintonium.es_de_overlay.core

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import org.w3c.dom.Element

/** The theme keys of ES-DE's es_settings.xml; a missing key takes ES-DE's default (`Settings.cpp`). */
data class ThemeSettings(
    val theme: String = "linear-es-de",
    val variant: String = "",
    val colorScheme: String = "",
    val fontSize: String = "",
    val aspectRatio: String = "automatic",
)

/** The theme keys of an es_settings.xml, whose `<string name="…" value="…"/>` entries have no root element. */
fun themeSettings(xml: InputStream): ThemeSettings {
    val body = xml.reader().readText().removePrefix("\uFEFF").replaceFirst(Regex("""^\s*<\?xml[^?]*\?>"""), "")
    val values = xmlDocument("<root>$body</root>")?.documentElement
        ?.let { root -> root.children("string").associate { it.getAttribute("name") to it.getAttribute("value") } }
        .orEmpty()
    val defaults = ThemeSettings()
    return ThemeSettings(
        theme = values["Theme"] ?: defaults.theme,
        variant = values["ThemeVariant"] ?: defaults.variant,
        colorScheme = values["ThemeColorScheme"] ?: defaults.colorScheme,
        fontSize = values["ThemeFontSize"] ?: defaults.fontSize,
        aspectRatio = values["ThemeAspectRatio"] ?: defaults.aspectRatio,
    )
}

/**
 * What a theme draws in the gamelist, as far as the overlay copies it; a field is null when the theme doesn't set it.
 * [background] is the colour the theme's full-screen images make over ES-DE's black, [listBackground] that of an image
 * the list sits on, a panel smaller than the screen; [helpBackgroundColor] is the help prompts' own colour, else that of
 * a panel under them. Colours are ARGB; sizes are fractions of the screen height, corner radii of its width; font paths are relative to
 * the theme folder. [themeName] and [colorSchemeLabel] are what ES-DE's menus call the theme and the colour scheme
 * in use.
 */
data class ThemeStyle(
    val background: Int? = null,
    val listBackground: Int? = null,
    val fontPath: String? = null,
    val fontSize: Float? = null,
    val lineSpacing: Float? = null,
    val primaryColor: Int? = null,
    val selectedColor: Int? = null,
    val selectorColor: Int? = null,
    val selectorWidth: Float? = null,
    val selectorCornerRadius: Float? = null,
    val selectedBackgroundColor: Int? = null,
    val selectedBackgroundCornerRadius: Float? = null,
    val descriptionFontPath: String? = null,
    val descriptionFontSize: Float? = null,
    val descriptionColor: Int? = null,
    val descriptionLineSpacing: Float? = null,
    val helpTextColor: Int? = null,
    val helpIconColor: Int? = null,
    val helpBackgroundColor: Int? = null,
    val helpFontPath: String? = null,
    val artCornerRadius: Float? = null,
    val themeName: String? = null,
    val colorSchemeLabel: String? = null,
)

/**
 * The gamelist style ES-DE draws for [system] with the theme whose files [read] returns by theme-relative path
 * (null when missing), or null when the theme has no readable theme.xml. [screenAspect] is width / height, used when
 * the aspect ratio setting is automatic; [mediaType] is the downloaded_media folder whose image the art box shows.
 */
fun resolveTheme(
    read: (String) -> String?,
    settings: ThemeSettings,
    system: String,
    screenAspect: Float,
    mediaType: String,
): ThemeStyle? {
    val caps = read("capabilities.xml")?.let(::xmlDocument)?.documentElement
    fun listed(tag: String) = caps?.children(tag)?.map { it.textContent.trim() }.orEmpty()
    fun named(tag: String) = caps?.children(tag)?.map { it.getAttribute("name") }?.filter { it.isNotEmpty() && it != "all" }.orEmpty()
    // As ES-DE's loader picks them: the setting when the theme lists it, else the first entry (font sizes in ES-DE's
    // own order), and the listed aspect ratio nearest the screen when that setting is automatic or unlisted.
    fun pick(options: List<String>, setting: String) = if (setting in options) setting else options.firstOrNull()
    val sizes = listed("fontSize")
    val ratios = listed("aspectRatio").filter { it in ASPECT_RATIOS }
    val aspect = when {
        ratios.isEmpty() -> null
        settings.aspectRatio in ratios -> settings.aspectRatio
        else -> ratios.fold("16:9") { best, r ->
            if (abs(ASPECT_RATIOS[r]!! - screenAspect) < abs(ASPECT_RATIOS[best]!! - screenAspect)) r else best
        }
    }
    val scheme = pick(named("colorScheme"), settings.colorScheme)
    val resolver = Resolver(
        read,
        variant = pick(named("variant"), settings.variant),
        colorScheme = scheme,
        fontSize = pick(FONT_SIZES.filter { it in sizes }, settings.fontSize),
        aspectRatio = aspect,
        variables = mapOf("system.name" to system, "system.theme" to system),
    )
    // A system's own theme.xml replaces the theme's root one.
    val files = listOfNotNull(system.takeIf { it.isNotEmpty() }?.let { "$it/theme.xml" }, "theme.xml")
    if (files.none(resolver::load)) return null

    val elements = resolver.gamelist
    fun of(type: String) = elements.filterKeys { it.startsWith("${type}_") }.values
    val list = of("textlist").firstOrNull().orEmpty()
    val description = of("text").firstOrNull { it["metadata"] == "description" }.orEmpty()
    // The gamelist's own help, not the one ES-DE's menus use or one switched off there.
    val help = of("helpsystem").firstOrNull { it["scope"] != "menu" && it["scope"] != "none" }.orEmpty()
    // An image's colour as ES-DE draws it, at its opacity: its color, else the fill of an SVG picture. A raster
    // picture without a color gives no colour to copy.
    fun painted(image: Map<String, String>) =
        (image["color"]?.let(::themeColor) ?: image["path"]?.takeIf { it.endsWith(".svg", ignoreCase = true) }?.let(read)?.let(::svgFill))
            ?.let { color ->
                val opacity = image["opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                ((color ushr 24) * opacity).roundToInt() shl 24 or (color and 0xFFFFFF)
            }
    // The full-screen images ES-DE draws over its black, the lower zIndex first and equal ones in the theme's order.
    val screen = of("image")
        .filter { image -> painted(image) != null && listOf("size", "cropSize").any { image[it]?.let(::pair) == listOf(1f, 1f) } }
        .sortedBy { it.zIndex(IMAGE_Z) }
    // The topmost coloured image below [zIndex], smaller than the screen, whose box holds the point (x, y).
    fun panelAt(x: Float, y: Float, zIndex: Float) = of("image").filter { image ->
        painted(image) != null && image.zIndex(IMAGE_Z) < zIndex &&
            box(image)?.let { (l, t, w, h) -> (w < 1f || h < 1f) && x in l..l + w && y in t..t + h } == true
    }.maxByOrNull { it.zIndex(IMAGE_Z) }
    val panel = box(list)?.let { (left, top, width, height) -> panelAt(left + width / 2, top + height / 2, list.zIndex(TEXTLIST_Z)) }
    // ES-DE draws the help prompts above everything, so any image under their position.
    val helpPanel = help["pos"]?.let(::pair)?.let { (x, y) -> panelAt(x, y, Float.MAX_VALUE) }
    val imageType = when (mediaType) {
        "3dboxes" -> "3dbox"
        "fanart", "physicalmedia" -> mediaType
        else -> mediaType.removeSuffix("s")
    }
    val arts = (of("image") + of("video")).filter { it["imageType"] != null }
    val art = arts.firstOrNull { imageType in names(it["imageType"]!!) } ?: arts.firstOrNull()
    return ThemeStyle(
        background = screen.takeIf { it.isNotEmpty() }?.fold(BLACK) { below, image -> blend(painted(image)!!, below) },
        listBackground = panel?.let(::painted),
        fontPath = list["fontPath"],
        fontSize = list["fontSize"]?.toFloatOrNull(),
        lineSpacing = list["lineSpacing"]?.toFloatOrNull(),
        primaryColor = list["primaryColor"]?.let(::themeColor),
        selectedColor = list["selectedColor"]?.let(::themeColor),
        selectorColor = list["selectorColor"]?.let(::themeColor),
        selectorWidth = list["selectorWidth"]?.toFloatOrNull(),
        selectorCornerRadius = list["selectorImagePath"]?.takeIf { it.endsWith(".svg", ignoreCase = true) }?.let(read)?.let(::svgCornerRadius),
        selectedBackgroundColor = list["selectedBackgroundColor"]?.let(::themeColor),
        selectedBackgroundCornerRadius = list["selectedBackgroundCornerRadius"]?.toFloatOrNull(),
        descriptionFontPath = description["fontPath"],
        descriptionFontSize = description["fontSize"]?.toFloatOrNull(),
        descriptionColor = description["color"]?.let(::themeColor),
        descriptionLineSpacing = description["lineSpacing"]?.toFloatOrNull(),
        helpTextColor = help["textColor"]?.let(::themeColor),
        helpIconColor = help["iconColor"]?.let(::themeColor),
        helpBackgroundColor = help["backgroundColor"]?.let(::themeColor) ?: helpPanel?.let(::painted),
        helpFontPath = help["fontPath"],
        artCornerRadius = art?.let { it["cornerRadius"] ?: it["imageCornerRadius"] }?.toFloatOrNull(),
        themeName = caps?.children("themeName")?.firstOrNull()?.textContent?.trim()?.ifEmpty { null },
        colorSchemeLabel = caps?.children("colorScheme")?.firstOrNull { it.getAttribute("name") == scheme }?.let { entry ->
            // The English label: ES-DE reads one without a language as en_US. Else the scheme's name.
            val labels = entry.children("label")
            (labels.firstOrNull { it.getAttribute("language") in setOf("", "en_US") } ?: labels.firstOrNull())
                ?.textContent?.trim()?.ifEmpty { null } ?: scheme
        },
    )
}

/**
 * The colour of an SVG picture: the fill of its first shape filled in a plain colour, with that shape's fill-opacity and
 * opacity; null when no shape is (none, gradients, no shapes).
 */
fun svgFill(svg: String): Int? {
    val shape = filledShape(svg) ?: return null
    // SVG fills a shape black when it names no fill.
    val hex = svgAttribute(shape, "fill")?.removePrefix("#") ?: "000000"
    val rgb = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
    val alpha = listOf("fill-opacity", "opacity").fold(1f) { a, name -> a * (svgAttribute(shape, name)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f) }
    return (alpha * 255).roundToInt() shl 24 or rgb.toInt(16)
}

// A shape's attribute or style property.
private fun svgAttribute(shape: String, name: String) =
    Regex("""(?:^|[\s;"'])$name\s*[:=]\s*["']?([^;"'\s>]+)""").find(shape)?.groupValues?.get(1)

// The first shape drawn in a plain colour (SVG's black when it names no fill): drawings often start with an unfilled
// box marking out the page.
private fun filledShape(svg: String) = SHAPE.findAll(svg).map { it.value }
    .firstOrNull { shape -> svgAttribute(shape, "fill").let { it == null || it.removePrefix("#").matches(Regex("[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}")) } }

/**
 * The corner radius of an SVG picture's first plainly filled shape as a fraction of its height (at most a half): a
 * rect's rx, else the radius of the first arc in a path, over the shape's height or the picture's; null when neither is
 * there.
 */
fun svgCornerRadius(svg: String): Float? {
    val shape = filledShape(svg) ?: return null
    fun number(name: String, text: String = shape) = Regex("""\s$name="([0-9.]+)""").find(text)?.groupValues?.get(1)?.toFloatOrNull()
    val radius = number("rx") ?: Regex("""[aA]\s*([0-9.]+)[ ,]""").find(shape)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
    val height = number("height") ?: Regex("""viewBox="[-0-9.]+[ ,]+[-0-9.]+[ ,]+[0-9.]+[ ,]+([0-9.]+)"""").find(svg)?.groupValues?.get(1)?.toFloatOrNull()
    return height?.takeIf { it > 0f }?.let { (radius / it).coerceIn(0f, 0.5f) }
}

/** ARGB [top] drawn over the opaque [bottom]: opaque. */
fun blend(top: Int, bottom: Int): Int {
    val a = (top ushr 24) / 255f
    fun channel(shift: Int) = ((top shr shift and 0xFF) * a + (bottom shr shift and 0xFF) * (1 - a)).roundToInt() shl shift
    return BLACK or channel(16) or channel(8) or channel(0)
}

/** An ES-DE colour, `RRGGBB` or `RRGGBBAA`, as ARGB; null when it is neither. */
fun themeColor(value: String): Int? {
    val hex = value.trim().takeIf { it.matches(Regex("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}")) } ?: return null
    val rgba = hex.toLong(16)
    return if (hex.length == 6) (0xFF000000 or rgba).toInt() else ((rgba ushr 8) or (rgba and 0xFF shl 24)).toInt()
}

/**
 * Hands [write] each regular file of a tar stream (ustar, with its prefix field or GNU long names): how a theme is
 * copied from ES-DE's app storage, by one `tar` process rather than a process per file. A path that is absolute or
 * goes up with `..` is skipped; a file cut short ends the stream without being written.
 */
fun unpackFiles(stream: InputStream, write: (path: String, bytes: ByteArray) -> Unit) {
    val input = stream.buffered()
    var longName: String? = null
    while (true) {
        val header = input.readNBytes(512)
        if (header.size < 512 || header.all { it == 0.toByte() }) return
        fun field(at: Int, length: Int) = String(header, at, length, Charsets.UTF_8).substringBefore('\u0000')
        val size = field(124, 12).trim().toIntOrNull(8) ?: return
        val body = input.readNBytes(size)
        if (body.size < size) return
        // Entries take whole 512-byte blocks.
        input.readNBytes((512 - size % 512) % 512)
        val name = longName ?: field(345, 155).takeIf { field(257, 5) == "ustar" && it.isNotEmpty() }
            ?.let { "$it/${field(0, 100)}" } ?: field(0, 100)
        longName = null
        when (header[156].toInt().toChar()) {
            'L' -> longName = String(body, Charsets.UTF_8).substringBefore('\u0000')
            '0', '\u0000' -> if (!name.startsWith("/") && name.split("/").none { it == ".." }) write(name, body)
        }
    }
}

// ES-DE's supported aspect ratios as width / height (ThemeData.cpp's sAspectRatioMap).
private val ASPECT_RATIOS = mapOf(
    "16:9" to 1.7777f, "16:9_vertical" to 0.5625f, "16:10" to 1.6f, "16:10_vertical" to 0.625f,
    "3:2" to 1.5f, "3:2_vertical" to 0.6667f, "4:3" to 1.3333f, "4:3_vertical" to 0.75f,
    "5:3" to 1.6667f, "5:3_vertical" to 0.6f, "5:4" to 1.25f, "5:4_vertical" to 0.8f,
    "8:7" to 1.1429f, "8:7_vertical" to 0.875f, "19.5:9" to 2.1667f, "19.5:9_vertical" to 0.4615f,
    "20:9" to 2.2222f, "20:9_vertical" to 0.45f, "21:9" to 2.3703f, "21:9_vertical" to 0.4219f,
    "32:9" to 3.5555f, "32:9_vertical" to 0.2813f, "1:1" to 1.0f,
)

// ES-DE's font sizes in its own order, which is the order a theme's sizes are offered and defaulted in.
private val FONT_SIZES = listOf("medium", "large", "small", "x-large", "x-small")

// Includes deeper than this are skipped, as a loop or runaway nesting.
private const val MAX_INCLUDE_DEPTH = 8

private enum class Part { VARIABLES, COLOR_SCHEMES, FONT_SIZES, INCLUDES, VIEWS, VARIANTS, ASPECT_RATIOS }

// What ES-DE parses, and in which order, in a theme file, a variant, an aspect ratio, and a colour scheme or font
// size block. Later definitions win, so this order, not the document's, decides between them.
private val FILE = Part.entries
private val VARIANT = FILE - Part.VARIANTS
private val ASPECT_RATIO = VARIANT - Part.ASPECT_RATIOS
private val SELECTION = listOf(Part.VARIABLES, Part.INCLUDES)

/** ES-DE's ThemeData::loadFile for the gamelist view, keeping each element's properties by `type_name`. */
private class Resolver(
    val read: (String) -> String?,
    val variant: String?,
    val colorScheme: String?,
    val fontSize: String?,
    val aspectRatio: String?,
    variables: Map<String, String>,
) {
    val gamelist = LinkedHashMap<String, MutableMap<String, String>>()
    private val variables = variables.toMutableMap()
    private val files = ArrayDeque<String>()

    /** Parses the theme file at [path]; false when it can't be read or isn't a `<theme>`. */
    fun load(path: String): Boolean {
        if (path in files || files.size > MAX_INCLUDE_DEPTH) return false
        val theme = read(path)?.let(::xmlDocument)?.documentElement?.takeIf { it.tagName == "theme" } ?: return false
        files.addLast(path)
        parse(theme, FILE)
        files.removeLast()
        return true
    }

    private fun parse(node: Element, parts: List<Part>): Unit = parts.forEach { part ->
        when (part) {
            Part.VARIABLES -> node.children("variables").flatMap { it.children() }.forEach { variable ->
                substitute(variable.textContent.trim()).takeIf { it.isNotEmpty() }?.let { variables[variable.tagName] = it }
            }
            Part.COLOR_SCHEMES -> node.selected("colorScheme", colorScheme).forEach { parse(it, SELECTION) }
            Part.FONT_SIZES -> node.selected("fontSize", fontSize).forEach { parse(it, SELECTION) }
            Part.INCLUDES -> node.children("include").forEach { include ->
                relative(substitute(include.textContent.trim()))?.let(::load)
            }
            Part.VIEWS -> node.children("view").filter { "gamelist" in names(it.getAttribute("name")) }.forEach(::view)
            Part.VARIANTS -> node.selected("variant", variant, all = true).forEach { parse(it, VARIANT) }
            Part.ASPECT_RATIOS -> node.selected("aspectRatio", aspectRatio).forEach { parse(it, ASPECT_RATIO) }
        }
    }

    // The blocks entered for the selected entry; none when the theme offers no such choice.
    private fun Element.selected(tag: String, selection: String?, all: Boolean = false) =
        if (selection == null) emptyList()
        else children(tag).filter { names(it.getAttribute("name")).any { name -> name == selection || all && name == "all" } }

    private fun view(view: Element) = view.children().forEach { element ->
        names(element.getAttribute("name")).forEach { name ->
            val properties = gamelist.getOrPut("${element.tagName}_$name") { mutableMapOf() }
            element.children().forEach { property ->
                val value = substitute(property.textContent.trim())
                // A path is relative to the file it is written in, not the theme folder.
                val resolved = if (property.tagName in PATHS) relative(value) else value
                // ES-DE marks a system variable that doesn't apply with a backspace and skips the property.
                if (!resolved.isNullOrEmpty() && resolved != "\b") properties[property.tagName] = resolved
            }
        }
    }

    // ES-DE replaces ${name} with the variable's value, or nothing when it isn't defined.
    private fun substitute(value: String) = Regex("""\$\{([^}]*)\}""").replace(value) { variables[it.groupValues[1]].orEmpty() }

    // A `./` path (`./../x` included) against the directory of the file being parsed, relative to the theme folder;
    // null for other paths (absolute, `~/`, ES-DE's own `:/` resources) and for one that leaves the theme folder.
    private fun relative(path: String): String? {
        if (!path.startsWith("./")) return null
        val segments = files.last().split("/").dropLast(1).toMutableList()
        for (segment in path.split("/")) when (segment) {
            "", "." -> {}
            ".." -> segments.removeLastOrNull() ?: return null
            else -> segments += segment
        }
        return segments.joinToString("/")
    }
}

// ES-DE's name lists: separated by commas and whitespace, newlines included.
private fun names(list: String) = list.split(',', ' ', '\t', '\r', '\n').filter { it.isNotEmpty() }

// An ES-DE normalized pair, `x y`; null when it isn't two numbers.
private fun pair(value: String) =
    value.trim().split(Regex("\\s+")).map { it.toFloatOrNull() ?: return null }.takeIf { it.size == 2 }

private const val BLACK = 0xFF000000.toInt()

// The properties whose values are file paths, relative to the theme file they're written in.
private val PATHS = setOf("fontPath", "path", "selectorImagePath")

// An SVG's drawing elements, opening tag only.
private val SHAPE = Regex("""<(?:path|rect|circle|ellipse|polygon)\b[^>]*>""")

// ES-DE's default zIndex for an image and for a textlist.
private const val IMAGE_Z = 30f
private const val TEXTLIST_Z = 50f

private fun Map<String, String>.zIndex(default: Float) = this["zIndex"]?.toFloatOrNull() ?: default

// An element's box as left, top, width and height in screen fractions, from its pos, size and origin (ES-DE's pos
// and origin default to 0 0); null without a size.
private fun box(element: Map<String, String>): List<Float>? {
    val (width, height) = element["size"]?.let(::pair) ?: return null
    val (x, y) = element["pos"]?.let(::pair) ?: listOf(0f, 0f)
    val (originX, originY) = element["origin"]?.let(::pair) ?: listOf(0f, 0f)
    return listOf(x - originX * width, y - originY * height, width, height)
}

private fun Element.children(tag: String? = null): List<Element> =
    (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { tag == null || it.tagName == tag }
