package com.clintonium.es_de_overlay.core

import java.net.URLDecoder

/** One ES-DE launch: the "Launching game" line plus the lines logged in the same second. */
data class Launch(
    val name: String,
    val system: String,
    val stamp: String,
    val pkg: String? = null,
    val lines: List<String> = emptyList(),
)

private val LAUNCH = Regex("""^(\S+ +\d+ [\d:]+) +Info: +Launching game "(.*)" from system ".*\((\S+)\)"\.\.\.$""")
private val PACKAGE = Regex("""Info: +Package: (\S+)""")
private val ESCAPES = Regex("(%[0-9A-Fa-f]{2})+")

/** Folds one es_log.txt line into the latest launch seen so far. */
fun nextLaunch(latest: Launch?, line: String): Launch? {
    LAUNCH.find(line)?.let { m ->
        return Launch(name = m.groupValues[2], system = m.groupValues[3], stamp = m.groupValues[1], lines = listOf(line))
    }
    if (latest == null || !line.startsWith(latest.stamp)) return latest
    return latest.copy(
        pkg = latest.pkg ?: PACKAGE.find(line)?.groupValues?.get(1),
        lines = latest.lines + line,
    )
}

/**
 * The ROM path relative to `<ROMs>/<system>/`, if any launch line names it. ES-DE only logs it for
 * some launch arguments: raw for %ROM%, percent-encoded inside content:// URIs for %ROMSAF% and
 * %ROMPROVIDER%. [romRoots] holds the ROMs root as an absolute path and as a SAF document id,
 * e.g. "/storage/7BEF-E760/ROMs" and "7BEF-E760:ROMs".
 */
fun romPathHint(launch: Launch, romRoots: List<String>): String? {
    for (line in launch.lines) {
        val uri = line.indexOf("content://")
        val text = if (uri < 0) line else ESCAPES.replace(line.substring(uri)) { URLDecoder.decode(it.value, "UTF-8") }
        for (root in romRoots) {
            val key = "$root/${launch.system}/"
            val i = text.lastIndexOf(key)
            if (i >= 0) return text.substring(i + key.length).trim().trimEnd('"', '\'')
        }
    }
    return null
}
