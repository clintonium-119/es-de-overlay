package com.clintonium.es_de_overlay.core

import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** A game's gamelist.xml metadata; a field is null when its tag is missing or empty. [released] is an ISO date. */
data class GameInfo(
    val developer: String?,
    val publisher: String?,
    val released: String?,
    val genre: String?,
    val players: String?,
    val description: String?,
)

private fun games(xml: InputStream): List<Element> {
    // A system with an alternative emulator set has an <alternativeEmulator> element before <gameList>: two root
    // elements, which a strict parser rejects. One wrapping root parses both forms.
    val body = xml.reader().readText().removePrefix("\uFEFF").replaceFirst(Regex("""^\s*<\?xml[^?]*\?>"""), "")
    val games = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(InputSource(StringReader("<root>$body</root>"))).getElementsByTagName("game")
    return (0 until games.length).map { games.item(it) as Element }
}

private fun Element.text(tag: String) = getElementsByTagName(tag).item(0)?.textContent?.trim()

/** A gamelist.xml game: [path] without "./", [name], and the raw `YYYYMMDDTHHMMSS` [lastPlayed]; null when missing. */
data class GamelistGame(val path: String, val name: String?, val lastPlayed: String?)

/** Every gamelist.xml game that has a <path>, in gamelist order. */
fun gamelistGames(xml: InputStream): List<GamelistGame> = games(xml).mapNotNull { game ->
    fun field(tag: String) = game.text(tag)?.takeIf { it.isNotEmpty() }
    field("path")?.let { GamelistGame(it.removePrefix("./"), field("name"), field("lastplayed")) }
}

/** ROM-relative paths (without the leading "./") of the gamelist.xml games whose <name> is [name]. */
fun gamelistPaths(xml: InputStream, name: String): List<String> =
    games(xml).mapNotNull { if (it.text("name") == name.trim()) it.text("path")?.removePrefix("./") else null }

/** The metadata of the gamelist.xml game at [romRelativePath], or null when the gamelist has no such game. */
fun gamelistInfo(xml: InputStream, romRelativePath: String): GameInfo? =
    games(xml).firstOrNull { it.text("path")?.removePrefix("./") == romRelativePath }?.let { game ->
        fun field(tag: String) = game.text(tag)?.takeIf { it.isNotEmpty() }
        GameInfo(
            developer = field("developer"),
            publisher = field("publisher"),
            released = field("releasedate")?.let(::isoDate),
            genre = field("genre"),
            players = field("players"),
            description = field("desc"),
        )
    }

/** ES-DE's `YYYYMMDDTHHMMSS` release date as `YYYY-MM-DD`, or null when it doesn't start with eight digits. */
fun isoDate(releasedate: String): String? = releasedate.take(8)
    .takeIf { it.length == 8 && it.all(Char::isDigit) }
    ?.let { "${it.take(4)}-${it.substring(4, 6)}-${it.substring(6)}" }
