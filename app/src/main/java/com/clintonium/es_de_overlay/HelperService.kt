package com.clintonium.es_de_overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clintonium.es_de_overlay.core.GameInfo
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.GuideSite
import com.clintonium.es_de_overlay.core.Launch
import com.clintonium.es_de_overlay.core.gamelistInfo
import com.clintonium.es_de_overlay.core.guideEntries
import com.clintonium.es_de_overlay.core.guidePath
import com.clintonium.es_de_overlay.core.manualPath
import com.clintonium.es_de_overlay.core.mediaPath
import com.clintonium.es_de_overlay.core.nextLaunch
import com.clintonium.es_de_overlay.core.resolveGame
import com.clintonium.es_de_overlay.core.startUrl
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

const val TAG = "ESDEShot"
const val ESDE = "org.es_de.frontend"
private const val CHANNEL = "status"
private const val ONGOING_ID = 1
private const val RESULT_ID = 2

/** Posts (or replaces) notification [id]; tapping it opens the settings screen, with [grant] asking for capture consent. */
fun notify(context: Context, id: Int, text: String, grant: Boolean = false, ongoing: Boolean = false): Notification {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Status", NotificationManager.IMPORTANCE_LOW))
    val open = Intent(context, SettingsActivity::class.java)
        .putExtra(SettingsActivity.EXTRA_GRANT, grant)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val n = Notification.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("ES-DE Overlay")
        .setContentText(text)
        .setStyle(Notification.BigTextStyle().bigText(text))
        .setOngoing(ongoing)
        .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()
    if (!ongoing) nm.notify(id, n)
    return n
}

/** Document URI for [path] below a SAF tree from the external storage provider (document ids are `volume:dir/sub`). */
fun treeChild(tree: Uri, path: String): Uri =
    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree) + "/" + path)

/** Reads the whole lines appended to ES-DE's es_log.txt since the last [read]; one instance per reading thread. */
class EsLogTail {
    private var offset = 0L
    private var head = ""

    /** The new lines below the ES-DE folder [tree]; [onNewLog] runs first when ES-DE started a new log, read from its start. */
    fun read(context: Context, tree: Uri, onNewLog: () -> Unit): List<String> {
        context.contentResolver.openFileDescriptor(treeChild(tree, "logs/es_log.txt"), "r")!!.use { pfd ->
            val ch = FileInputStream(pfd.fileDescriptor).channel
            val size = ch.size()
            val first = ByteBuffer.allocate(minOf(size, 64L).toInt()).also { ch.read(it, 0) }.array().decodeToString()
            if (first != head || size < offset) {
                head = first
                offset = 0
                onNewLog()
            }
            if (size == offset) return emptyList()
            val bytes = ByteBuffer.allocate((size - offset).toInt()).also { ch.read(it, offset) }.array()
            val end = bytes.lastIndexOf('\n'.code.toByte())
            if (end < 0) return emptyList()
            offset += end + 1
            return bytes.decodeToString(0, end).lines()
        }
    }
}

/** The package of the most recently resumed activity, tracked incrementally from usage events. */
class ForegroundApp {
    private var since = 0L
    private var pkg: String? = null

    @Synchronized
    fun read(context: Context): String? {
        val now = System.currentTimeMillis()
        val from = if (since == 0L) now - TimeUnit.DAYS.toMillis(1) else since - 2_000
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) pkg = e.packageName
        since = now
        return pkg
    }
}

/** A context for overlay windows on the built-in display. */
fun overlayContext(context: Context): Context = context.createWindowContext(
    context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    null,
)

/** The ROMs root as ES-DE logs it (absolute path) and as SAF document id, e.g. `/storage/7BEF-E760/ROMs`, `7BEF-E760:ROMs`. */
fun romRoots(romsTree: Uri): List<String> {
    val docId = DocumentsContract.getTreeDocumentId(romsTree)
    val volume = docId.substringBefore(':')
    val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    return listOf("$base/${docId.substringAfter(':')}".trimEnd('/'), docId)
}

