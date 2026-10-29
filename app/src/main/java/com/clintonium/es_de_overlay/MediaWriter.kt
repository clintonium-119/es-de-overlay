package com.clintonium.es_de_overlay

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.clintonium.es_de_overlay.core.Overwrite
import com.clintonium.es_de_overlay.core.targetName
import java.io.OutputStream

/** Writes files below a SAF tree (the ES-DE data folder), creating missing folders. */
class MediaWriter(private val context: Context, private val tree: Uri) {
    private val resolver = context.contentResolver

    /**
     * Writes `<tree>/<segments>` under [policy] by calling [copy] with the open stream.
     * Returns the path actually written (relative to the tree), or null when skipped because it exists.
     */
    fun write(segments: List<String>, mime: String, policy: Overwrite, copy: (OutputStream) -> Unit): String? {
        var dir = DocumentsContract.getTreeDocumentId(tree)
        for (name in segments.dropLast(1)) dir = children(context, tree, dir)[name] ?: create(dir, Document.MIME_TYPE_DIR, name)
        val existing = children(context, tree, dir)
        val name = targetName(segments.last(), policy) { it in existing } ?: return null
        val doc = existing[name] ?: create(dir, mime, name)
        resolver.openOutputStream(DocumentsContract.buildDocumentUriUsingTree(tree, doc), "wt")!!.use(copy)
        val written = doc.substringAfterLast('/')
        if (written != name) Log.w(TAG, "provider renamed $name to $written; ES-DE will not match it")
        return (segments.dropLast(1) + written).joinToString("/")
    }

    private fun create(dir: String, mime: String, name: String): String =
        DocumentsContract.getDocumentId(
            DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, dir), mime, name)!!,
        )
}

/** Child display name → document id, for the folder with document id [dir] below a SAF [tree]. */
fun children(context: Context, tree: Uri, dir: String): Map<String, String> =
    context.contentResolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir),
        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
        null, null, null,
    )!!.use { c -> buildMap { while (c.moveToNext()) put(c.getString(1), c.getString(0)) } }

/** Whether [path] below the ROMs tree is a directory (ES-DE treats e.g. `dig.scummvm/` as one game). */
fun isDirectory(context: Context, romsTree: Uri, path: String): Boolean = runCatching {
    context.contentResolver.query(treeChild(romsTree, path), arrayOf(Document.COLUMN_MIME_TYPE), null, null, null)
        ?.use { it.moveToFirst() && it.getString(0) == Document.MIME_TYPE_DIR }
}.getOrNull() == true
