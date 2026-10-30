package com.clintonium.es_de_overlay.core

import java.text.Normalizer

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
    val headings = MAPS_HEADING.findAll(atlasHtml).toList()
    val i = headings.indexOfFirst { matchKey(cleanName(it.groupValues[1])) == key }
    if (i < 0) return emptyList()
    val end = headings.getOrNull(i + 1)?.range?.first ?: atlasHtml.length
    return MAP_HREF.findAll(atlasHtml.substring(headings[i].range.last, end)).map { it.groupValues[1] }.distinct().toList()
}