class HelperService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor()
    private lateinit var prefs: Prefs
    private lateinit var menu: MenuOverlay
    private lateinit var screenshots: ScreenshotObserver
    private lateinit var windowContext: Context
    private var projection: MediaProjection? = null
    private var capture: Capture? = null
    /** Main thread: the cancel flag of the running Find Guides check or save, set when the menu goes away. */
    private var finding: AtomicBoolean? = null

    // Written on the io thread, read on main.
    @Volatile private var launch: Launch? = null
    @Volatile private var game: GameRef? = null
    @Volatile private var foreground: String? = null

    // io thread only.
    private val log = EsLogTail()
    // The io thread's poll, and the main thread on Back.
    private val front = ForegroundApp()

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        windowContext = overlayContext(this)
        menu = MenuOverlay(windowContext, prefs)
        screenshots = ScreenshotObserver(this, main, ::onScreenshot)
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) screenshots.register()
        io.scheduleWithFixedDelay({
            try {
                poll()
            } catch (e: Exception) {
                Log.w(TAG, "poll failed", e)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    /**
     * Every start returns sticky (Android keeps only the latest start's answer): after Android ends the process for
     * memory, it starts the service again with no intent, so without capture consent. Stopping the service ends it for good.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OPEN_FAILED) {
            menu.error("Couldn't open ${intent.getStringExtra(EXTRA_NAME)}")
            return START_STICKY
        }
        if (intent?.action == ACTION_GUIDE_SAVED) {
            val g = currentGame()
            val name = launch?.name
            if (menu.isOpen && g != null && name != null) relist(g, name)
            return START_STICKY
        }
        // The microphone type lets playback capture record game audio while the game is in front.
        startForeground(
            ONGOING_ID,
            notify(this, ONGOING_ID, "Ready", ongoing = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                (if (audioGranted()) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0),
        )
        val data = intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        if (data != null) {
            projection?.stop()
            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data)
                ?.apply { registerCallback(ProjectionLost(), main) }
            Log.i(TAG, "capture consent granted: ${projection != null}")
        }
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        running = false
        capture?.stop()
        closeViewer()
        menu.release()
        menu.endCapture()
        screenshots.unregister()
        io.shutdownNow()
        projection?.stop()
        super.onDestroy()
    }

    private fun audioGranted() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** The game to file media under: the last ES-DE launch, only while its package is the app in front ([fg]). */
    private fun currentGame(fg: String? = foreground): GameRef? = launch?.takeIf { it.pkg != null && it.pkg == fg }?.let { game }

    /** [currentGame] with the app in front read now rather than at the last poll, which can be a second old. */
    private fun gameInFront(): GameRef? = currentGame(front.read(this))

    private fun poll() {
        readLog()
        val fg = front.read(this)
        if (fg != foreground) {
            foreground = fg
            Log.i(TAG, "foreground: $fg (current game: ${currentGame()})")
        }
        main.post { onForeground(fg) }
    }

    /** Main thread: when the game is left, close the menu and end a recording. */
    private fun onForeground(fg: String?) {
        if (fg == launch?.pkg) return
        closeMenu()
        capture?.stop()
    }

    /** Main thread: closes the menu and any viewer or browser over it, and cancels a Find Guides check or save. */
    private fun closeMenu() {
        finding?.set(true)
        closeViewer()
        menu.release()
    }

    /**
     * Main thread: the controller's Back. Stops a recording; with the menu (and any viewer or browser over it) open, closes
     * all of it so the game resumes; otherwise opens the menu for the game in front, if ES-DE launched one.
     */
    private fun onBack() {
        capture?.let {
            it.stop()
            return
        }
        if (menu.isOpen) {
            closeMenu()
            return
        }
        val g = currentGame() ?: return
        // ES-DE's name for the game, which Find Guides looks up.
        val name = launch?.name ?: return
        io.execute {
            val (manual, guides, info) = runCatching { documents(g) }
                .onFailure { Log.w(TAG, "looking up manual and guides failed", it) }
                .getOrDefault(Triple(null, emptyList(), null))
            val art = runCatching { screenshot(g) }.getOrNull()
            val inFront = gameInFront() == g
            main.post {
                // The game may have been left while the lookup ran.
                if (!inFront) {
                    art?.recycle()
                    return@post
                }
                menu.title = name
                menu.art = art
                openMenu(g, name, manual, guides, info)
            }
        }
    }

    /** Main thread: the main menu with the cursor on row [start]. */
    private fun openMenu(g: GameRef, name: String, manual: Doc?, guides: List<Doc>, info: GameInfo?, start: Int = 0) {
        val guidesRow = if (guides.isNotEmpty()) "Guides" else prefs.esdeTree?.let { "Find Guides" }
        // ES-DE adds an entry with only a play count when it launches a game; that gives Game Info nothing to show.
        val infoRow = info?.takeIf { it != GameInfo(null, null, null, null, null, null) }?.let { "Game Info" }
        val items = listOfNotNull(
            "Resume", guidesRow, manual?.let { "Manual" }, infoRow, "Capture", "Quit Game",
        )
        menu.show(items, { i ->
            // B in a screen opened from here comes back to its row.
            val back = { openMenu(g, name, manual, guides, info, i) }
            when (items[i]) {
                "Resume" -> menu.release()
                "Manual" -> view(manual!!)
                "Find Guides" -> findGuides(g, name, back)
                "Game Info" -> menu.info(
                    listOfNotNull(
                        info!!.developer?.let { "Developer" to it },
                        info.publisher?.let { "Publisher" to it },
                        info.released?.let { "Released" to it },
                        info.genre?.let { "Genre" to it },
                        info.players?.let { "Players" to it },
                    ),
                    info.description,
                    back,
                )
                "Capture" -> menu.show(listOf("Screenshot", "Video", "Screenshot and Video"), { mode ->
                    menu.release()
                    startCapture(g, keepShot = mode != 1, keepVideo = mode != 0)
                }, back)
                "Quit Game" -> quitGame()
                else -> guidesList(g, name, guides, back)
            }
        }, menu::release, start = start)
    }

    /** Main thread: the game's [guides], then Find Guides, with the cursor on [start]. X on a guide asks to delete it. */
    private fun guidesList(g: GameRef, name: String, guides: List<Doc>, back: () -> Unit, start: Int = 0) {
        menu.show(guides.map { it.name } + "Find Guides", { i ->
            if (i < guides.size) view(guides[i]) else findGuides(g, name) { guidesList(g, name, guides, back, i) }
        }, back, "Delete" to { i ->
            if (i < guides.size) confirmDelete(g, name, guides[i], i) { guidesList(g, name, guides, back, i) }
        }, start)
    }

    /** Main thread: Delete removes [doc] (row [at] of the Guides list) and lists the guides again; Keep and B go to [keep]. */
    private fun confirmDelete(g: GameRef, name: String, doc: Doc, at: Int, keep: () -> Unit) {
        menu.show(listOf("Delete \"${doc.name}\"", "Keep"), { i ->
            if (i == 1) {
                keep()
                return@show
            }
            io.execute {
                val deleted = runCatching { deleteGuide(g, doc) }.onFailure { Log.w(TAG, "deleting ${doc.key} failed", it) }
                main.post {
                    if (deleted.getOrDefault(false)) relist(g, name, at) else menu.show(listOf("Couldn't delete ${doc.name}"), {}, keep)
                }
            }
        }, keep)
    }

    /**
     * Main thread: lists the game's guides again and shows them with the cursor on [start], or the main menu when none
     * are left. Does nothing if the menu has closed meanwhile.
     */
    private fun relist(g: GameRef, name: String, start: Int = 0) {
        io.execute {
            val (manual, guides, info) = runCatching { documents(g) }
                .onFailure { Log.w(TAG, "looking up manual and guides failed", it) }
                .getOrDefault(Triple(null, emptyList(), null))
            main.post {
                if (!menu.isOpen) return@post
                // The row after Resume: Guides, or Find Guides once none are left.
                val toMenu = { openMenu(g, name, manual, guides, info, start = 1) }
                if (guides.isEmpty()) toMenu() else guidesList(g, name, guides, toMenu, start)
            }
        }
    }

    /** Main thread: force-stops the game's app, which then has no chance to save, and goes to ES-DE. */
    private fun quitGame() {
        val pkg = launch?.pkg ?: return
        menu.release()
        io.execute { launch = null }
        ButtonService.forceStop(pkg)
    }

    /**
     * Main thread: checks VGMaps, then StrategyWiki, for the game and lists those that have it, then the browser
     * sites, which aren't contacted until picked. Picking a checked source saves it and opens it. Requests run one
     * at a time on their own thread, and only from here. B cancels and goes [back].
     */
    private fun findGuides(g: GameRef, name: String, back: () -> Unit) {
        val cancelled = AtomicBoolean().also { finding = it }
        val cancel = {
            cancelled.set(true)
            back()
        }
        menu.show(listOf("Checking sources…"), {}, cancel)
        thread(name = "guides") {
            val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
            val failures = mutableListOf<Throwable>()
            fun <T> check(source: String, lookup: () -> T): T? =
                runCatching(lookup).onFailure { Log.w(TAG, "$source check failed", it); failures += it }.getOrNull()
            val maps = check("VGMaps") { vgmaps(g.system, name) }.orEmpty()
            val wiki = check("StrategyWiki") { strategyWiki(name) }
            val writer = MediaWriter(this, prefs.esdeTree!!)
            val progress: (String) -> Unit = { label -> main.post { if (!cancelled.get()) menu.relabel(0, label) } }
            val found = buildList<Pair<String, () -> String>> {
                if (maps.isNotEmpty()) {
                    add("VGMaps: ${maps.size} Maps" to { saveVgmaps(writer, cacheDir, g, dir, name, maps, progress, cancelled::get) })
                }
                wiki?.let { (title, pages) ->
                    add("StrategyWiki: $title (${pages.size} Pages)" to {
                        saveStrategyWiki(writer, cacheDir, g, dir, title, pages, progress, cancelled::get)
                    })
                }
            }
            main.post {
                when {
                    cancelled.get() -> {}
                    // A row rather than a toast: toasts draw under the menu's window.
                    found.isEmpty() && failures.isNotEmpty() -> menu.show(listOf("Couldn't reach the guide sources"), {}, back)
                    else -> {
                        val rows = found.map { (label, save) -> label to { saveGuide(g, name, save, cancelled, back) } } + browserRows(g, name)
                        menu.show(rows.map { it.first }, { rows[it].second() }, cancel)
                    }
                }
            }
        }
    }

    /**
     * The sites only a browser gets into, which open over this menu at the game's search, or ZeldaDungeon's walkthrough.
     * Closing the browser shows the menu again; a guide saved there opens in the viewer.
     */
    private fun browserRows(g: GameRef, name: String): List<Pair<String, () -> Unit>> = GuideSite.entries.mapNotNull { site ->
        startUrl(site, name)?.let { url ->
            (if (site == GuideSite.ZELDADUNGEON) site.label else "Search ${site.label}") to { startService(ViewerService.browse(this, url, g)) }
        }
    }

    /** Main thread: runs [save] with progress in the menu, then reopens the menu with the new guide and opens it. */
    private fun saveGuide(g: GameRef, name: String, save: () -> String, cancelled: AtomicBoolean, back: () -> Unit) {
        menu.show(listOf("Saving…"), {}, {
            cancelled.set(true)
            back()
        })
        thread(name = "guides") {
            val saved = runCatching(save).onFailure { if (it !is CancellationException) Log.w(TAG, "saving a guide failed", it) }
            val docs = saved.getOrNull()?.let { runCatching { documents(g) }.getOrNull() }
            main.post {
                if (cancelled.get()) return@post
                saved.onFailure {
                    menu.show(listOf("Couldn't save the guide"), {}, back)
                    return@post
                }
                val (manual, guides, info) = docs ?: Triple(null, emptyList(), null)
                openMenu(g, name, manual, guides, info)
                guides.firstOrNull { it.key == saved.getOrNull() }?.let(::view)
            }
        }
    }

    /** Opens [doc] over the menu, which takes focus back when the viewer closes. */
    private fun view(doc: Doc) {
        startService(ViewerService.intent(this, doc.uri, doc.name, doc.key))
    }

    private fun closeViewer() = stopService(Intent(this, ViewerService::class.java))

    /** A manual or guide; [key] is its path below the ES-DE folder, which the viewer files its position under. */
    private class Doc(val name: String, val uri: Uri, val key: String)

    /**
     * io thread: the game's manual and guides that exist, found by listing their folders below downloaded_media, and its
     * gamelist.xml metadata when the gamelist has it.
     */
    private fun documents(g: GameRef): Triple<Doc?, List<Doc>, GameInfo?> {
        val tree = prefs.esdeTree ?: return Triple(null, emptyList(), null)
        val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
        val media = DocumentsContract.getTreeDocumentId(tree) + "/downloaded_media/"
        fun list(id: String) = runCatching { children(this, tree, id) }.getOrDefault(emptyMap())
        fun doc(name: String, id: String, path: String) =
            Doc(name, DocumentsContract.buildDocumentUriUsingTree(tree, id), "downloaded_media/$path")
        val manual = manualPath(g, dir).let { path ->
            list(media + path.dropLast(1).joinToString("/"))[path.last()]?.let { doc(path.last(), it, path.joinToString("/")) }
        }
        val path = guidePath(g, dir)
        val base = path.last()
        val folder = list(media + path.dropLast(1).joinToString("/"))
        val inner = folder[base]?.let(::list).orEmpty()
        val guides = guideEntries(base, folder.keys, inner.keys).map { entry ->
            val name = entry.substringAfter("$base/")
            val id = if (entry.startsWith("$base/")) inner.getValue(name) else folder.getValue(entry)
            doc(name, id, (path.dropLast(1) + entry).joinToString("/"))
        }
        val info = runCatching {
            contentResolver.openInputStream(treeChild(tree, "gamelists/${g.system}/gamelist.xml"))!!.use { gamelistInfo(it, g.romRelativePath) }
        }.getOrNull()
        return Triple(manual, guides, info)
    }

    /** io thread: deletes [g]'s guide [doc], and the game's `<base>/` guides folder when that leaves it empty. */
    private fun deleteGuide(g: GameRef, doc: Doc): Boolean {
        // documents() lists guides only below the game's guides path; never delete anything else.
        check(doc.key.startsWith("downloaded_media/${g.system}/guides/")) { "not a guide: ${doc.key}" }
        if (!DocumentsContract.deleteDocument(contentResolver, doc.uri)) return false
        val tree = prefs.esdeTree!!
        val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
        val folder = DocumentsContract.getTreeDocumentId(tree) + "/downloaded_media/" + guidePath(g, dir).joinToString("/")
        if (DocumentsContract.getDocumentId(doc.uri).startsWith("$folder/") && children(this, tree, folder).isEmpty()) {
            DocumentsContract.deleteDocument(contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, folder))
        }
        return true
    }

    /**
     * io thread: the game's screenshot for the menu's art box (PNG, else JPG, as ES-DE looks), or null when it has none.
     * One larger than the box both ways, like a full-screen capture, is decoded at half size.
     */
    private fun screenshot(g: GameRef): Bitmap? {
        val tree = prefs.esdeTree ?: return null
        val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
        for (ext in listOf("png", "jpg")) {
            val uri = treeChild(tree, (listOf("downloaded_media") + mediaPath(g, prefs.mediaType, ext, dir)).joinToString("/"))
            fun decode(options: BitmapFactory.Options) =
                contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Opening a missing file throws.
            if (runCatching { decode(size) }.isFailure) continue
            return decode(BitmapFactory.Options().apply { if (size.outWidth > menu.artW && size.outHeight > menu.artH) inSampleSize = 2 })
        }
        return null
    }

    /** Captures the game and saves the screenshot when [keepShot] and the clip when [keepVideo]. */
    private fun startCapture(g: GameRef, keepShot: Boolean, keepVideo: Boolean) {
        val p = projection ?: run {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_GRANT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        menu.beginCapture()
        // Let the closed menu leave the screen before the first frame.
        main.postDelayed({
            try {
                // A screenshot alone stops as soon as its frame is taken, too soon for a usable clip.
                val onRecording = { if (!keepVideo) capture?.stop() }
                capture = Capture(windowContext, p, prefs.clipSeconds, audioGranted(), menu::nudge, onRecording) { png, mp4 ->
                    capture = null
                    menu.endCapture()
                    io.execute { save(g, png?.takeIf { keepShot }, mp4?.takeIf { keepVideo }) }
                }.also { it.start() }
                Log.i(TAG, "capturing $g")
            } catch (e: Exception) {
                Log.e(TAG, "capture failed to start", e)
                menu.endCapture()
                toast("Capture failed: ${e.message}")
                notify(this, RESULT_ID, "Capture failed: ${e.message}")
            }
        }, 100)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** A system screenshot: file it like a captured frame, or leave it where it is when no game is in front. */
    private fun onScreenshot(uri: Uri, mime: String) {
        val g = currentGame() ?: run {
            notify(this, RESULT_ID, "Screenshot left in Screenshots: no ES-DE game detected")
            return
        }
        io.execute {
            val png = File(cacheDir, "system.png")
            runCatching {
                contentResolver.openInputStream(uri)!!.use { input ->
                    png.outputStream().use { out ->
                        if (mime == "image/png") input.copyTo(out)
                        else BitmapFactory.decodeStream(input).compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }.onSuccess { save(g, png, null) }.onFailure {
                Log.e(TAG, "reading screenshot failed", it)
                notify(this, RESULT_ID, "Couldn't read screenshot: ${it.message}")
            }
        }
    }

    /** io thread: file the capture under the game's ES-DE media folders. */
    private fun save(g: GameRef, png: File?, mp4: File?) {
        val written = mutableListOf<String>()
        val result = runCatching {
            val writer = MediaWriter(this, prefs.esdeTree!!)
            val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
            fun put(file: File?, type: String, ext: String, mime: String) {
                if (file == null) return
                writer.write(listOf("downloaded_media") + mediaPath(g, type, ext, dir), mime, prefs.overwrite) { out ->
                    file.inputStream().use { it.copyTo(out) }
                }?.let { written += it.removePrefix("downloaded_media/") }
                file.delete()
            }
            put(png, prefs.mediaType, "png", "image/png")
            put(mp4, "videos", "mp4", "video/mp4")
        }
        result.exceptionOrNull()?.let { Log.e(TAG, "save failed", it) }
        val text = when {
            result.isFailure -> "Couldn't save: ${result.exceptionOrNull()?.message}"
            written.isNotEmpty() -> "Saved: ${written.joinToString()}"
            png == null && mp4 == null -> "Nothing captured"
            else -> "Skipped: files already exist"
        }
        main.post { toast(text) }
        notify(this, RESULT_ID, text)
        Log.i(TAG, "saved $written for $g")
    }

    /** Folds the lines ES-DE logged since the last poll into its latest launch. */
    private fun readLog() {
        val tree = prefs.esdeTree ?: return
        val lines = log.read(this, tree) { launch = null }
        val before = launch
        var latest = before
        lines.forEach { latest = nextLaunch(latest, it) }
        if (latest != before && latest != null) {
            launch = latest
            game = resolve(latest)
            Log.i(TAG, "launch: ${latest.name} (${latest.system}) pkg=${latest.pkg} -> $game")
        }
    }

    private fun resolve(l: Launch): GameRef? {
        val esde = prefs.esdeTree ?: return null
        val roms = prefs.romsTree ?: return null
        return resolveGame(l, romRoots(roms)) {
            runCatching { contentResolver.openInputStream(treeChild(esde, "gamelists/${l.system}/gamelist.xml")) }.getOrNull()
        }
    }

    private inner class ProjectionLost : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "capture consent lost")
            projection = null
            notify(this@HelperService, ONGOING_ID, "Capture permission lost — tap to grant again", grant = true)
        }
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        /** From the viewer's process: opening [EXTRA_NAME] failed, which the menu underneath shows. */
        const val ACTION_OPEN_FAILED = "com.clintonium.es_de_overlay.OPEN_FAILED"
        /** From the viewer's process: the viewer on a guide just saved from the browser has closed, so list the guides again. */
        const val ACTION_GUIDE_SAVED = "com.clintonium.es_de_overlay.GUIDE_SAVED"
        const val EXTRA_NAME = "name"
        var running by mutableStateOf(false)

        // Main thread only; set while the service exists.
        @SuppressLint("StaticFieldLeak")
        private var instance: HelperService? = null

        /** Main thread: whether the controller's Back is the overlay's: a capture is running, the menu is open, or ES-DE's game is in front. */
        fun takesBack(): Boolean = instance?.run { running && (capture != null || menu.isOpen || gameInFront() != null) } == true

        /** Main thread: the controller's Back, taken because [takesBack] said so. */
        fun back() {
            instance?.onBack()
        }

        /** Main thread: the controller's Home, which Android still gets. Closes the menu now rather than at the next poll. */
        fun home() {
            instance?.closeMenu()
        }
    }
}
