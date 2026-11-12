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
     * A file [write] wrote: [path] below the tree, in the place of a file that was there when [replaced]. Under Ask, a
     * file that would replace one waits beside it under its temporary name instead: [finish] swaps it in (true) or
     * deletes it (false), and returns what was written.
     */
    class Written(val path: String, val replaced: Boolean, val finish: ((Boolean) -> Written?)? = null)

    /**
     * Writes `<tree>/<segments>` under [policy] by calling [copy] with the open stream, or returns null when skipped
     * because it exists. The bytes go to a temporary file beside it first, which takes its name only once [copy]
     * has finished, so a write that fails leaves the file already there as it was.
     */
    fun write(segments: List<String>, mime: String, policy: Overwrite, copy: (OutputStream) -> Unit): Written? {
        var dir = DocumentsContract.getTreeDocumentId(tree)
        for (name in segments.dropLast(1)) dir = children(context, tree, dir)[name] ?: create(dir, Document.MIME_TYPE_DIR, name)
        val existing = children(context, tree, dir)
        val name = targetName(segments.last(), policy) { it in existing } ?: return null
        // Never `<ROM name>.<ext>`, so ES-DE can't take it for the game's media. A generic type keeps the provider
        // from adding an extension to it.
        val part = ".$name.part"
        // Left by a write that was cut off.
        existing[part]?.let(::delete)
        val tmp = create(dir, "application/octet-stream", part)
        try {
            resolver.openOutputStream(uri(tmp), "w")!!.use(copy)
        } catch (e: Throwable) {
            runCatching { delete(tmp) }
            throw e
        }
        val old = existing[name]
        fun swapIn(): Written {
            val written = swap(dir, tmp, name, old, mime)
            if (written != name) Log.w(TAG, "provider renamed $name to $written; ES-DE will not match it")
            return Written((segments.dropLast(1) + written).joinToString("/"), old != null)
        }
        if (policy == Overwrite.ASK && old != null) {
            return Written(segments.joinToString("/"), false) { replace -> if (replace) swapIn() else null.also { delete(tmp) } }
        }
        return swapIn()
    }

    /**
     * Puts the finished temporary file [tmp] in the place of [old], or under [name] when there's none, and returns
     * the name it got. Where the provider can't rename, the bytes are copied to a new file under [name] instead.
     */
    private fun swap(dir: String, tmp: String, name: String, old: String?, mime: String): String {
        old?.let(::delete)
        runCatching { DocumentsContract.renameDocument(resolver, uri(tmp), name) }
            .onFailure { Log.w(TAG, "renaming $tmp failed; writing $name in place", it) }
            .getOrNull()?.let { return DocumentsContract.getDocumentId(it).substringAfterLast('/') }
        val doc = create(dir, mime, name)
        try {
            resolver.openInputStream(uri(tmp))!!.use { input -> resolver.openOutputStream(uri(doc), "w")!!.use { input.copyTo(it) } }
        } finally {
            runCatching { delete(tmp) }
        }
        return doc.substringAfterLast('/')
    }

    private fun uri(doc: String) = DocumentsContract.buildDocumentUriUsingTree(tree, doc)

    private fun delete(doc: String) {
        DocumentsContract.deleteDocument(resolver, uri(doc))
    }

    private fun create(dir: String, mime: String, name: String): String =
        DocumentsContract.getDocumentId(DocumentsContract.createDocument(resolver, uri(dir), mime, name)!!)
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
