package com.clintonium.esderecorder

import android.content.Context
import android.net.Uri
import com.clintonium.esderecorder.core.Overwrite

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

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
        get() = Overwrite.valueOf(sp.getString("overwrite", Overwrite.REPLACE.name)!!)
        set(v) = sp.edit().putString("overwrite", v.name).apply()
    var clipSeconds: Int
        get() = sp.getInt("clipSeconds", 15)
        set(v) = sp.edit().putInt("clipSeconds", v).apply()
    var bootStart: Boolean
        get() = sp.getBoolean("bootStart", false)
        set(v) = sp.edit().putBoolean("bootStart", v).apply()
    var overlayX: Int
        get() = sp.getInt("overlayX", 0)
        set(v) = sp.edit().putInt("overlayX", v).apply()
    var overlayY: Int
        get() = sp.getInt("overlayY", 200)
        set(v) = sp.edit().putInt("overlayY", v).apply()
}
