package com.clintonium.es_de_overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import com.clintonium.es_de_overlay.core.LINEAR
import com.clintonium.es_de_overlay.core.Look
import com.clintonium.es_de_overlay.core.ThemeSettings
import com.clintonium.es_de_overlay.core.averageColor
import com.clintonium.es_de_overlay.core.look
import com.clintonium.es_de_overlay.core.overlayTheme
import com.clintonium.es_de_overlay.core.resolveTheme
import com.clintonium.es_de_overlay.core.themeSettings
import com.clintonium.es_de_overlay.core.unpackFiles
import com.clintonium.es_de_overlay.core.with

/**
 * The overlay's [Look] from ES-DE's theme settings, the active theme's files and an optional overlay-theme.xml in the
 * ES-DE folder, with the list and body fonts loaded. Read when ES-DE launches a game ([warm]), which the in-game menu
 * then keeps, and when the settings screen or the viewer opens; kept while es_settings.xml, overlay-theme.xml, the
 * system and the media type stay the same.
 * A theme's files come from the first place that has it: `ES-DE/themes/`, ES-DE's own app (its built-in themes, as
 * assets), or a copy of a theme from ES-DE's theme downloader, which lives in ES-DE's app storage where only Shizuku
 * can read it and which [warm] makes in the cache.
 * One instance per service; [context] is a window context, for the screen's aspect ratio.
 */
class ThemeLook(private val context: Context, private val prefs: Prefs) {
    /**
     * A look with its fonts: the theme's, or sans-serif when it has none or it can't be read. [open] opens one of the
     * look's files by its path below the ES-DE folder, from wherever the theme was read; null when it's missing.
     * With ES-DE's "Swap the A/B and X/Y buttons" on ([swapButtons]), a gamepad's A works as B and X as Y, and the
     * other way round, in ES-DE's input and its help alike.
     */
    class Current(
        val look: Look,
        val font: Typeface,
        val bodyFont: Typeface,
        val swapButtons: Boolean = false,
        val open: (String) -> InputStream?,
    ) {
        /** Key [code] as ES-DE reads it. */
        fun button(code: Int) = if (!swapButtons) code else when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> code
        }

