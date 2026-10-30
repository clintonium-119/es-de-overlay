package com.clintonium.es_de_overlay.core

/** Guide formats the viewer reads, including the self-contained HTML that Find guides saves. CBR (a RAR archive) is left out. */
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
