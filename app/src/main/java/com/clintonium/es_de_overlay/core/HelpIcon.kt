package com.clintonium.es_de_overlay.core

/** One shape of an ES-DE help icon as SVG path data in its viewBox: filled, or stroked [stroke] wide; at [opacity]. */
data class IconShape(val pathData: String, val stroke: Float?, val opacity: Float)

/** An ES-DE help icon: its [shapes] in a [size]-wide square viewBox, drawn white for the theme's icon colour to tint. */
data class HelpIcon(val size: Float, val shapes: List<IconShape>)

/**
 * The circles and paths of ES-DE's help icon [svg], in order; null when it has neither. ES-DE's icons for the buttons
 * and the D-pad use nothing else: no groups or transforms. A circle becomes two arcs.
 */
fun helpIcon(svg: String): HelpIcon? {
    val size = Regex("""viewBox="[-0-9.]+[ ,]+[-0-9.]+[ ,]+([0-9.]+)""").find(svg)?.groupValues?.get(1)?.toFloatOrNull()
        ?: Regex("""<svg\b[^>]*\swidth="([0-9.]+)""").find(svg)?.groupValues?.get(1)?.toFloatOrNull()
        ?: return null
    val shapes = Regex("""<(circle|path)\b[^>]*>""").findAll(svg).mapNotNull { match ->
        val element = match.value
        fun number(name: String) = Regex("""\s$name="([-0-9.]+)"""").find(element)?.groupValues?.get(1)?.toFloatOrNull()
        val data = if (match.groupValues[1] == "path") {
            Regex("""\sd="([^"]*)"""").find(element)?.groupValues?.get(1)?.trim()
        } else {
            val (cx, cy, r) = listOf("cx", "cy", "r").map { number(it) ?: return@mapNotNull null }
            "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
        } ?: return@mapNotNull null
        val fill = svgAttribute(element, "fill")
        val stroke = svgAttribute(element, "stroke")?.takeIf { it != "none" }
        val opacity = listOf("fill-opacity", "opacity").fold(1f) { a, name -> a * (svgAttribute(element, name)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f) }
        when {
            // SVG fills a shape that names no fill.
            fill != "none" -> IconShape(data, null, opacity)
            stroke != null -> IconShape(data, svgAttribute(element, "stroke-width")?.toFloatOrNull() ?: 1f, opacity)
            else -> null
        }
    }.toList()
    return HelpIcon(size, shapes).takeIf { shapes.isNotEmpty() }
}

/**
 * The file name (without `.svg`) of ES-DE's help icon for its prompt [prompt]: `a`, `b`, `x` or `y` as [controllerType]'s
 * pictures draw it, the other of the pair with [swap], as ES-DE's help shows them; `left/right` and `up/down` for the
 * D-pad. Null for anything else.
 */
fun helpIconName(prompt: String, swap: Boolean, controllerType: String): String? = when (prompt) {
    "left/right" -> "dpad_leftright"
    "up/down" -> "dpad_updown"
    else -> "abxy".indexOf(prompt).takeIf { prompt.length == 1 && it >= 0 }?.let { i ->
        val type = when {
            controllerType.startsWith("ps") -> "PS"
            "snes" in controllerType -> "SNES"
            controllerType.startsWith("switch") -> "switch"
            else -> "XBOX"
        }
        "button_${"abxy"[if (swap) i xor 1 else i]}_$type"
    }
}
