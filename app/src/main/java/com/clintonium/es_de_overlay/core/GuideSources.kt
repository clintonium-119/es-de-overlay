package com.clintonium.es_de_overlay.core

import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * A game name as guide sites write it: no `(USA)`/`[!]` tags, "A: B" rather than ES-DE's "A : B", and a
 * trailing ", The" moved to the front.
 */
fun cleanName(name: String): String = name
    .replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
    .replace(Regex("\\s+:\\s+"), ": ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .let { if (it.endsWith(", The", ignoreCase = true)) "The " + it.dropLast(5).trimEnd() else it }

/**
 * Letters and digits of [name], lowercased, with HTML entities decoded and accents dropped. Two names are the
 * same game only when their keys are equal and not empty; nothing looser, so a miss is possible but a wrong
 * game isn't.
 */
fun matchKey(name: String): String {
    val decoded = name
        .replace(Regex("&#(\\d+);")) { runCatching { String(Character.toChars(it.groupValues[1].toInt())) }.getOrDefault("") }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { runCatching { String(Character.toChars(it.groupValues[1].toInt(16))) }.getOrDefault("") }
        .replace(Regex("&([a-zA-Z])(acute|grave|circ|uml|tilde|cedil|ring|slash);"), "$1")
        // &amp;, &nbsp; and the like carry no letters.
        .replace(Regex("&\\w+;"), "")
    return Normalizer.normalize(decoded, Normalizer.Form.NFD).lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
}

/** ES-DE system → VGMaps atlas folder, from the atlas index on 2026-10-30. */
val VGMAPS_SYSTEMS = mapOf(
    "atari2600" to "Atari2600", "atari5200" to "Atari5200", "atari7800" to "Atari7800",
    "nes" to "NES", "famicom" to "NES", "snes" to "SuperNES", "sfc" to "SuperNES", "n64" to "N64",
    "gc" to "GCN", "wii" to "Wii", "virtualboy" to "VirtualBoy",
    "gb" to "GB-GBC", "gbc" to "GB-GBC", "gba" to "GBA", "nds" to "DS", "n3ds" to "3DS",
    "mastersystem" to "MasterSystem", "gamegear" to "GameGear", "genesis" to "Genesis", "megadrive" to "Genesis",
    "saturn" to "Saturn", "dreamcast" to "DC",
    "psx" to "PSX", "ps2" to "PS2", "psp" to "PSP",
    "pcengine" to "TG16", "tg16" to "TG16", "neogeo" to "Neo-Geo", "ngp" to "NGP-NGPC", "ngpc" to "NGP-NGPC",
    "wonderswan" to "WS-WSC", "wonderswancolor" to "WS-WSC",
    "colecovision" to "ColecoVision", "msx" to "MSX", "amiga" to "Amiga", "atarist" to "AtariST", "dos" to "PC",
    "arcade" to "Arcade", "fbneo" to "Arcade", "mame" to "Arcade",
)

fun vgmapsUrl(folder: String) = "https://www.vgmaps.com/Atlas/$folder/index.htm"

private val MAPS_HEADING = Regex(">([^<>]*?) Maps\\s*<", RegexOption.IGNORE_CASE)
private val MAP_HREF = Regex("href=\"([^\"]+\\.(?:png|gif|jpe?g))\"", RegexOption.IGNORE_CASE)

/**
 * The map images (`href`s as written, relative to the atlas page) of [gameName]'s section on a VGMaps atlas
 * page, in page order. A section runs from its "<Game> Maps" heading to the next one. Empty when no
 * heading names the game.
 */
fun vgmapsMaps(atlasHtml: String, gameName: String): List<String> {
    val key = matchKey(cleanName(gameName))
    if (key.isEmpty()) return emptyList()
    val headings = MAPS_HEADING.scan(atlasHtml)
    val i = headings.indexOfFirst { matchKey(cleanName(it.group(1))) == key }
    if (i < 0) return emptyList()
    val end = headings.getOrNull(i + 1)?.start() ?: atlasHtml.length
    return MAP_HREF.scan(atlasHtml.substring(headings[i].end(), end)).map { it.group(1) }.distinct()
}

/** StrategyWiki's API for one page's rendered HTML. Its `index.php` pages sit behind a bot check; the API doesn't. */
fun strategyWikiParseUrl(page: String) = "https://strategywiki.org/w/api.php?action=parse&page=" +
    URLEncoder.encode(page.replace(' ', '_'), Charsets.UTF_8) + "&prop=text&disableeditsection=1&redirects=1&format=xml"

/** The article HTML in an `action=parse` XML response; null for an `<error>` such as `missingtitle`. */
fun parsedText(xml: InputStream): String? = parsedPage(xml)?.second

/** The page title (after redirects) and article HTML in an `action=parse` XML response; null for an `<error>`. */
fun parsedPage(xml: InputStream): Pair<String, String>? {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
    if (doc.getElementsByTagName("error").length > 0) return null
    val parse = doc.getElementsByTagName("parse").item(0) as? Element ?: return null
    return parse.getAttribute("title") to (parse.getElementsByTagName("text").item(0)?.textContent ?: return null)
}

/**
 * A guide's pages: [title], then the `/wiki/<title>/…` pages its Table of Contents links to, in order. The
 * navigation boxes, which repeat a few of them first, are left out, as are the contents page itself,
 * missing (red) pages and pages outside the guide.
 */
fun guidePages(title: String, tocHtml: String): List<String> {
    val prefix = "$title/"
    val linked = WIKI_LINK.scan(stripWikiChrome(tocHtml)).map { wikiPage(it.group(1)) }
        .filter { it.startsWith(prefix) && it != "${prefix}Table of Contents" }
    return (listOf(title) + linked).distinct()
}

/**
 * One wiki page as a guide section: site styles, scripts, navigation boxes, ads and edit links removed;
 * links to [pages] pointed at their `#sK` section and other links reduced to their content; image `src`s
 * made absolute and returned as the section's image keys.
 */
fun wikiSection(heading: String, html: String, pages: List<String>): Pair<GuideSection, List<String>> {
    val images = mutableListOf<String>()
    val linked = removeElements(stripWikiChrome(html), "span", Regex("<span\\b[^>]*class=\"mw-editsection\""))
        .replace(EVENT_ATTR, "")
        .let { text ->
            ANCHOR.replaceEach(text) { a ->
                val target = WIKI_LINK.find(a.group(1))?.let { pages.indexOf(wikiPage(it.groupValues[1])) } ?: -1
                if (target < 0) a.group(2) else "<a href=\"#s${target + 1}\">${a.group(2)}</a>"
            }
        }
    val body = IMG_TAG.replaceEach(linked) { match ->
        val img = match.group()
        val src = IMG_SRC_ATTR.find(img)?.groupValues?.get(1) ?: return@replaceEach img.replace(SRCSET, "")
        val url = when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("/") -> "https://strategywiki.org$src"
            else -> src
        }
        images += url
        img.replace(SRCSET, "").replace(IMG_SRC_ATTR) { "src=\"$url\"" }
    }
    return GuideSection(heading, body) to images.distinct()
}

