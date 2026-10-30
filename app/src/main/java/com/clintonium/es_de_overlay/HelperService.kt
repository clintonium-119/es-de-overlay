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
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.Launch
import com.clintonium.es_de_overlay.core.guideEntries
import com.clintonium.es_de_overlay.core.guidePath
import com.clintonium.es_de_overlay.core.manualPath
import com.clintonium.es_de_overlay.core.mediaPath
import com.clintonium.es_de_overlay.core.nextLaunch
import com.clintonium.es_de_overlay.core.resolveGame
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val TAG = "ESDEShot"
const val ESDE_PACKAGE = "org.es_de.frontend"
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
    private lateinit var viewer: ViewerOverlay
    private lateinit var screenshots: ScreenshotObserver
    private lateinit var windowContext: Context
    private var projection: MediaProjection? = null
    private var capture: Capture? = null
    private var guardAt = 0L

    // Written on the io thread, read on main.
    @Volatile private var launch: Launch? = null
    @Volatile private var game: GameRef? = null
    @Volatile private var foreground: String? = null

    // io thread only.
    private var logOffset = 0L
    private var logHead = ""
    private var eventsSince = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        windowContext = createWindowContext(
            getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            null,
        )
        menu = MenuOverlay(windowContext, prefs)
        viewer = ViewerOverlay(windowContext)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HOME_GUARD) {
            Log.i(TAG, "home guard")
            onHomeGuard()
            return START_NOT_STICKY
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
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        capture?.stop()
        viewer.close()
        menu.close()
        menu.endCapture()
        screenshots.unregister()
        io.shutdownNow()
        projection?.stop()
        super.onDestroy()
    }

    private fun audioGranted() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** The game to file media under: the last ES-DE launch, only while its package is the app in front. */
    private fun currentGame(): GameRef? = launch?.takeIf { it.pkg != null && it.pkg == foreground }?.let { game }

    private fun poll() {
        readLog()
        val fg = foregroundPackage()
        if (fg != foreground) {
            foreground = fg
            Log.i(TAG, "foreground: $fg (current game: ${currentGame()})")
        }
        main.post { onForeground(fg) }
    }

    /** Main thread: when the game is left, close the menu and end a recording. */
    private fun onForeground(fg: String?) {
        if (fg == launch?.pkg) return
        viewer.close()
        menu.close()
        capture?.stop()
    }

    /**
     * Main thread: Retroid's first-Home toast. Stops a recording; with the menu or viewer open, goes home
     * (Home pressed twice); otherwise opens the menu for the game in front, if ES-DE launched one.
     */
    private fun onHomeGuard() {
        capture?.let {
            it.stop()
            return
        }
        if (menu.isOpen || viewer.isOpen) {
            viewer.close()
            menu.close()
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val g = currentGame() ?: return
        guardAt = SystemClock.uptimeMillis()
        io.execute {
            val (manual, guides) = runCatching { documents(g) }
                .onFailure { Log.w(TAG, "looking up manual and guides failed", it) }
                .getOrDefault(null to emptyList())
            main.post { openMenu(g, manual, guides) }
        }
    }

    private fun openMenu(g: GameRef, manual: Doc?, guides: List<Doc>) {
        val items = listOfNotNull("Capture", manual?.let { "Manual" }, guides.firstOrNull()?.let { "Guide" })
        menu.show(items, { i ->
            when (items[i]) {
                "Capture" -> {
                    menu.close()
                    startCapture(g)
                }
                "Manual" -> view(manual!!)
                // Several guides get their own list; B there returns to this menu.
                else -> if (guides.size == 1) view(guides[0]) else menu.show(guides.map { it.name }, { view(guides[it]) }) {
                    openMenu(g, manual, guides)
                }
            }
        }) {}
    }

    /** Opens [doc] over the menu, which takes focus back when the viewer closes. */
    private fun view(doc: Doc) {
        runCatching { viewer.open(doc.uri, doc.name) {} }.onFailure {
            Log.e(TAG, "opening ${doc.name} failed", it)
            toast("Couldn't open ${doc.name}: ${it.message}")
        }
    }

    private class Doc(val name: String, val uri: Uri)

    /** io thread: the game's manual and guides that exist, found by listing their folders below downloaded_media. */
    private fun documents(g: GameRef): Pair<Doc?, List<Doc>> {
        val tree = prefs.esdeTree ?: return null to emptyList()
        val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
        val media = DocumentsContract.getTreeDocumentId(tree) + "/downloaded_media/"
        fun list(id: String) = runCatching { children(this, tree, id) }.getOrDefault(emptyMap())
        fun doc(name: String, id: String) = Doc(name, DocumentsContract.buildDocumentUriUsingTree(tree, id))
        val manual = manualPath(g, dir).let { path ->
            list(media + path.dropLast(1).joinToString("/"))[path.last()]?.let { doc(path.last(), it) }
        }
        val path = guidePath(g, dir)
        val base = path.last()
        val folder = list(media + path.dropLast(1).joinToString("/"))
        val inner = folder[base]?.let(::list).orEmpty()
        val guides = guideEntries(base, folder.keys, inner.keys).map { entry ->
            val name = entry.substringAfter("$base/")
            doc(name, if (entry.startsWith("$base/")) inner.getValue(name) else folder.getValue(entry))
        }
        return manual to guides
    }

    private fun startCapture(g: GameRef) {
        val p = projection ?: run {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_GRANT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        menu.beginCapture()
        // Let the closed menu leave the screen before the first frame, and Retroid's toast too: it stays
        // up about 2.6 s after the Home press.
        main.postDelayed({
            try {
                capture = Capture(windowContext, p, prefs.clipSeconds, audioGranted(), menu::nudge, {}) { png, mp4 ->
                    capture = null
                    menu.endCapture()
                    io.execute { save(g, png, mp4) }
                }.also { it.start() }
                Log.i(TAG, "capturing $g")
            } catch (e: Exception) {
                Log.e(TAG, "capture failed to start", e)
                menu.endCapture()
                toast("Capture failed: ${e.message}")
                notify(this, RESULT_ID, "Capture failed: ${e.message}")
            }
        }, (guardAt + 3_000 - SystemClock.uptimeMillis()).coerceAtLeast(100))
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

    /** Reads bytes appended to es_log.txt since the last poll; starts over when ES-DE rotates the log. */
    private fun readLog() {
        val tree = prefs.esdeTree ?: return
        contentResolver.openFileDescriptor(treeChild(tree, "logs/es_log.txt"), "r")!!.use { pfd ->
            val ch = FileInputStream(pfd.fileDescriptor).channel
            val size = ch.size()
            val head = ByteBuffer.allocate(minOf(size, 64L).toInt()).also { ch.read(it, 0) }.array().decodeToString()
            if (head != logHead || size < logOffset) {
                logHead = head
                logOffset = 0
                launch = null
            }
            if (size == logOffset) return
            val bytes = ByteBuffer.allocate((size - logOffset).toInt()).also { ch.read(it, logOffset) }.array()
            val end = bytes.lastIndexOf('\n'.code.toByte())
            if (end < 0) return
            logOffset += end + 1
            val before = launch
            var latest = before
            bytes.decodeToString(0, end).lineSequence().forEach { latest = nextLaunch(latest, it) }
            if (latest != before && latest != null) {
                launch = latest
                game = resolve(latest)
                Log.i(TAG, "launch: ${latest.name} (${latest.system}) pkg=${latest.pkg} -> $game")
            }
        }
    }

    private fun resolve(l: Launch): GameRef? {
        val esde = prefs.esdeTree ?: return null
        val roms = prefs.romsTree ?: return null
        return resolveGame(l, romRoots(roms)) {
            runCatching { contentResolver.openInputStream(treeChild(esde, "gamelists/${l.system}/gamelist.xml")) }.getOrNull()
        }
    }

    /** Package of the most recently resumed activity, tracked incrementally from usage events. */
    private fun foregroundPackage(): String? {
        val now = System.currentTimeMillis()
        val from = if (eventsSince == 0L) now - TimeUnit.DAYS.toMillis(1) else eventsSince - 2_000
        val events = getSystemService(UsageStatsManager::class.java).queryEvents(from, now)
        val e = UsageEvents.Event()
        var fg = foreground
        while (events.getNextEvent(e)) if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) fg = e.packageName
        eventsSince = now
        return fg
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
        const val ACTION_HOME_GUARD = "com.clintonium.es_de_overlay.HOME_GUARD"
        var running by mutableStateOf(false)
    }
}
