package com.clintonium.es_de_overlay.core

import java.io.Writer
import java.util.Base64

/** One page of a saved web guide: its title in the contents list and its cleaned, script-free HTML. */
data class GuideSection(val title: String, val html: String)

/** An image embedded in a saved guide, keyed in [writeHtmlGuide] by the `src` its section uses. */
class GuideImage(val mime: String, val bytes: ByteArray)

/** A plain-text page kept in `<pre>`, so its ASCII art keeps its columns. */
fun textSection(title: String, text: String) = GuideSection(title, "<pre>${escapeHtml(text)}</pre>")

fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** [text] with numeric character references and the common named ones decoded; others are left as written. */
fun unescapeHtml(text: String): String = ENTITY.replaceEach(text) { m ->
    val ref = m.group(1)
    val code = when {
        ref.startsWith("#x", ignoreCase = true) -> ref.drop(2).toIntOrNull(16)
        ref.startsWith("#") -> ref.drop(1).toIntOrNull()
        else -> null
    }
    code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: NAMED_ENTITIES[ref] ?: m.group()
}

private val ENTITY = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

private val IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMG_SRC = Regex("\\s(src|srcset)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE)

/**
 * Writes a guide as one self-contained HTML file: a contents list linking `#s1` … `#sN`, one
 * `<section id="sK">` per page, [images] inlined as `data:` URIs and the [credit] HTML as the footer. Colours
 * and fonts come from the `guide.css` the viewer serves. An `<img>` whose `src` isn't in [images] loses its
 * `src` (and any `srcset`), so the file never loads anything from the network. Streams to [out], so large
 * images aren't held twice.
 */
fun writeHtmlGuide(
    out: Writer,
    title: String,
    sections: List<GuideSection>,
    images: Map<String, GuideImage>,
    credit: String,
) {
    val name = escapeHtml(title)
    out.write("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>$name</title>")
    out.write("<link rel=\"stylesheet\" href=\"guide.css\"></head>\n<body>\n<h1>$name</h1>\n")
    out.write("<nav id=\"contents\"><ol>\n")
    sections.forEachIndexed { i, s -> out.write("<li><a href=\"#s${i + 1}\">${escapeHtml(s.title)}</a></li>\n") }
    out.write("</ol></nav>\n")
    sections.forEachIndexed { i, s ->
        out.write("<section id=\"s${i + 1}\"><h2>${escapeHtml(s.title)}</h2>\n")
        var at = 0
        for (img in IMG.scan(s.html)) {
            out.write(s.html, at, img.start() - at)
            val tag = img.group()
            val key = IMG_SRC.findAll(tag).firstOrNull { it.groupValues[1].equals("src", true) }
                ?.groupValues?.get(2)?.trim('"', '\'')
            val rest = tag.replace(IMG_SRC, "")
            val image = key?.let { images[it] }
            if (image == null) {
                out.write(rest)
            } else {
                out.write("<img src=\"data:${image.mime};base64,")
                out.write(Base64.getEncoder().encodeToString(image.bytes))
                out.write("\"" + rest.substring("<img".length))
            }
            at = img.end()
        }
        out.write(s.html, at, s.html.length - at)
        out.write("\n</section>\n")
    }
    out.write("<footer>$credit</footer>\n</body></html>\n")
}
