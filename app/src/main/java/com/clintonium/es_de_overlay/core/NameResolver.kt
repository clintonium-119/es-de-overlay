package com.clintonium.es_de_overlay.core

import java.io.InputStream

data class GameRef(val system: String, val romRelativePath: String)

/** What a capture does when the game's media file exists; listed in this order in settings. */
enum class Overwrite { REPLACE, ASK, SKIP }

/**
 * Resolves [launch] to a game: the ROM path ES-DE logged, else a unique gamelist.xml name match.
 * Returns null rather than guessing when neither is conclusive.
 */
fun resolveGame(launch: Launch, romRoots: List<String>, gamelist: () -> InputStream?): GameRef? {
    romPathHint(launch, romRoots)?.let { return GameRef(launch.system, it) }
    val matches = gamelist()?.use { gamelistPaths(it, launch.name) }.orEmpty()
    return matches.singleOrNull()?.let { GameRef(launch.system, it) }
}

/**
 * Path segments under downloaded_media: `<system>/<mediaType>/<ROM subfolders>/<base>.<ext>`.
 * ES-DE pairs media with the ROM by exact basename, so the basename is never altered; a directory
 * interpreted as a file (e.g. `dig.scummvm`) keeps its whole name.
 */
fun mediaPath(game: GameRef, mediaType: String, ext: String, romIsDirectory: Boolean): List<String> {
    val parts = game.romRelativePath.split('/').filter { it.isNotEmpty() }
    val file = parts.last()
    val base = if (romIsDirectory) file else file.substringBeforeLast('.')
    return listOf(game.system, mediaType) + parts.dropLast(1) + "$base.${ext.lowercase()}"
}

/** The file name to write under [policy]: [name], or null when it exists and the policy is SKIP. ASK names it as REPLACE does. */
fun targetName(name: String, policy: Overwrite, exists: (String) -> Boolean): String? =
    if (!exists(name) || policy != Overwrite.SKIP) name else null
