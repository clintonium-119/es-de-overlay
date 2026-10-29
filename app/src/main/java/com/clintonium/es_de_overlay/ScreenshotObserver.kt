package com.clintonium.es_de_overlay

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore.Images.Media

/** Reports each new system screenshot (Pictures/ or DCIM/Screenshots) once, with its MIME type. */
class ScreenshotObserver(
    private val context: Context,
    handler: Handler,
    private val onScreenshot: (Uri, String) -> Unit,
) : ContentObserver(handler) {
    private val seen = mutableSetOf<Long>()

    fun register() = context.contentResolver.registerContentObserver(Media.EXTERNAL_CONTENT_URI, true, this)

    fun unregister() = context.contentResolver.unregisterContentObserver(this)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        val since = System.currentTimeMillis() / 1000 - 15
        context.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI,
            arrayOf(Media._ID, Media.MIME_TYPE),
            "${Media.RELATIVE_PATH} IN (?, ?) AND ${Media.DATE_ADDED} >= ?",
            arrayOf("Pictures/Screenshots/", "DCIM/Screenshots/", since.toString()),
            "${Media.DATE_ADDED} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (seen.add(id)) onScreenshot(ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id), c.getString(1))
            }
        }
    }
}
