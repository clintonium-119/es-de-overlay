package com.clintonium.es_de_overlay

import android.util.Log
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.GuideImage
import com.clintonium.es_de_overlay.core.GuideSection
import com.clintonium.es_de_overlay.core.Overwrite
import com.clintonium.es_de_overlay.core.VGMAPS_SYSTEMS
import com.clintonium.es_de_overlay.core.VgMap
import com.clintonium.es_de_overlay.core.cleanName
import com.clintonium.es_de_overlay.core.fileName
import com.clintonium.es_de_overlay.core.guidePages
import com.clintonium.es_de_overlay.core.guidePath
import com.clintonium.es_de_overlay.core.mapEntryName
import com.clintonium.es_de_overlay.core.parsedPage
import com.clintonium.es_de_overlay.core.strategyWikiParseUrl
import com.clintonium.es_de_overlay.core.vgmapsMaps
import com.clintonium.es_de_overlay.core.vgmapsUrl
import com.clintonium.es_de_overlay.core.wikiSection
import com.clintonium.es_de_overlay.core.writeHtmlGuide
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val USER_AGENT = "ES-DE-Overlay/${BuildConfig.VERSION_NAME} (personal guide reader)"
private const val MAX_BYTES = 50 shl 20

/** One GET with the app's User-Agent. Throws on anything but a 200, and on a body over 50 MB. */
fun get(url: String): ByteArray {
    Log.i(TAG, "guide request: $url ($USER_AGENT)")
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 20_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} from ${c.url.host}")
        return c.inputStream.use { it.readNBytes(MAX_BYTES + 1) }.also { if (it.size > MAX_BYTES) throw IOException("over 50 MB: $url") }
    } finally {
        c.disconnect()
    }
}

/** StrategyWiki's guide for [name]: its title after redirects and its pages, or null when it has none. */
fun strategyWiki(name: String): Pair<String, List<String>>? {
    val (title, _) = parse(cleanName(name)) ?: return null
    val toc = parse("$title/Table of Contents")?.second
    return title to if (toc == null) listOf(title) else guidePages(title, toc)
}

private fun parse(page: String) = get(strategyWikiParseUrl(page)).inputStream().use(::parsedPage)

/**
 * Saves a StrategyWiki guide as `StrategyWiki - <title>.html` in [game]'s guides folder: every page, then every
 * image once, built in [cache] and copied only when all of it arrived. Returns the path written below the tree.
 */
fun saveStrategyWiki(
    writer: MediaWriter, cache: File, game: GameRef, romIsDirectory: Boolean, title: String, pages: List<String>,
    progress: (String) -> Unit, cancelled: () -> Boolean,
): String {
    val sections = mutableListOf<GuideSection>()
    val urls = mutableListOf<String>()
    pages.forEachIndexed { i, page ->
        if (cancelled()) throw CancellationException()
        progress("Saving page ${i + 1} of ${pages.size}")
        val html = parse(page)?.second ?: throw IOException("StrategyWiki has no page $page")
        val (section, images) = wikiSection(if (i == 0) title else page.substringAfter("$title/"), html, pages)
        sections += section
        urls += images
    }
    // ponytail: holds every image in memory until the file is written; spool to files if guides outgrow the heap.
    val images = urls.distinct().let { distinct ->
        distinct.mapIndexed { i, url ->
            if (cancelled()) throw CancellationException()
            progress("Saving image ${i + 1} of ${distinct.size}")
            url to GuideImage(URLConnection.guessContentTypeFromName(url) ?: "image/png", get(url))
        }.toMap()
    }
    val source = "https://strategywiki.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), Charsets.UTF_8)
    val credit = "From <a href=\"$source\">StrategyWiki</a> ($source), by StrategyWiki contributors, under " +
        "<a href=\"https://creativecommons.org/licenses/by-sa/4.0/\">CC BY-SA 4.0</a>."
    return saveVia(writer, cache, game, romIsDirectory, "StrategyWiki - ${fileName(title)}.html", "text/html", cancelled) { file ->
        file.bufferedWriter().use { writeHtmlGuide(it, title, sections, images, credit) }
    }
}

/** The maps VGMaps has for [name] on [system]'s atlas page; empty when it has none or no atlas. */
fun vgmaps(system: String, name: String): List<VgMap> {
    val folder = VGMAPS_SYSTEMS[system] ?: return emptyList()
    return vgmapsMaps(String(get(vgmapsUrl(folder)), Charset.forName("windows-1252")), name)
}

/**
 * Saves [maps] as `VGMaps - <name>.cbz` in [game]'s guides folder, one stored entry per map in atlas order,
 * named `NNN <map name>.<ext>`. Returns the path written below the tree.
 */
fun saveVgmaps(
    writer: MediaWriter, cache: File, game: GameRef, romIsDirectory: Boolean, name: String, maps: List<VgMap>,
    progress: (String) -> Unit, cancelled: () -> Boolean,
): String {
    val folder = VGMAPS_SYSTEMS.getValue(game.system)
    return saveVia(writer, cache, game, romIsDirectory, "VGMaps - ${fileName(cleanName(name))}.cbz", "application/octet-stream", cancelled) { file ->
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            maps.forEachIndexed { i, map ->
                if (cancelled()) throw CancellationException()
                progress("Saving map ${i + 1} of ${maps.size}")
                val path = map.href.replace("&amp;", "&")
                val bytes = get(if (path.startsWith("http")) path else URI("https", "www.vgmaps.com", "/Atlas/$folder/$path", null).toASCIIString())
                val entry = mapEntryName(i, map.name, path.substringAfterLast('.'))
                zip.putNextEntry(ZipEntry(entry).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}

/** Builds the guide in a cache file with [build], then copies it into the guides folder; the cache file never stays. */
fun saveVia(
    writer: MediaWriter, cache: File, game: GameRef, romIsDirectory: Boolean, name: String, mime: String,
    cancelled: () -> Boolean, build: (File) -> Unit,
): String {
    val file = File.createTempFile("guide", null, cache)
    try {
        build(file)
        if (cancelled()) throw CancellationException()
        return writer.write(listOf("downloaded_media") + guidePath(game, romIsDirectory) + name, mime, Overwrite.REPLACE) { out ->
            file.inputStream().use { it.copyTo(out) }
        }!!
    } finally {
        file.delete()
    }
}

