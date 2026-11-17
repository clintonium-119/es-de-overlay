package com.clintonium.es_de_overlay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.edit
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.Overwrite

class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    // Written only by the viewer's process: a process writes back its whole cached file, so sharing "prefs"
    // with the settings screen would let one erase the other's changes.
    private val positions = context.getSharedPreferences("positions", Context.MODE_PRIVATE)
    // Written only by the service's process, which the viewer tells what it opened.
    private val lastDocs = context.getSharedPreferences("lastDocs", Context.MODE_PRIVATE)

    var esdeTree: Uri?
        get() = sp.getString("esdeTree", null)?.let(Uri::parse)
        set(v) = sp.edit { putString("esdeTree", v?.toString()) }
    var romsTree: Uri?
        get() = sp.getString("romsTree", null)?.let(Uri::parse)
        set(v) = sp.edit { putString("romsTree", v?.toString()) }
    var mediaType: String
        get() = sp.getString("mediaType", "screenshots")!!
        set(v) = sp.edit { putString("mediaType", v) }
    /** The ES-DE image type the menu shows; empty for the one captures are saved as ([mediaType]). */
    var menuImageType: String
        get() = sp.getString("menuImageType", "")!!
        set(v) = sp.edit { putString("menuImageType", v) }
    val menuImage get() = menuImageType.ifEmpty { mediaType }
    /** Whether the menu crops [menuImage] to its box: a game's screen, as themes draw it; covers and the rest fit. */
    val menuImageCrops get() = menuImage == "screenshots" || menuImage == "titlescreens"
    /** The gamepad key code that opens the menu; 0 for Back or Menu. */
    var menuKey: Int
        get() = sp.getInt("menuKey", 0)
        set(v) = sp.edit { putInt("menuKey", v) }
    var overwrite: Overwrite
        // A policy an older build stored but this one dropped (Keep both) reads as Skip: it never overwrote either.
        get() = when (val name = sp.getString("overwrite", null)) {
            null -> Overwrite.REPLACE
            else -> Overwrite.entries.firstOrNull { it.name == name } ?: Overwrite.SKIP
        }
        set(v) = sp.edit { putString("overwrite", v.name) }
    var clipSeconds: Int
        get() = sp.getInt("clipSeconds", 15)
        set(v) = sp.edit { putInt("clipSeconds", v) }
    /** The theme's text sizes in the overlay, as a percentage. */
    var textScale: Int
        get() = sp.getInt("textScale", 100)
        set(v) = sp.edit { putInt("textScale", v) }
    /** Whether the service starts after the device boots. On by default, so a restart doesn't leave the menu gone. */
    var bootStart: Boolean
        get() = sp.getBoolean("bootStart", true)
        set(v) = sp.edit { putBoolean("bootStart", v) }
    var tileAdded: Boolean
        get() = sp.getBoolean("tileAdded", false)
        set(v) = sp.edit { putBoolean("tileAdded", v) }
    // On by default where ES-DE is the Home app, so a handheld left in ES-DE doesn't stay lit until the battery runs out.
    var sleepWhenIdle: Boolean
        get() = sp.getBoolean("sleepWhenIdle", esdeIsHome())
        set(v) = sp.edit { putBoolean("sleepWhenIdle", v) }
    /** Minutes ES-DE's screensaver runs before the screen goes off. */
    var sleepMinutes: Int
        get() = sp.getInt("sleepMinutes", 9)
        set(v) = sp.edit { putInt("sleepMinutes", v) }
    /** Whether a system screenshot taken during a game is filed as that game's image. */
    var systemScreenshots: Boolean
        get() = sp.getBoolean("systemScreenshots", false)
        set(v) = sp.edit { putBoolean("systemScreenshots", v) }
    /** Whether Quit Game asks before it ends the game; off by default, as Quit Game always ended it at once. */
    var confirmQuit: Boolean
        get() = sp.getBoolean("confirmQuit", false)
        set(v) = sp.edit { putBoolean("confirmQuit", v) }
    /** Whether a game left with Home is ended through Shizuku, as Quit Game would. */
    var quitOnHome: Boolean
        get() = sp.getBoolean("quitOnHome", false)
        set(v) = sp.edit { putBoolean("quitOnHome", v) }
    /** Whether the menu opens straight into the guide, map or manual last opened for the game. */
    var reopenLastDoc: Boolean
        get() = sp.getBoolean("reopenLastDoc", true)
        set(v) = sp.edit { putBoolean("reopenLastDoc", v) }

    /** The key (path below the ES-DE folder) of the guide, map or manual last opened for [game], or null. */
    fun lastDoc(game: GameRef) = lastDocs.getString("${game.system}/${game.romRelativePath}", null)

    /** Remembers [key] as [game]'s last opened document, or forgets it when null. */
    fun setLastDoc(game: GameRef, key: String?) = lastDocs.edit {
        val name = "${game.system}/${game.romRelativePath}"
        if (key == null) remove(name) else putString(name, key)
    }

    /** Whether the menu, guides and browser open on a second screen, when there is one, leaving the game running. */
    var secondScreen: Boolean
        get() = sp.getBoolean("secondScreen", false)
        set(v) = sp.edit { putBoolean("secondScreen", v) }
    /** Whether the menu opens on the second screen by itself when a game starts; off, as that screen may be in use. */
    var openAtLaunch: Boolean
        get() = sp.getBoolean("openAtLaunch", false)
        set(v) = sp.edit { putBoolean("openAtLaunch", v) }

    fun esdeIsHome() =context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName == ESDE

    /** Where the viewer left a document (a page, or a scroll fraction), keyed by its path below the ES-DE folder. */
    fun position(key: String) = positions.getInt(key, 0)

    /** The viewer's zoom (and centre) for a document left zoomed in, or null for its fit. */
    fun view(key: String) = positions.getString("/$key", null)?.split(' ')?.mapNotNull(String::toFloatOrNull)

    /**
     * Written at once: the viewer's process ends right after it closes, before an apply() would land. The
     * [view] goes under "/" and the key: keys are relative paths, so that can't clash with another's position.
     */
    fun setPosition(key: String, value: Int, view: List<Float>?) = positions.edit(commit = true) {
        putInt(key, value)
        if (view == null) remove("/$key") else putString("/$key", view.joinToString(" "))
    }
}