private val WIKI_LINK = Regex("href=\"/wiki/([^\"#?]+)[^\"]*\"")
private val ANCHOR = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMG_SRC_ATTR = Regex("\\bsrc=\"([^\"]*)\"")
private val SRCSET = Regex("\\s+srcset=\"[^\"]*\"")
private val EVENT_ATTR = Regex("\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*')", RegexOption.IGNORE_CASE)

/** `Super_Metroid/Space_Colony_Ceres` (URL-encoded) → `Super Metroid/Space Colony Ceres`. */
private fun wikiPage(path: String) = URLDecoder.decode(path, Charsets.UTF_8).replace('_', ' ')

/** Comments, `<style>`, `<script>`, `<link>`, and the header/footer navigation boxes, ad slot and page contents box. */
private fun stripWikiChrome(html: String) = removeElements(
    html.replace(Regex("<!--.*?-->|<(style|script)\\b.*?</\\1>|<link\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), ""),
    "div",
    Regex("<div\\b[^>]*(?:class=\"[^\"]*\\b(?:nav_box|section-ad)\\b|id=\"toc\")"),
)

/** [html] without each [tag] element whose opening tag matches [open], counting nested [tag]s to find its end. */
private fun removeElements(html: String, tag: String, open: Regex): String {
    val tags = Regex("</?$tag\\b[^>]*>", RegexOption.IGNORE_CASE)
    var out = html
    while (true) {
        val start = open.find(out)?.range?.first ?: return out
        var depth = 0
        val end = tags.scan(out, start).firstOrNull {
            depth += if (it.group()[1] == '/') -1 else 1
            depth == 0
        }?.end() ?: out.length
        out = out.removeRange(start, end)
    }
}

/**
 * Every match in [text] from [from], found with one `Matcher`. Kotlin's `findAll` and `replace { }` start a new
 * `Matcher` per match, and on Android each one copies all of [text] into native memory: scanning a 4.7 MB
 * VGMaps atlas page that way grew the service to 3.7 GB before the low-memory killer stopped it.
 */
internal fun Regex.scan(text: String, from: Int = 0): List<java.util.regex.MatchResult> {
    val m = toPattern().matcher(text)
    return buildList { if (m.find(from)) do add(m.toMatchResult()) while (m.find()) }
}

/** [text] with each match replaced by [transform]'s result, through [scan]. */
internal fun Regex.replaceEach(text: String, transform: (java.util.regex.MatchResult) -> CharSequence): String {
    val out = StringBuilder(text.length)
    var at = 0
    for (m in scan(text)) {
        out.append(text, at, m.start()).append(transform(m))
        at = m.end()
    }
    return out.append(text, at, text.length).toString()
}
