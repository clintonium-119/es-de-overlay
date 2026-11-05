package com.clintonium.es_de_overlay.core

import java.io.StringWriter
import java.io.Writer
import java.util.Base64

/**
 * One page of a saved web guide: its title in the contents list and its cleaned, script-free HTML, under the
 * title as a heading unless the HTML holds its own ([heading] false).
 */
data class GuideSection(val title: String, val html: String, val heading: Boolean = true)

/** An image embedded in a saved guide, keyed in [writeHtmlGuide] by the `src` its section uses. */
class GuideImage(val mime: String, val bytes: ByteArray)

/** A plain-text page kept in `<pre>`, so its ASCII art keeps its columns. */
fun textSection(title: String, text: String) = GuideSection(title, "<pre>${escapeHtml(text)}</pre>")

/**
 * A plain-text guide as sections, each a `<pre>` holding its own heading lines, so the text reads as one. It
 * splits at the lines a contents list near the top points at by its tags ("[INTRO]", "(B)", "1.", "5.3"), else
 * at lines boxed, overlined or underlined by a rule (20 or more of one of `= - * # ~ _`). Text before the first
 * heading is a section named [title]. A text with fewer than two headings stays one section.
 */
fun textSections(title: String, text: String): List<GuideSection> {
    val lines = text.lines()
    val headings = contentsHeadings(lines) ?: ruleHeadings(lines)
    if (headings.size < 2) return listOf(textSection(title, text))
    // A heading's box, or a rule above it, goes with it.
    var floor = 0
    val starts = headings.map { (line, name) ->
        var start = line
        while (start > floor && lines[start - 1].isNotBlank() && lines[start - 1].none(Char::isLetterOrDigit)) start--
        floor = line + 1
        start to name
    }
    val bounds = if (lines.subList(0, starts[0].first).any(String::isNotBlank)) listOf(0 to title) + starts
    else listOf(0 to starts[0].second) + starts.drop(1)
    // Each section but the last keeps its final line break: a <pre> drops a trailing one, which would lose a blank line.
    return bounds.mapIndexed { i, (from, name) ->
        val to = bounds.getOrNull(i + 1)?.first ?: lines.size
        val end = if (to < lines.size) "\n" else ""
        GuideSection(name, "<pre>${escapeHtml(lines.subList(from, to).joinToString("\n") + end)}</pre>", heading = false)
    }
}

private val CONTENTS = Regex("\\bcontents\\b", RegexOption.IGNORE_CASE)
private val CODE = "\\[[A-Za-z0-9.-]{2,8}]"
/** A line starting with a tag ("[INTRO]", "(B)", "B)", "1.", "5.3") and then a name. */
private val TAGGED = Regex("\\s*($CODE|\\(?[A-Za-z0-9]{1,3}(?:\\.[0-9]+)*[.)]|[0-9]+(?:\\.[0-9]+)+)\\s+(\\S.*?)\\s*")
/** A line ending with a "[CODE]", as in "Introduction        [INTRO]". */
private val CODED = Regex("\\s*\\S.*?\\s+($CODE)\\s*")
private val RULE = Regex("\\s*([-=*#~_])\\1{19,}\\s*")

/**
 * The heading lines a contents list in the first third points at, with the names it gives them: for each entry
 * in turn, the next line that starts with its tag and its name, or that starts or ends with its "[CODE]". Null
 * when no contents list finds two.
 */
private fun contentsHeadings(lines: List<String>): List<Pair<Int, String>>? {
    // Each line matched once: tag and name, and an ending code.
    val tagged = lines.map { TAGGED.matchEntire(it)?.let { m -> m.groupValues[1] to m.groupValues[2] } }
    val coded = lines.map { CODED.matchEntire(it)?.groupValues?.get(1) }
    fun isHeading(k: Int, tag: String, name: String): Boolean {
        val code = tag.startsWith("[")
        val (t, n) = tagged[k] ?: return code && coded[k] == tag
        return t == tag && (code || matchKey(n) == matchKey(name)) || code && coded[k] == tag
    }
    for (c in 0 until maxOf(60, lines.size / 3).coerceAtMost(lines.size)) {
        if (lines[c].trim().length >= 40 || !CONTENTS.containsMatchIn(lines[c])) continue
        // The entries start within a few lines (past a box or a "Table of Contents" line) and end at a blank line.
        var i = c + 1
        while (i < minOf(lines.size, c + 6) && tagged[i] == null) i++
        val entries = mutableListOf<Pair<String, String>>()
        while (i < lines.size && lines[i].isNotBlank()) tagged[i++]?.let(entries::add)
        val found = mutableListOf<Pair<Int, String>>()
        for ((tag, name) in entries) {
            val k = (i until lines.size).firstOrNull { isHeading(it, tag, name) } ?: continue
            found += k to name
            i = k + 1
        }
        if (found.size >= 2) return found
    }
    return null
}

/** Lines boxed, overlined or underlined by a [RULE] that opens after a blank line, with their text as the name. */
private fun ruleHeadings(lines: List<String>): List<Pair<Int, String>> {
    val rules = lines.map { RULE.matchEntire(it) != null }
    fun rule(i: Int) = rules.getOrElse(i) { false }
    // A rule below a line of text closes it, so the line after that rule is body text.
    fun opens(i: Int) = i < 0 || lines[i].isBlank() || rule(i)
    return lines.indices
        .filter { i -> lines[i].isNotBlank() && !rule(i) && if (rule(i - 1)) opens(i - 2) else rule(i + 1) && opens(i - 1) }
        .map { it to lines[it].trim().removeSuffix(":").replace(Regex("\\s+"), " ") }
}

private val ONE_TEXT_SECTION = Regex(
    "<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>([^<]*)</title>.*?" +
        "<li><a href=\"#s1\">[^<]*</a></li>\n</ol></nav>\n<section id=\"s1\"><h2>[^<]*</h2>\n<pre>([^<]*)</pre>\n</section>\n" +
        "<footer>(.*)</footer>\n</body></html>\n?",
    RegexOption.DOT_MATCHES_ALL,
)

/** Whether [head], the first few kilobytes of a saved guide, starts one saved as a single text section. */
fun isOneTextSection(head: String) = "<section id=\"s1\">" in head && "href=\"#s2\"" !in head && "</h2>\n<pre>" in head

/**
 * A guide saved as a single text section, as text guides were before they were split, written again with
 * [textSections]; null for any other guide, or a text with no headings.
 */
fun splitTextGuide(html: String): String? {
    val m = ONE_TEXT_SECTION.matchEntire(html) ?: return null
    val title = unescapeHtml(m.groupValues[1])
    val sections = textSections(title, unescapeHtml(m.groupValues[2]))
    if (sections.size < 2) return null
    return StringWriter().also { writeHtmlGuide(it, title, sections, emptyMap(), m.groupValues[3]) }.toString()
}

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
        out.write("<section id=\"s${i + 1}\">" + (if (s.heading) "<h2>${escapeHtml(s.title)}</h2>" else "") + "\n")
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
