package com.clintonium.es_de_overlay

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.format.Formatter
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.clintonium.es_de_overlay.core.GamelistGame
import com.clintonium.es_de_overlay.core.GuideFile
import com.clintonium.es_de_overlay.core.GuideGame
import com.clintonium.es_de_overlay.core.gamelistGames
import com.clintonium.es_de_overlay.core.guideGames
import com.clintonium.es_de_overlay.core.isoDate
import kotlin.concurrent.thread

/** Every saved guide, grouped by game, with search, sorts and delete. */
class GuidesActivity : ComponentActivity() {
    // Null while the scan runs.
    private var entries by mutableStateOf<List<Entry>?>(null)
    private var message by mutableStateOf<String?>(null)

    // The open confirm dialog: its question and what Delete does.
    private var confirm by mutableStateOf<Pair<String, () -> Unit>?>(null)

    /** A game in the list; [error] is why its last delete failed. */
    private data class Entry(val game: GuideGame, val romMissing: Boolean, val error: String? = null) {
        val key get() = "${game.system}/${game.dir}/${game.base}"
    }

    private enum class Sort(val label: String, val order: Comparator<GuideGame>) {
        SIZE("Size", compareByDescending { it.size }),
        LAST_PLAYED("Last Played", compareBy(nullsFirst<String>()) { g: GuideGame -> g.lastPlayed }.thenBy { it.name.lowercase() }),
        NAME("Name", compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val esde = prefs.esdeTree
        if (esde == null) message = "Set the ES-DE data folder in settings first" else thread {
            val start = SystemClock.elapsedRealtime()
            val found = runCatching { scan(esde, prefs.romsTree) }
            Log.i(TAG, "guides scan: ${SystemClock.elapsedRealtime() - start} ms")
            runOnUiThread {
                found.onSuccess { entries = it }.onFailure { message = "Couldn't read the guides: ${it.message}" }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    /** Background thread: every system's guides below downloaded_media, matched to its gamelist.xml games. */
    private fun scan(esde: Uri, roms: Uri?): List<Entry> {
        val media = children(this, esde, DocumentsContract.getTreeDocumentId(esde) + "/downloaded_media")
        // ROM folder listings, once per folder. Without a ROMs folder nothing is flagged.
        val romFolders = HashMap<String, Set<String>>()
        fun romMissing(g: GuideGame): Boolean {
            if (roms == null) return false
            val names = romFolders.getOrPut("${g.system}/${g.dir}") {
                val id = (listOf(DocumentsContract.getTreeDocumentId(roms), g.system) + g.dir).filter { it.isNotEmpty() }
                runCatching { children(this, roms, id.joinToString("/")).keys }.getOrDefault(emptySet())
            }
            return names.none { it == g.base || it.substringBeforeLast('.') == g.base }
        }
        return media.flatMap { (system, id) ->
            val guides = runCatching { children(this, esde, id)["guides"] }.getOrNull() ?: return@flatMap emptyList()
            val files = mutableListOf<GuideFile>().also { walk(esde, guides, "", it) }
            val games = runCatching {
                contentResolver.openInputStream(treeChild(esde, "gamelists/$system/gamelist.xml"))!!.use(::gamelistGames)
            }.getOrDefault(emptyList<GamelistGame>())
            guideGames(system, files, games).map { Entry(it, romMissing(it)) }
        }
    }

    /** Adds the files below the folder [dir] to [out], with paths relative to it after [prefix]. */
    private fun walk(tree: Uri, dir: String, prefix: String, out: MutableList<GuideFile>) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        contentResolver.query(uri, columns, null, null, null)!!.use { c ->
            while (c.moveToNext()) {
                val path = prefix + c.getString(1)
                if (c.getString(2) == Document.MIME_TYPE_DIR) walk(tree, c.getString(0), "$path/", out)
                else out += GuideFile(path, c.getLong(3), c.getString(0))
            }
        }
    }

    /** Deletes [files] of [e] in the background, then drops them from the list, and the game once it has none left. */
    private fun delete(e: Entry, files: List<GuideFile>) {
        val esde = Prefs(this).esdeTree ?: return
        thread {
            val g = e.game
            val guides = DocumentsContract.getTreeDocumentId(esde) + "/downloaded_media/${g.system}/guides/"
            var error: String? = null
            val deleted = files.filter { f ->
                runCatching {
                    // The scan lists only files below this system's guides folder; never delete anything else.
                    check(f.id.startsWith(guides)) { "not a guide: ${f.id}" }
                    DocumentsContract.deleteDocument(contentResolver, DocumentsContract.buildDocumentUriUsingTree(esde, f.id))
                }.onFailure { Log.w(TAG, "deleting ${f.id} failed", it) }.getOrDefault(false)
                    .also { if (!it) error = "Couldn't delete ${f.path.substringAfterLast('/')}" }
            }.toSet()
            // The game's <base>/ folder goes once it's empty, as the in-game menu does it.
            val folder = guides + listOf(g.dir, g.base).filter { it.isNotEmpty() }.joinToString("/")
            if (deleted.isNotEmpty() && runCatching { children(this, esde, folder).isEmpty() }.getOrDefault(false)) {
                runCatching { DocumentsContract.deleteDocument(contentResolver, DocumentsContract.buildDocumentUriUsingTree(esde, folder)) }
            }
            runOnUiThread {
                entries = entries?.mapNotNull {
                    if (it.key != e.key) it
                    else (it.game.files - deleted).takeIf(List<GuideFile>::isNotEmpty)
                        ?.let { left -> it.copy(game = it.game.copy(files = left), error = error) }
                }
            }
        }
    }

    @Composable
    private fun Screen() {
        val search = rememberTextFieldState()
        val query = search.text.toString()
        var sort by remember { mutableStateOf(Sort.SIZE) }
        var open by remember { mutableStateOf<String?>(null) }
        val list = rememberLazyListState()
        // A new order or filter starts at the top; the list would otherwise stay on the row it showed first.
        LaunchedEffect(sort, query) { list.scrollToItem(0) }
        val all = entries
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Manage Guides", style = MaterialTheme.typography.headlineSmall)
            when {
                all == null -> Text(message ?: "Loading…")
                all.isEmpty() -> Text("No saved guides")
                else -> {
                    Text("${all.sumOf { it.game.files.size }} guides, ${size(all.sumOf { it.game.size })}")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The keyboard waits for a tap: on focus it would cover the list the first time the D-pad moves.
                        // The String overload shows it on focus regardless.
                        NoExtractUi {
                            OutlinedTextField(
                                search,
                                Modifier.weight(1f),
                                label = { Text("Search") },
                                trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { search.clearText() }) {
                                        Text("✕", Modifier.clearAndSetSemantics { contentDescription = "Clear search" })
                                    }
                                },
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                            )
                        }
                        for (s in Sort.entries) FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                    }
                    val shown = all.filter { it.game.name.contains(query.trim(), ignoreCase = true) }
                        .sortedWith(compareBy(sort.order) { it.game })
                    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(shown, key = { it.key }) { e ->
                            Game(e, open == e.key) { open = if (open == e.key) null else e.key }
                        }
                    }
                }
            }
        }
        confirm?.let { (question, action) ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                text = { Text(question) },
                confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep") } },
            )
        }
    }

    /**
     * Keeps the keyboard from going fullscreen with its own copy of the field, as it does in landscape: Compose
     * doesn't keep that copy in sync, so typing and deleting showed only once the keyboard closed.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun NoExtractUi(content: @Composable () -> Unit) = InterceptPlatformTextInput({ request, next ->
        next.startInputMethod(object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                }
        })
    }, content)

    /** A game's row; tapping it shows its guides, each with Delete, and Delete All. */
    @Composable
    private fun Game(e: Entry, expanded: Boolean, toggle: () -> Unit) {
        val g = e.game
        // An opened game scrolls fully into view: the last row's guides would otherwise open below the screen.
        val view = remember { BringIntoViewRequester() }
        LaunchedEffect(expanded) { if (expanded) view.bringIntoView() }
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(view)) {
            Column(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 8.dp)) {
                Text(g.name)
                Text(
                    listOfNotNull(
                        g.system,
                        if (g.files.size == 1) "1 guide" else "${g.files.size} guides",
                        size(g.size),
                        g.lastPlayed?.let(::isoDate)?.let { "Last played $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (e.romMissing) Text("ROM missing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                e.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (expanded) {
                for (f in g.files) {
                    val name = f.path.substringAfterLast('/')
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(size(f.size), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { confirm = "Delete \"$name\"?" to { delete(e, listOf(f)) } }) { Text("Delete") }
                    }
                }
                val all = if (g.files.size == 1) "the guide" else "the ${g.files.size} guides"
                OutlinedButton(onClick = { confirm = "Delete $all for ${g.name}?" to { delete(e, g.files) } }, Modifier.padding(start = 16.dp)) {
                    Text("Delete All")
                }
            }
        }
    }

    private fun size(bytes: Long) = Formatter.formatShortFileSize(this, bytes)
}
