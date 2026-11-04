package com.clintonium.es_de_overlay.core

/** Guide formats the viewer reads, including the self-contained HTML that Find Guides saves. CBR (a RAR archive) is left out. */
val GUIDE_EXTENSIONS = setOf("pdf", "txt", "png", "jpg", "jpeg", "cbz", "html")

/** ES-DE's scraped manual below downloaded_media, paired with the ROM like its other media. */
fun manualPath(game: GameRef, romIsDirectory: Boolean): List<String> =
    mediaPath(game, "manuals", "pdf", romIsDirectory)

/**
 * `<system>/guides/<ROM subfolders>/<base>` below downloaded_media. The game's guides are `<base>.<ext>`
 * files in the parent folder, or the files in a `<base>/` folder; `<base>` follows [mediaPath]'s rule.
 */
fun guidePath(game: GameRef, romIsDirectory: Boolean): List<String> =
    mediaPath(game, "guides", "", romIsDirectory).let { it.dropLast(1) + it.last().removeSuffix(".") }

/**
 * The game's guides as paths relative to the guides folder (`Easyland.pdf`, `Easyland/map.png`), from the
 * names in that [folder] and in its [base] subfolder. Keeps [GUIDE_EXTENSIONS] in any case, sorted by name.
 */
fun guideEntries(base: String, folder: Collection<String>, baseFolder: Collection<String>): List<String> =
    (folder.filter { it.substringBeforeLast('.') == base } + baseFolder.map { "$base/$it" })
        .filter { it.substringAfterLast('.', "").lowercase() in GUIDE_EXTENSIONS }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

/** A saved guide: [path] relative to `<system>/guides/`, its size in bytes, and its opaque document [id]. */
data class GuideFile(val path: String, val size: Long, val id: String)

/** One game's guides: [dir] is the ROM subfolder (`""` at the top), [base] the `<base>` of [guidePath]. */
data class GuideGame(
    val system: String,
    val dir: String,
    val base: String,
    val name: String,
    val lastPlayed: String?,
    val files: List<GuideFile>,
) {
    val size: Long get() = files.sumOf { it.size }
}

/** [path] without its last segment's extension. */
private fun stem(path: String) = path.lastIndexOf('.').let { if (it > path.lastIndexOf('/')) path.take(it) else path }

/**
 * Groups one system's guide [files] (kept to [GUIDE_EXTENSIONS]) into games. A file belongs to the gamelist
 * game whose stem is the file's path without its extension (`Easyland.pdf`), else its parent folder
 * (`Shantae/map.png`). A game's stems are its ROM path without the extension and, for a directory ROM, the
 * whole path, as in [mediaPath]. When two games share a stem (`Game.zip`, `Game.7z`) the first in [games]
 * wins. A file that matches no game groups under its parent folder, or its own stem at the top.
 */
fun guideGames(system: String, files: List<GuideFile>, games: List<GamelistGame>): List<GuideGame> {
    val byStem = HashMap<String, GamelistGame>()
    for (game in games) for (s in listOf(stem(game.path), game.path)) byStem.putIfAbsent(s, game)
    return files.filter { it.path.substringAfterLast('.', "").lowercase() in GUIDE_EXTENSIONS }
        .groupBy { file ->
            val parent = file.path.substringBeforeLast('/', "")
            val own = stem(file.path)
            own.takeIf { it in byStem } ?: parent.takeIf { it in byStem } ?: parent.ifEmpty { own }
        }
        .map { (key, files) ->
            val game = byStem[key]
            val base = key.substringAfterLast('/')
            GuideGame(
                system, key.substringBeforeLast('/', ""), base, game?.name ?: base, game?.lastPlayed,
                files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path }),
            )
        }
}
