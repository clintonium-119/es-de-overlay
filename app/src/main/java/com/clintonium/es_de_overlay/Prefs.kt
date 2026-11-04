package com.clintonium.es_de_overlay

import android.content.Context
import android.net.Uri
import com.clintonium.es_de_overlay.core.Overwrite

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    // Written only by the viewer's process: a process writes back its whole cached file, so sharing "prefs"
    // with the settings screen would let one erase the other's changes.
    private val positions = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    var esdeTree: Uri?
        get() = sp.getString("esdeTree", null)?.let(Uri::parse)
        set(v) = sp.edit().putString("esdeTree", v?.toString()).apply()
    var romsTree: Uri?
        get() = sp.getString("romsTree", null)?.let(Uri::parse)
        set(v) = sp.edit().putString("romsTree", v?.toString()).apply()
    var mediaType: String
        get() = sp.getString("mediaType", "screenshots")!!
        set(v) = sp.edit().putString("mediaType", v).apply()
    var overwrite: Overwrite
        // A policy an older build stored but this one dropped (Keep both) reads as Skip: it never overwrote either.
        get() = when (val name = sp.getString("overwrite", null)) {
            null -> Overwrite.REPLACE
            else -> Overwrite.entries.firstOrNull { it.name == name } ?: Overwrite.SKIP
        }
        set(v) = sp.edit().putString("overwrite", v.name).apply()
    var clipSeconds: Int
        get() = sp.getInt("clipSeconds", 15)
        set(v) = sp.edit().putInt("clipSeconds", v).apply()
    var bootStart: Boolean
        get() = sp.getBoolean("bootStart", false)
        set(v) = sp.edit().putBoolean("bootStart", v).apply()
    var tileAdded: Boolean
        get() = sp.getBoolean("tileAdded", false)
        set(v) = sp.edit().putBoolean("tileAdded", v).apply()

    /** Where the viewer left a document (a page, or a scroll fraction), keyed by its path below the ES-DE folder. */
    fun position(key: String) = positions.getInt(key, 0)

    /** Written at once: the viewer's process ends right after it closes, before an apply() would land. */
    fun setPosition(key: String, value: Int) = positions.edit().putInt(key, value).commit()
}