        /** The help's name for the button that does ES-DE's [letter] (A, B, X or Y). */
        fun label(letter: String) = if (!swapButtons) letter else when (letter) {
            "A" -> "B"
            "B" -> "A"
            "X" -> "Y"
            "Y" -> "X"
            else -> letter
        }
    }

    private var cached: Pair<List<Any?>, Current>? = null

    /** The look last read, by [warm] when ES-DE launched a game; null before any. Reads nothing. */
    val warmed: Current?
        @Synchronized get() = cached?.second

    /** The look for a game of [system] whose art box shows [mediaType] images. Reads files: not on the main thread. */
    @Synchronized
    fun current(system: String, mediaType: String): Current {
        val started = SystemClock.uptimeMillis()
        val tree = prefs.esdeTree
        val settings = tree?.let { read(it, "settings/es_settings.xml") }
        val overrides = tree?.let { read(it, "overlay-theme.xml") }
        val scale = prefs.textScale
        val key = listOf(tree, settings, system, mediaType, overrides, scale)
        cached?.let { (cachedKey, current) ->
            if (cachedKey == key) {
                Log.i(TAG, "look: cached, ${SystemClock.uptimeMillis() - started} ms")
                return current
            }
        }
        val parsed = settings?.let { themeSettings(it.byteInputStream()) } ?: ThemeSettings()
        val (base, files) = if (tree == null) look(null, "", "", "no ES-DE folder") to NO_FILES else themed(tree, parsed, system, mediaType)
        // A theme's file from where the theme was read, else from the ES-DE folder itself (an overlay-theme.xml font).
        val open = { path: String ->
            files(path) ?: tree?.let { runCatching { context.contentResolver.openInputStream(treeChild(it, path)) }.getOrNull() }
        }
        val look = (overrides?.let { base.with(overlayTheme(it.byteInputStream())) } ?: base)
            .let { it.copy(fontSize = it.fontSize * scale / 100f, bodyFontSize = it.bodyFontSize * scale / 100f) }
        val font = typeface(open, look.fontPath)
        val current = Current(look, font, if (look.bodyFontPath == look.fontPath) font else typeface(open, look.bodyFontPath), parsed.swapButtons, open)
        cached = key to current
        val loaded = if (look.fontPath != null && font === Typeface.SANS_SERIF) " (not loaded)" else ""
        Log.i(TAG, "look: ${look.source}, font ${look.fontPath}$loaded, ${SystemClock.uptimeMillis() - started} ms")
        return current
    }

    // The active theme's look and its files by path below the ES-DE folder; the default look when it can't be read.
    private fun themed(tree: Uri, settings: ThemeSettings, system: String, mediaType: String): Pair<Look, (String) -> InputStream?> {
        val own = if (isFolderName(settings.theme)) themeFiles(tree, settings.theme) else null
        // ES-DE loads its built-in Linear when the configured theme is gone. Without Shizuku a downloaded theme can't be
        // told from a gone one, so that gets the default look and a reason instead.
        val (theme, files) = when {
            own != null -> settings.theme to own
            ShizukuShell.ready -> BUILT_IN to themeFiles(tree, BUILT_IN)
            else -> settings.theme to null
        }
        if (files == null) {
            val reason = when {
                esde() == null -> "ES-DE not found"
                !ShizukuShell.ready -> "theme isn't in ES-DE/themes; allow Shizuku, or copy its folder there"
                else -> "theme folder not found"
            }
            return look(null, theme, "", reason) to NO_FILES
        }
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val style = resolveTheme(
            { path -> runCatching { files(path)?.use { it.reader().readText() } }.getOrNull() },
            settings,
            system,
            bounds.width().toFloat() / bounds.height(),
            mediaType,
            { path -> rasterColor(files, theme, path) },
        )
        val prefix = "themes/$theme/"
        return look(style, theme, settings.colorScheme, "theme.xml couldn't be read") to
            { path -> if (path.startsWith(prefix)) files(path.removePrefix(prefix)) else null }
    }

    // The average colour of the theme's picture at [path]. The copy of a downloaded theme holds no pictures, so
    // that theme's comes through Shizuku.
    private fun rasterColor(files: (String) -> InputStream?, theme: String, path: String): Int? {
        val bytes = runCatching { files(path)?.use { it.readBytes() } }.getOrNull()
            ?: (if (ShizukuShell.ready) ShizukuShell.output("cat ${quote("$APP_THEMES/$theme/$path")}") else null)
            ?: return null
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
        // Decoded down to about 64 pixels across: enough for an average, and a wallpaper is large.
        val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(size.outWidth, size.outHeight) / 64) }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return averageColor(pixels)
    }

    /**
     * The theme's files by path inside its folder, from the first place that has its theme.xml: ES-DE/themes, ES-DE's
     * own app, then (with [copied]) the copy from ES-DE's app storage; null when none has it.
     */
    private fun themeFiles(tree: Uri, theme: String, copied: Boolean = true): ((String) -> InputStream?)? {
        val assets = esde()?.assets
        val copy = File(context.cacheDir, "themes/$theme")
        val sources = listOfNotNull<(String) -> InputStream?>(
            { path -> runCatching { context.contentResolver.openInputStream(treeChild(tree, "themes/$theme/$path")) }.getOrNull() },
            { path -> assets?.let { runCatching { it.open("$theme/$path") }.getOrNull() } },
            if (copied) { path -> File(copy, path).takeIf { it.isFile }?.inputStream() } else null,
        )
        return sources.firstOrNull { open -> open("theme.xml")?.use { true } == true }
    }

    // ES-DE's app, for its assets; null when it isn't installed or can't be seen.
    private fun esde() = runCatching { context.createPackageContext(ESDE, 0) }.getOrNull()

    private fun read(tree: Uri, path: String): String? =
        runCatching { context.contentResolver.openInputStream(treeChild(tree, path))?.use { it.reader().readText() } }.getOrNull()

    // A font of the look's, else sans-serif. Built from its bytes, so it can come from any of the theme's places.
    private fun typeface(open: (String) -> InputStream?, path: String?): Typeface {
        val bytes = path?.let { runCatching { open(it)?.use(InputStream::readBytes) }.getOrNull() } ?: return Typeface.SANS_SERIF
        return runCatching {
            val font = Font.Builder(ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }).build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).setSystemFallback("sans-serif").build()
        }.onFailure { Log.w(TAG, "font $path couldn't be loaded", it) }.getOrDefault(Typeface.SANS_SERIF)
    }

    /**
     * Off the main thread, when ES-DE launches a game of [system]: copies the active theme from ES-DE's app storage
     * through Shizuku if neither ES-DE/themes nor ES-DE's app has it and its files changed since the last copy, then
     * reads the look, so the menu's Back finds it cached.
     */
    @Synchronized
    fun warm(system: String, mediaType: String) {
        val tree = prefs.esdeTree ?: return
        val theme = read(tree, "settings/es_settings.xml")?.let { themeSettings(it.byteInputStream()).theme } ?: return
        if (!isFolderName(theme)) return
        val copies = File(context.cacheDir, "themes")
        when {
            themeFiles(tree, theme, copied = false) != null -> copies.deleteRecursively()
            ShizukuShell.ready -> copy(theme, copies)
        }
        current(system, mediaType)
    }

    // The theme's XML, font and SVG files (SVG pictures give panel colours and selector shapes), by a signature of
    // their paths, sizes and times, into copies/<theme>; other themes' copies go.
    private fun copy(theme: String, copies: File) {
        val started = SystemClock.uptimeMillis()
        val files = "cd ${quote("$APP_THEMES/$theme")} && find . \\( -name '*.xml' -o -name '*.ttf' -o -name '*.otf' -o -name '*.svg' \\)"
        val signature = ShizukuShell.output("$files -exec stat -c '%Y %s %n' {} + | md5sum")?.decodeToString()?.trim() ?: return
        val copy = File(copies, theme)
        if (File(copy, SIGNATURE).takeIf { it.isFile }?.readText() == signature) {
            Log.i(TAG, "look: $theme copy is current, ${SystemClock.uptimeMillis() - started} ms")
            return
        }
        // One tar process: a process per file took 8 s for a theme of 481 files, tar 0.2 s.
        val archive = ShizukuShell.output("$files | tar -c -f - -T -") ?: return
        val incoming = File(copies, ".incoming").apply { deleteRecursively() }
        var count = 0
        unpackFiles(archive.inputStream()) { path, bytes ->
            File(incoming, path.removePrefix("./")).apply { parentFile?.mkdirs() }.writeBytes(bytes)
            count++
        }
        File(incoming, SIGNATURE).writeText(signature)
        copies.listFiles()?.filter { it != incoming }?.forEach { it.deleteRecursively() }
        incoming.renameTo(copy)
        // The look read before was of the old files.
        cached = null
        Log.i(TAG, "look: copied $count files of $theme in ${SystemClock.uptimeMillis() - started} ms")
    }

    companion object {
        /** Before anything is read: the default look in sans-serif. */
        val DEFAULT = Current(LINEAR, Typeface.SANS_SERIF, Typeface.SANS_SERIF) { null }

        // ES-DE's package, where its theme downloader installs themes, and the theme it falls back to (built in).
        private const val ESDE = "org.es_de.frontend"
        private const val APP_THEMES = "/storage/emulated/0/Android/data/$ESDE/files/themes"
        private const val BUILT_IN = "linear-es-de"
        private const val SIGNATURE = ".signature"
        private val NO_FILES: (String) -> InputStream? = { null }

        // A theme is named by its folder, so neither a read, the copy nor the command leaves the themes folders.
        private fun isFolderName(theme: String) = theme.isNotEmpty() && '/' !in theme && theme != "." && theme != ".."

        // [s] as one shell word.
        private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    }
}
