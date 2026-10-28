package com.clintonium.esderecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clintonium.esderecorder.core.GameRef
import com.clintonium.esderecorder.core.Launch
import com.clintonium.esderecorder.core.nextLaunch
import com.clintonium.esderecorder.core.resolveGame
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val TAG = "ESDEShot"
const val ESDE_PACKAGE = "org.es_de.frontend"
private const val CHANNEL = "status"
private const val ONGOING_ID = 1

/** Posts (or replaces) notification [id]; tapping it opens the settings screen, with [grant] asking for capture consent. */
fun notify(context: Context, id: Int, text: String, grant: Boolean = false, ongoing: Boolean = false): Notification {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Status", NotificationManager.IMPORTANCE_LOW))
    val open = Intent(context, SettingsActivity::class.java)
        .putExtra(SettingsActivity.EXTRA_GRANT, grant)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val n = Notification.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("ES-DE Recorder")
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
    private var projection: MediaProjection? = null

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
        io.scheduleWithFixedDelay({
            try {
                poll()
            } catch (e: Exception) {
                Log.w(TAG, "poll failed", e)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            ONGOING_ID,
            notify(this, ONGOING_ID, "Ready", ongoing = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
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
        io.shutdownNow()
        projection?.stop()
        super.onDestroy()
    }

    /** The game to file media under: the last ES-DE launch, only while its package is the app in front. */
    private fun currentGame(): GameRef? = launch?.takeIf { it.pkg != null && it.pkg == foreground }?.let { game }

    private fun poll() {
        readLog()
        val fg = foregroundPackage()
        if (fg != foreground) {
            foreground = fg
            Log.i(TAG, "foreground: $fg (current game: ${currentGame()})")
        }
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
        var running by mutableStateOf(false)
    }
}
