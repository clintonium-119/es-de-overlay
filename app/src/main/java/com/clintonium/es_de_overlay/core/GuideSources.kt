package com.clintonium.es_de_overlay.core

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
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

/**
 * [title] as a file name the SD card accepts, which stays in its folder: "A: B" becomes "A - B", other reserved
 * characters and both slashes "_", control characters go, and it keeps to 120 characters; one of only dots or
 * spaces, such as "..", is "_".
 */
fun fileName(title: String): String {
    val name = title.filterNot(Char::isISOControl).replace(": ", " - ").replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    return if (name.all { it == '.' || it == ' ' }) "_" else name
}

/**
 * The zip entry for map [index] (from 0) of an atlas, `NNN <name>.<ext>`: the names stay as the atlas page writes
 * them, but without a slash, which would make a folder in the zip, or a control character.
 */
fun mapEntryName(index: Int, name: String, ext: String): String {
    fun part(text: String) = text.filterNot(Char::isISOControl).replace(Regex("[/\\\\]"), "_")
    return "%03d %s.%s".format(index + 1, part(name), part(ext))
}

/** The guide sites only a browser gets into, which Find Guides opens in the touch browser. */
enum class GuideSite(val label: String, val host: String) {
    GAMEFAQS("GameFAQs", "gamefaqs.gamespot.com"),
    NEOSEEKER("Neoseeker", "www.neoseeker.com"),
    ZELDADUNGEON("ZeldaDungeon", "www.zeldadungeon.net"),
}

/** ZeldaDungeon's walkthroughs, as its site menu listed them on 2026-10-30. The site has no index page. */
val ZELDADUNGEON_WALKTHROUGHS = listOf(
    "echoes-of-wisdom-walkthrough-guide", "tears-of-the-kingdom-walkthrough", "breath-of-the-wild-walkthrough",
    "tri-force-heroes-walkthrough", "a-link-between-worlds-walkthrough", "skyward-sword-walkthrough",
    "spirit-tracks-walkthrough", "phantom-hourglass-walkthrough", "twilight-princess-walkthrough",
    "the-minish-cap-walkthrough", "the-wind-waker-walkthrough", "oracle-of-ages-walkthrough",
    "oracle-of-seasons-walkthrough", "majoras-mask-walkthrough", "ocarina-of-time-walkthrough",
    "links-awakening-walkthrough", "a-link-to-the-past-walkthrough", "the-adventure-of-link-walkthrough",
    "the-legend-of-zelda-walkthrough", "four-swords-adventures-walkthrough", "link-the-faces-of-evil-walkthrough",
    "zelda-the-wand-of-gamelon-walkthrough",
)

/**
 * Where the browser opens for [name] on [site]: the site's search for it, or for ZeldaDungeon the game's
 * walkthrough, since its search only finds news. Null when ZeldaDungeon has no walkthrough for the game.
 */
fun startUrl(site: GuideSite, name: String): String? {
    val q = cleanName(name)
    fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)
    return when (site) {
        GuideSite.GAMEFAQS -> "https://${site.host}/search?game=${encode(q)}"
        // Neoseeker's search shows its blank form for a query with a colon in it.
        GuideSite.NEOSEEKER -> "https://${site.host}/seeker.php?q=${encode(q.replace(":", ""))}&searchby=product&area=all"
        GuideSite.ZELDADUNGEON -> zeldaDungeonWalkthrough(q)?.let { "https://${site.host}/$it/" }
    }
}

/**
 * The walkthrough whose title is in [name] ("Link's Awakening" in "The Legend of Zelda: Link's Awakening DX"),
 * the longest if several are; the first game's only for that exact name, which every other title contains.
 */
private fun zeldaDungeonWalkthrough(name: String): String? {
    val key = matchKey(name)
    return ZELDADUNGEON_WALKTHROUGHS
        .map { it to matchKey(it.removeSuffix("-guide").removeSuffix("-walkthrough")) }
        .filter { (_, title) -> if (title == "thelegendofzelda") key == title else title in key }
        .maxByOrNull { it.second.length }?.first
}

/**
 * A web search for [name]'s guides, for those on other sites. DuckDuckGo, because on 2026-11-05 it showed results
 * in the Nova's WebView, where Brave Search and Mojeek asked to prove it wasn't a bot and Bing opened on a cookie
 * banner.
 */
fun webSearchUrl(name: String) = "https://$WEB_SEARCH/?q=" + URLEncoder.encode("${cleanName(name)} guide", Charsets.UTF_8)

private const val WEB_SEARCH = "duckduckgo.com"

/** The guide site [url] is on, if any. */
fun siteOf(url: String): GuideSite? = runCatching { URI(url).host }.getOrNull()?.let { host ->
    GuideSite.entries.firstOrNull { it.host == host || it.host == "www.$host" }
}

/**
 * Whether [url] may hold a guide to save: any web page off the three guide sites but the web search's, where
 * Reader View's rules judge it, or on them a guide's page, written from guide URLs seen on each site on 2026-10-30:
 * - GameFAQs `/gameboy/563277-the-legend-of-zelda-links-awakening/faqs/18445`, plus `/main-walkthrough` for a page;
 * - Neoseeker `/linkdx/faqs/182906-zelda-links-awakening-h.html` and its wiki walkthroughs,
 *   `/metroid-samus-returns/walkthrough/Collectathon_-_Surface` and `/animal-well/Path_of_the_Seahorse_(B._Flame)`;
 * - ZeldaDungeon `/links-awakening-walkthrough/` and its chapters, `/links-awakening-walkthrough/tail-cave/`.
 */
