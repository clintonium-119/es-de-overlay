package com.clintonium.es_de_overlay.core

import java.io.InputStream

data class GameRef(val system: String, val romRelativePath: String)

enum class Overwrite { REPLACE, SKIP, SUFFIX }

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

/** The file name to write under [policy], or null to skip. SUFFIX keeps extras as `name-1.png`, `name-2.png`… */
fun targetName(name: String, policy: Overwrite, exists: (String) -> Boolean): String? = when {
    !exists(name) || policy == Overwrite.REPLACE -> name
    policy == Overwrite.SKIP -> null
    else -> generateSequence(1) { it + 1 }
        .map { "${name.substringBeforeLast('.')}-$it.${name.substringAfterLast('.')}" }
        .first { !exists(it) }
}