fun isGuidePage(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val path = uri.path ?: return false
    val pattern = when (siteOf(url) ?: return uri.scheme in setOf("http", "https") && uri.host != WEB_SEARCH) {
        GuideSite.GAMEFAQS -> GAMEFAQS_GUIDE
        GuideSite.NEOSEEKER -> NEOSEEKER_GUIDE
        GuideSite.ZELDADUNGEON -> ZELDADUNGEON_GUIDE
    }
    return pattern.matches(path)
}

/** The most pages a guide page's own list is kept to: a page can list thousands. */
const val MAX_GUIDE_PAGES = 50

/** Whether [url], which a page at [here] reported, is an https page on [here]'s host. */
fun onSameSite(here: String, url: String): Boolean {
    val host = runCatching { URI(here).host }.getOrNull() ?: return false
    return runCatching { URI(url) }.getOrNull()?.let { it.scheme == "https" && it.host == host } == true
}

/** The pages a guide page at [here] listed, as URL and title: those [onSameSite], at most [MAX_GUIDE_PAGES]. */
fun guidePageList(here: String, pages: List<Pair<String, String>>) =
    pages.filter { onSameSite(here, it.first) }.take(MAX_GUIDE_PAGES).map { (url, title) -> url to guideText(title) }

/** A title or author a page reported: no control characters, at most 200 characters. */
fun guideText(text: String) = text.filterNot(Char::isISOControl).take(200)

private val GAMEFAQS_GUIDE = Regex("/[^/]+/\\d+-[^/]+/faqs/\\d+(/[^/]+)?/?")
private val NEOSEEKER_GUIDE = Regex("/[^/]+/(faqs/\\d+-[^/]+\\.html|walkthrough(/.*)?|[A-Z][^/]*)")
private val ZELDADUNGEON_GUIDE = Regex("/[a-z0-9-]+-walkthrough(-guide)?/.*")

/** Whether a page titled [title] is a bot check waiting on the browser or the user, as the sites showed them on 2026-10-30. */
fun isBotCheck(title: String): Boolean = BOT_CHECK_TITLES.any { title.trim().startsWith(it, ignoreCase = true) }

private val BOT_CHECK_TITLES = listOf("Just a moment", "Security check", "Attention Required!")

/** `<site> - <title>.html`, at most 120 characters; [site] is a guide site's label, or another site's host. */
fun guideFileName(site: String, title: String): String {
    val prefix = "${fileName(site)} - "
    return prefix + fileName(title).take(120 - prefix.length - ".html".length).trimEnd() + ".html"
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
private val MAP_LINK = Regex(
    "href=\"([^\"]+\\.(?:png|gif|jpe?g))\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val ROW = Regex("<tr\\b", RegexOption.IGNORE_CASE)
private val FIRST_CELL = Regex("<td\\b[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** A map on a VGMaps atlas page: its image `href` as written (relative to the page) and its name there. */
data class VgMap(val href: String, val name: String)

/**
 * The maps of [gameName]'s section on a VGMaps atlas page, in page order. A section runs from its "<Game> Maps"
 * heading to the next one. A map is named by its link text, after its row's group (the first column, "Start"
 * for Shantae's "Part 1") when the link text doesn't already say it. Empty when no heading names the game.
 */
fun vgmapsMaps(atlasHtml: String, gameName: String): List<VgMap> {
    val key = matchKey(cleanName(gameName))
    if (key.isEmpty()) return emptyList()
    val headings = MAPS_HEADING.scan(atlasHtml)
    val i = headings.indexOfFirst { matchKey(cleanName(it.group(1))) == key }
    if (i < 0) return emptyList()
    val section = atlasHtml.substring(headings[i].end(), headings.getOrNull(i + 1)?.start() ?: atlasHtml.length)
    val starts = listOf(0) + ROW.scan(section).map { it.start() } + section.length
    return starts.zipWithNext { a, b -> section.substring(a, b) }.flatMap { row ->
        val group = FIRST_CELL.find(row)?.let { htmlText(it.groupValues[1]) }.orEmpty()
        MAP_LINK.scan(row).map { link ->
            val href = link.group(1)
            val text = htmlText(link.group(2)).ifEmpty { href.substringAfterLast('/').substringBeforeLast('.') }
            VgMap(href, if (matchKey(group) in matchKey(text)) text else "$group - $text")
        }
    }.distinctBy { it.href }
}

/** [html]'s text: tags dropped, character references decoded, white space collapsed. */
private fun htmlText(html: String) = unescapeHtml(html.replace(Regex("<[^>]*>"), " ")).replace(Regex("\\s+"), " ").trim()

/** StrategyWiki's API for one page's rendered HTML. Its `index.php` pages sit behind a bot check; the API doesn't. */
fun strategyWikiParseUrl(page: String) = "https://strategywiki.org/w/api.php?action=parse&page=" +
    URLEncoder.encode(page.replace(' ', '_'), Charsets.UTF_8) + "&prop=text&disableeditsection=1&redirects=1&format=xml"

/** The article HTML in an `action=parse` XML response; null for an `<error>` such as `missingtitle`. */
fun parsedText(xml: InputStream): String? = parsedPage(xml)?.second

/** The page title (after redirects) and article HTML in an `action=parse` XML response; null for an `<error>`. */
fun parsedPage(xml: InputStream): Pair<String, String>? {
    val doc = xmlDocument(xml.reader().readText()) ?: throw IOException("StrategyWiki's answer isn't well-formed or has a DOCTYPE")
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
