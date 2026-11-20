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
import android.graphics.Color
import android.graphics.drawable.Icon
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clintonium.es_de_overlay.core.GameInfo
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.GuideSite
import com.clintonium.es_de_overlay.core.Launch
import com.clintonium.es_de_overlay.core.LeaveWatch
import com.clintonium.es_de_overlay.core.Look
import com.clintonium.es_de_overlay.core.Overwrite
import com.clintonium.es_de_overlay.core.Pick
import com.clintonium.es_de_overlay.core.PictureSource
import com.clintonium.es_de_overlay.core.RECENT_TAGS
import com.clintonium.es_de_overlay.core.RecentGame
import com.clintonium.es_de_overlay.core.Selection
import com.clintonium.es_de_overlay.core.Steering
import com.clintonium.es_de_overlay.core.Step
import com.clintonium.es_de_overlay.core.SwitcherNeed
import com.clintonium.es_de_overlay.core.contentBounds
import com.clintonium.es_de_overlay.core.detailLine
import com.clintonium.es_de_overlay.core.displaySize
import com.clintonium.es_de_overlay.core.esSetting
import com.clintonium.es_de_overlay.core.followPick
import com.clintonium.es_de_overlay.core.gamelistInfo
import com.clintonium.es_de_overlay.core.guideEntries
import com.clintonium.es_de_overlay.core.guidePath
import com.clintonium.es_de_overlay.core.keptPicturePath
import com.clintonium.es_de_overlay.core.manualPath
import com.clintonium.es_de_overlay.core.mediaPath
import com.clintonium.es_de_overlay.core.nextLaunch
import com.clintonium.es_de_overlay.core.pictureSource
import com.clintonium.es_de_overlay.core.playedFirst
import com.clintonium.es_de_overlay.core.recentGames
import com.clintonium.es_de_overlay.core.resolveGame
import com.clintonium.es_de_overlay.core.retroarchProcesses
import com.clintonium.es_de_overlay.core.retryPick
import com.clintonium.es_de_overlay.core.watchesPick
import com.clintonium.es_de_overlay.core.selection
import com.clintonium.es_de_overlay.core.sessionPicture
import com.clintonium.es_de_overlay.core.systemNames
import com.clintonium.es_de_overlay.core.startUrl
import com.clintonium.es_de_overlay.core.statePicture
import com.clintonium.es_de_overlay.core.steer
import com.clintonium.es_de_overlay.core.steerKeys
import com.clintonium.es_de_overlay.core.switcherNeeds
import com.clintonium.es_de_overlay.core.webSearchUrl
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

const val TAG = "ESDEShot"
const val ESDE = "org.es_de.frontend"
private const val CHANNEL = "status"
private const val ONGOING_ID = 1
private const val RESULT_ID = 2

/** Posts (or replaces) notification [id]; tapping it runs [tap], else opens the settings screen. */
fun notify(context: Context, id: Int, text: String, ongoing: Boolean = false, tap: PendingIntent? = null): Notification {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Status", NotificationManager.IMPORTANCE_LOW))
    val open = Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val n = Notification.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("ES-DE Overlay")
        .setContentText(text)
        .setStyle(Notification.BigTextStyle().bigText(text))
        .setOngoing(ongoing)
        .setContentIntent(tap ?: PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    // The service's own notification: a way into the menu by touch, for controllers whose buttons can't open it.
    if (ongoing) {
        val openMenu = Intent(context, HelperService::class.java).setAction(HelperService.ACTION_OPEN_MENU)
        n.addAction(Notification.Action.Builder(null as Icon?, "Open Menu", PendingIntent.getService(context, 0, openMenu, PendingIntent.FLAG_IMMUTABLE)).build())
    }
    return n.build().also { if (!ongoing) nm.notify(id, it) }
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
        val pfd = context.contentResolver.openFileDescriptor(treeChild(tree, "logs/es_log.txt"), "r")!!
        // Closing the channel closes its stream, which closes the descriptor.
        ParcelFileDescriptor.AutoCloseInputStream(pfd).channel.use { ch ->
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
    private lateinit var switcher: SwitcherOverlay
    private lateinit var themeLook: ThemeLook
    private lateinit var screenshots: ScreenshotObserver
    private lateinit var windowContext: Context
    private var projection: MediaProjection? = null
    private var capture: Capture? = null
    /** Main thread: the cancel flag of the running Find Guides check or save, set when the menu goes away. */
    private var finding: AtomicBoolean? = null
    /** Main thread: set while the menu's lookup runs; the menu opens only if it's still the same one when the lookup ends. */
    private var opening: Any? = null
    /** Main thread: whether a capture is waiting for the closed menu to leave the screen. */
    private var starting = false
    /** Main thread: the capture picked before Android's capture prompt, which goes on once consent arrives. */
    private var afterConsent: (() -> Unit)? = null
    /** Shows a menu left invisible under a reopened document whose viewer never said it was up. */
    private val revealLate = Runnable { menu.reveal() }
    /** Main thread: while the menu asks whether a capture replaces a file, the way out that keeps the file. */
    private var unanswered: (() -> Unit)? = null
    /** Set when a capture starts: its clip has no sound because the game's app couldn't be found. Read by [report]. */
    @Volatile private var noGameSound = false
    /** Main thread. */
    private val leave = LeaveWatch(ESDE)

    // Written on the io thread, read on main.
    @Volatile private var launch: Launch? = null
    @Volatile private var game: GameRef? = null
    @Volatile private var foreground: String? = null
    /**
     * The game ES-DE launched last in PPSSPP or ARMSX2 (`ppsspp`, `armsx2`), which name save states by the disc, and when
     * (epoch seconds), until the pictures saved in its session are looked for.
     */
    @Volatile private var discSession: Triple<GameRef, String, Long>? = null

    // io thread only.
    private val log = EsLogTail()
    // The io thread's poll, and the main thread on Back.
    private val front = ForegroundApp()

    /** ES-DE's Last Played games and the look to show them in, read ahead so the switcher opens at once. */
    @Volatile private var recent: Pair<List<RecentGame>, ThemeLook.Current>? = null
    /** RetroArch's save-state pictures, by when each was written (epoch seconds), as read with [recent]. */
    @Volatile private var states = emptyList<Pair<Long, String>>()
    /** Whether RetroArch starts a game from its auto save state (Auto Load State), as read with [recent]. */
    @Volatile private var retroarchResumes = false
    /** Whether PPSSPP starts a game from a save state (Auto Load Savestate), as read with [recent] once it has saved a picture here. */
    @Volatile private var ppssppResumes = false
    /** Switcher pictures read, by ROM path ending: those either side of the game shown, and a few passed. */
    private val pictures = LruCache<String, Bitmap>(15)
    /** Keys of [pictures] being read. */
    private val reading = ConcurrentHashMap.newKeySet<String>()
    private val pictureReader = Executors.newSingleThreadExecutor()
    /** The game the switcher shows, which [wantPictures] keeps its reads near. */
    @Volatile private var shownGame = 0
    /** Set when another app comes to the front: ES-DE may have played a game since [recent] was read. */
    @Volatile private var recentStale = true
    /** What ES-DE's settings lacked for the switcher when last read; null before they're read or when they can't be. */
    @Volatile private var missing: List<SwitcherNeed>? = null
    /** Main thread: the switcher is up over ES-DE, which a poll then mustn't close. */
    private var switching = false
    /** Main thread: the switcher is steering ES-DE to the picked game. */
    private var steering = false
    /** Main thread: the screen read as the menu opened over the game, for Quit Game to keep; dropped as the menu closes. */
    private var menuScreen: Future<Pair<ByteBuffer, Int>?>? = null
    /** RetroArch's hotkeys when the last launch was in RetroArch, read on the io thread as the launch is logged. */
    @Volatile private var hotkeys: RetroArchHotkeys? = null
    /** Set while the overlay has RetroArch's shader turned off, taken to be on at each launch as RetroArch's config says. */
    @Volatile private var shaderOff = false
    /** Presses of RetroArch's hotkeys, one at a time. */
    private val presses = Executors.newSingleThreadExecutor()
    /** The launch the switcher last pressed, followed through ES-DE's log on the io thread until its game ends. */
    private val pick = AtomicReference<Pick?>()
    /** RetroArch's packages running as the switcher's game was picked, read alongside steering. */
    @Volatile private var runningAtPick: Future<Set<String>>? = null
    /** io thread: set when ES-DE has logged the end of a game the switcher launched, having written its gamelist. */
    private var switcherGameEnded = false
    /** The games the switcher shows, while it's open. */
    @Volatile private var showing: List<RecentGame>? = null
    /** The pictures read before the games were last read again, which the switcher shows until each is read anew. */
    @Volatile private var stale = emptyMap<String, Bitmap>()
    /** Counts the times the games are read again; a picture read before the latest is dropped. */
    @Volatile private var pictureRound = 0
    /**
     * The game just played, as its log suffix, while the newer picture it's expected to have is on its way: until its
     * picture is read after the games are read again, past the [pictureRound] given.
     */
    @Volatile private var awaited: Pair<String, Int>? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        // Logs where each stream, cursor or descriptor that was never closed was opened.
        if (BuildConfig.DEBUG) StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        instance = this
        prefs = Prefs(this)
        windowContext = overlayContext(this)
        menu = MenuOverlay(windowContext)
        switcher = SwitcherOverlay(windowContext)
        themeLook = ThemeLook(windowContext, prefs)
        screenshots = ScreenshotObserver(this, main, ::onScreenshot)
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) screenshots.register()
        // ES-DE's previous log names the systems browsed before ES-DE last started; read first, so the current log's win.
        io.execute {
            val tree = prefs.esdeTree ?: return@execute
            runCatching {
                contentResolver.openInputStream(treeChild(tree, "logs/es_log.txt.bak"))!!.bufferedReader().useLines {
                    prefs.learnSystemNames(systemNames(it))
                }
            }.onFailure { Log.i(TAG, "ES-DE's previous log wasn't read: $it") }
        }
        io.scheduleWithFixedDelay(::safePoll, 0, 1, TimeUnit.SECONDS)
    }

    private fun safePoll() {
        try {
            poll()
        } catch (e: Exception) {
            Log.w(TAG, "poll failed", e)
        }
    }

    /** Set while [pollSoon]'s first poll is waiting to run. */
    private val soon = AtomicBoolean()

    /**
     * Polls a moment from now, and again a little later, as well as each second: a game is being left, and ES-DE is
     * coming back to the front, or has, and may not have logged it yet.
     */
    private fun pollSoon() {
        if (!soon.compareAndSet(false, true)) return
        runCatching {
            io.schedule(Runnable { soon.set(false); safePoll() }, 100, TimeUnit.MILLISECONDS)
            io.schedule(Runnable { safePoll() }, 400, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Starts without capture consent: the menu, guides and Quit Game don't need it, and the first Capture asks.
     * Every start returns sticky (Android keeps only the latest start's answer): after Android ends the process for
     * memory, it starts the service again with no intent. Stopping the service ends it for good.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OPEN_FAILED) {
            // A remembered document that no longer opens is forgotten, so the next press opens the main menu.
            val key = intent.getStringExtra(EXTRA_KEY)
            currentGame()?.let { g -> if (key != null && prefs.lastDoc(g) == key) prefs.setLastDoc(g, null) }
            menu.reveal()
            menu.error("Couldn't open ${intent.getStringExtra(EXTRA_NAME)}" + (intent.getStringExtra(EXTRA_REASON)?.let { ": $it" } ?: ""))
            return START_STICKY
        }
        if (intent?.action == ACTION_OPEN_MENU) {
            ButtonService.closeShade()
            // Only when nothing is up: then the menu button's way opens the menu, if ES-DE's game is in front.
            // The shade may still be over the game, so the screen isn't read for Quit Game.
            if (capture == null && !starting && !menu.isOpen && !switcher.isOpen && opening == null) onBack(readsScreen = false)
            return START_STICKY
        }
        if (intent?.action == ACTION_DOC_OPENED) {
            val key = intent.getStringExtra(EXTRA_KEY)
            currentGame()?.let { g -> key?.let { prefs.setLastDoc(g, it) } }
            // The viewer's window is up over the menu, which can now show under it.
            main.removeCallbacks(revealLate)
            menu.reveal()
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
            if (projection != null) afterConsent?.invoke()
            afterConsent = null
        }
        getSystemService(NotificationManager::class.java).cancel(BootReceiver.BOOT_ID)
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        running = false
        capture?.stop()
        closeViewer()
        menu.release()
        switcher.close()
        menu.endCapture()
        screenshots.unregister()
        io.shutdownNow()
        pictureReader.shutdownNow()
        presses.shutdownNow()
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
        followSwitcherFront(fg)
        val changed = fg != foreground
        if (changed) {
            foreground = fg
            recentStale = true
            Log.i(TAG, "foreground: $fg (current game: ${currentGame()})")
        }
        // At the end of a game the switcher launched, ES-DE has written the game to its gamelist, and the games are read
        // again at once; until then, after such a game, not at all.
        val ended = switcherGameEnded.also { switcherGameEnded = false }
        if (recentStale && switcherOn(fg) && (!changed || ended) && pick.get()?.let { it.played && !it.ended } != true) {
            // Otherwise a poll after ES-DE came to the front, by when it has written the game just played to its
            // gamelist. While ES-DE's settings lack something every poll reads them again, as the user may be changing
            // them in ES-DE.
            val needs = runCatching {
                contentResolver.openInputStream(treeChild(prefs.esdeTree!!, "settings/es_settings.xml"))!!.use { switcherNeeds(it.reader().readText()) }
            }.onFailure { Log.w(TAG, "switcher: reading ES-DE's settings failed", it) }.getOrNull()
            if (needs != missing) Log.i(TAG, "switcher: ES-DE's settings lack ${needs ?: "(unread)"}")
            missing = needs
            if (needs?.isEmpty() == true) {
                runCatching { loadRecent() }.onFailure { Log.w(TAG, "switcher: reading the recent games failed", it) }
                recentStale = false
            }
        }
        // A switcher launch over a running RetroArch is looked at each quarter second until it's judged.
        pick.get()?.let { if (watchesPick(it, SystemClock.uptimeMillis(), ::runningBefore)) runCatching { io.schedule(Runnable { safePoll() }, 250, TimeUnit.MILLISECONDS) } }
        main.post { onForeground(fg) }
    }

    /** Main thread: when the game is left, close the menu and end a recording; when it's left for ES-DE, end it if set to. */
    private fun onForeground(fg: String?) {
        leave.left(fg, launch?.pkg)?.let(::quitLeft)
        if (fg == launch?.pkg) {
            // The switcher is only ever over ES-DE: one up as a game comes to the front goes.
            if (switching) closeMenu()
            // A game left during a capture with RetroArch's shader off gets it back as it's in front again.
            restoreShader(fg)
            return
        }
        if (switching && fg == ESDE) return
        closeMenu()
        capture?.stop()
    }

    /** Main thread: ends [pkg], left with Home, when that's set and Shizuku is usable; never through App Info. */
    private fun quitLeft(pkg: String) {
        // ES-DE can launch this app and Shizuku as Android apps too; ending either would end what ends games.
        if (pkg == packageName || pkg == ShizukuShell.PACKAGE) return
        if (!prefs.quitOnHome || !ShizukuShell.ready) return
        io.execute {
            launch = null
            Log.i(TAG, if (ShizukuShell.forceStop(pkg)) "left with Home: ended $pkg" else "left with Home: couldn't end $pkg")
        }
    }

    /**
     * Main thread: closes the menu or the switcher and any viewer or browser over the menu, cancels its opening or a
     * Find Guides check or save, and keeps the existing file when it was asking about one.
     */
    private fun closeMenu() {
        switching = false
        opening = null
        menuScreen = null
        finding?.set(true)
        unanswered?.invoke()
        closeViewer()
        menu.release()
        switcher.close()
        showing = null
        stale = emptyMap()
        awaited = null
    }

    /**
     * Main thread: the controller's Back. Stops a recording; with the menu (and any viewer or browser over it) open, or
     * still being looked up, closes all of it so the game resumes; otherwise opens the menu for the game in front, if
     * ES-DE launched one, or the switcher over ES-DE. Does nothing while a capture starts or the switcher steers.
     * With [readsScreen] and [keepsPicture], the screen is read for Quit Game as the menu opens: once the menu is up, a
     * game that pauses draws differently (RetroArch's frame-splitting shaders come back wrong for a moment as it resumes).
     */
    private fun onBack(readsScreen: Boolean = true) {
        capture?.let {
            it.stop()
            return
        }
        if (starting || steering) return
        if (menu.isOpen || switcher.isOpen || opening != null) {
            closeMenu()
            return
        }
        val fg = front.read(this)
        val g = currentGame(fg) ?: run {
            if (switcherReady(fg)) openSwitcher()
            return
        }
        // ES-DE's name for the game, which Find Guides looks up.
        val name = launch?.name ?: return
        val token = Any().also { opening = it }
        val started = SystemClock.uptimeMillis()
        val retroarch = hotkeys?.takeIf { it.pkg == fg }
        // RetroArch with its shader on, which masks and darkens frames: the screen is read with it off.
        val unshade = retroarch?.takeIf { it.togglesShader && !shaderOff }
        val screen = when {
            !readsScreen || !keepsPicture() -> null
            // RetroArch's own state picture, with no shader, is the game's when it saves one as it exits.
            retroarch?.savesOnExit == true -> null.also { Log.i(TAG, "menu: no screen read, RetroArch saves its state on exit") }
            unshade != null -> FutureTask { shaderFreeScreen(unshade) }.also(presses::execute)
            else -> FutureTask(::readScreen).also { thread(name = "menu screen", block = it::run) }
        }
        menuScreen = screen
        io.execute {
            val (manual, guides, info) = runCatching { documents(g) }
                .onFailure { Log.w(TAG, "looking up manual and guides failed", it) }
                .getOrDefault(Triple(null, emptyList(), null))
            // The menu waits for the screen to be read, so it's never in the picture, and for RetroArch's shader to be on
            // again, as the menu takes the keys.
            runCatching { screen?.get(if (unshade != null) 5 else 2, TimeUnit.SECONDS) }
            val art = runCatching { screenshot(g) }.getOrNull()
            // The look read when ES-DE launched the game: its theme only changes in ES-DE, before a launch, and reading
            // it here would add to the menu's opening.
            val theme = themeLook.warmed?.also { Log.i(TAG, "look: kept from the launch") }
                ?: runCatching { themeLook.current(g.system, prefs.menuImage) }
                    .onFailure { Log.w(TAG, "reading the theme failed", it) }
                    .getOrDefault(ThemeLook.DEFAULT)
            val inFront = gameInFront() == g
            main.post {
                // The game may have been left, or Back pressed again, while the lookup ran.
                if (!inFront || opening !== token) {
                    art?.recycle()
                    return@post
                }
                opening = null
                menu.title = name
                menu.art = art
                menu.cropArt = prefs.menuImageCrops
                menu.theme = theme
                // The guide or manual last opened for the game, if set to and it's still there, over the list it opens
                // from, so B goes back as if it had been picked there.
                val last = prefs.lastDoc(g)?.takeIf { prefs.reopenLastDoc }
                val guide = guides.indexOfFirst { it.key == last }
                val reopened = guides.getOrNull(guide) ?: manual?.takeIf { it.key == last }
                if (reopened != null) {
                    // The list stays invisible, showing the game, until the viewer says it's up; or, if it never
                    // does, after a wait several times its usual start.
                    menu.hideUntilRevealed()
                    main.removeCallbacks(revealLate)
                    main.postDelayed(revealLate, 2_000)
                }
                when {
                    // Guides is the row after Resume.
                    guide >= 0 -> guidesList(g, name, guides, { openMenu(g, name, manual, guides, info, start = 1) }, guide)
                    // Manual is the row after Guides or Find Guides, which a game with a manual always has.
                    reopened != null -> openMenu(g, name, manual, guides, info, start = 2)
                    else -> openMenu(g, name, manual, guides, info)
                }
                reopened?.let(::view)
                Log.i(TAG, "menu: open in ${SystemClock.uptimeMillis() - started} ms" + (reopened?.let { ", reopening ${it.name}" } ?: ""))
            }
        }
    }

    /** Whether the switcher is turned on over [fg]: it's ES-DE, the switch is on, its folders are set and Shizuku can send it presses. */
    private fun switcherOn(fg: String?) = fg == ESDE && prefs.switcher && prefs.esdeTree != null && prefs.romsTree != null && ShizukuShell.ready

    /** Whether the switcher can open over [fg]: [switcherOn], and ES-DE's settings lacked nothing when last read. */
    private fun switcherReady(fg: String?) = switcherOn(fg) && missing?.isEmpty() == true

    /**
     * io thread: reads ES-DE's Last Played list from its gamelists, searched in one go through Shizuku (parsing them
     * all takes seconds), the look to show it in, and where RetroArch saved its state pictures, into [recent] and
     * [states]; then the first games' pictures, which a game just played may have changed.
     */
    private fun loadRecent() {
        val started = SystemClock.uptimeMillis()
        // How long each part took, for the log.
        val took = mutableListOf<String>()
        var mark = started
        fun took(part: String) {
            val now = SystemClock.uptimeMillis()
            took += "$part ${now - mark}"
            mark = now
        }
        val gamelists = romRoots(prefs.esdeTree!!).first() + "/gamelists"
        val tags = RECENT_TAGS.joinToString(" ") { "-e '<$it>'" }
        val lines = ShizukuShell.output("cd ${ShizukuShell.quote(gamelists)} && grep -H -F $tags */gamelist.xml")?.decodeToString().orEmpty()
        took("search")
        val played = recentGames(lines.lineSequence(), max = Int.MAX_VALUE)
        took("parse")
        // ES-DE leaves out a game whose file is gone, though its gamelist keeps the entry; unchecked if the check fails.
        val present = played.takeIf { it.isNotEmpty() }?.let {
            ShizukuShell.output(
                "cd ${ShizukuShell.quote(romRoots(prefs.romsTree!!).first())} && for f in " +
                    it.joinToString(" ") { g -> ShizukuShell.quote("${g.system}/${g.path}") } +
                    "; do [ -e \"${'$'}f\" ] && echo \"${'$'}f\"; done; true",
            )?.decodeToString()?.lines()?.toSet()
        }
        took("files")
        val kept = played.filter { present == null || "${it.system}/${it.path}" in present }
        val games = kept.take(50)
        // Warmed, so a downloaded theme chosen since the last launch is copied first.
        val theme = runCatching { games.firstOrNull()?.system.orEmpty().let { themeLook.warm(it, prefs.menuImage); themeLook.current(it, prefs.menuImage) } }
            .onFailure { Log.w(TAG, "reading the theme failed", it) }
            .getOrDefault(ThemeLook.DEFAULT)
        took("theme")
        // `autoload` if RetroArch's Auto Load State is on, then `<modified> <path>` for every picture it saved beside a
        // state, below the folder its config names.
        val retroarch = ShizukuShell.output(
            """for c in /sdcard/Android/data/com.retroarch*/files/retroarch.cfg; do grep -q '^savestate_auto_load = "true"' "${'$'}c" && echo autoload; d=$(sed -n 's/^savestate_directory = "\(.*\)"$/\1/p' "${'$'}c"); [ -d "${'$'}d" ] && find "${'$'}d" -name '*.state*.png' -exec stat -c '%Y %n' {} +; done; true""",
        )?.decodeToString().orEmpty().lines()
        took("states")
        retroarchResumes = "autoload" in retroarch
        states = retroarch.mapNotNull { line -> line.substringBefore(' ').toLongOrNull()?.let { it to line.substringAfter(' ') } }
        discSession?.let { (g, emulator, since) ->
            discSession = null
            runCatching { keepSessionPicture(g, emulator, since) }.onFailure { Log.w(TAG, "switcher: reading $emulator's save states failed", it) }
        }
        // Read only once PPSSPP has saved a picture here, as finding its folders takes a while.
        if (File(filesDir, "ppsspp").exists()) {
            ppssppResumes = ppssppStates.isNotEmpty() && ShizukuShell.output(
                "cat " + ppssppStates.joinToString(" ") { ShizukuShell.quote("$it/../SYSTEM/ppsspp.ini") } + " 2>/dev/null | sed -n 's/^AutoLoadSaveState = //p'; true",
            )?.decodeToString()?.lines()?.any { it.isNotBlank() && it.trim() != "0" } == true
        }
        took("other")
        // An open switcher keeps showing the pictures it has until each is read anew.
        stale = if (showing != null) pictures.snapshot() else emptyMap()
        pictureRound++
        pictures.evictAll()
        recent = games to theme
        Log.i(TAG, "switcher: ${games.size} recent games (${played.size - kept.size} files gone), ${states.size} state pictures, read in ${SystemClock.uptimeMillis() - started} ms (${took.joinToString()})")
        if (games.isNotEmpty()) main.post { showing?.let { wantPictures(it, shownGame) } ?: wantPictures(games, 0) }
    }

    /** Where the picture kept of a game when it's quit goes in the app's own files; null for a path that would leave them. */
    private fun keptPicture(system: String, path: String) = keptPicturePath(system, path)?.let { File(filesDir, "kept/$it") }

    /**
     * Where a copy of a game's save-state picture from [emulator] (`ppsspp` or `armsx2`, which name their states by the
     * disc) goes; null as for [keptPicture]. BitmapFactory reads ARMSX2's PNG whatever the name says.
     */
    private fun copiedPicture(emulator: String, system: String, path: String) = keptPicturePath(system, path)?.let { File(filesDir, "$emulator/$it") }

    /** Set while [findDiscStates] runs. */
    private val findingStates = AtomicBoolean()

    /**
     * Its own thread: finds where PPSSPP and ARMSX2 keep their save states, into [Prefs.discStates]: the
     * `PSP/PPSSPP_STATE` folders of PPSSPP's memory stick, and ARMSX2's settings files (`PCSX2-Android.ini`, which name
     * its folder). Either may be anywhere in shared storage, and walking it takes seconds (7 on the Nova, half of it in
     * RetroArch's thumbnails, which are skipped), so it runs when one of them starts a game ES-DE launched, which lasts
     * longer.
     */
    private fun findDiscStates() {
        if (!findingStates.compareAndSet(false, true)) return
        thread(name = "state folders") {
            try {
                val started = SystemClock.uptimeMillis()
                val found = ShizukuShell.output(
                    "find /storage/emulated/0 /storage/*-* -maxdepth 4 -name RetroArch -prune -o \\( -type d -name PPSSPP_STATE -o -type f -name PCSX2-Android.ini \\) -print 2>/dev/null; " +
                        "ls -d /storage/emulated/0/Android/data/org.ppsspp.*/files/PSP/PPSSPP_STATE /storage/*-*/Android/data/org.ppsspp.*/files/PSP/PPSSPP_STATE " +
                        "/storage/emulated/0/Android/data/com.armsx2/files/PCSX2-Android.ini /storage/*-*/Android/data/com.armsx2/files/PCSX2-Android.ini 2>/dev/null; true",
                )?.decodeToString()?.lines()?.filter { it.isNotBlank() }?.toSet() ?: return@thread
                prefs.discStates = found
                Log.i(TAG, "switcher: save-state folders $found, found in ${SystemClock.uptimeMillis() - started} ms")
            } finally {
                findingStates.set(false)
            }
        }
    }

    private val ppssppStates get() = prefs.discStates.filterNot { it.endsWith(".ini") }

    /** ARMSX2's save-state folders, as its settings' `Savestates = sstates` names them, relative to the settings' folder unless absolute. */
    private fun armsx2States() = prefs.discStates.filter { it.endsWith(".ini") }.mapNotNull { ini ->
        val dir = ShizukuShell.output("sed -n 's/^Savestates = //p' ${ShizukuShell.quote(ini)}")?.decodeToString()?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        if (dir.startsWith('/')) dir else ini.substringBeforeLast('/') + "/" + dir
    }

    /**
     * io thread: copies the picture [emulator] (`ppsspp` or `armsx2`) saved last with a state during [g]'s session, from
     * [since] until now, as [g]'s, keeping its time. Both name states by the disc (`ULUS10466_1.00_0.jpg`,
     * `SLUS-20071 (23AF6876).00.p2s`), so the time is what ties one to the game. PPSSPP writes a JPEG beside the state;
     * ARMSX2 packs a PNG in the state, a zip, as `Screenshot.png`.
     */
    private fun keepSessionPicture(g: GameRef, emulator: String, since: Long) {
        val armsx2 = emulator == "armsx2"
        val files = if (armsx2) armsx2States().map { ShizukuShell.quote(it) + "/*.p2s" } else ppssppStates.map { ShizukuShell.quote(it) + "/*.jpg" }
        if (files.isEmpty()) return
        val written = ShizukuShell.output("stat -c '%Y %n' ${files.joinToString(" ")} 2>/dev/null; true")
            ?.decodeToString().orEmpty().lines().mapNotNull { line -> line.substringBefore(' ').toLongOrNull()?.let { it to line.substringAfter(' ') } }
        val (at, path) = sessionPicture(written, since, System.currentTimeMillis() / 1000) ?: return
        val file = copiedPicture(emulator, g.system, g.romRelativePath) ?: return
        val bytes = ShizukuShell.output(if (armsx2) "unzip -p ${ShizukuShell.quote(path)} Screenshot.png" else "cat ${ShizukuShell.quote(path)}")?.takeIf { it.isNotEmpty() } ?: return
        file.parentFile!!.mkdirs()
        val part = File(file.path + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(file) || !file.setLastModified(at * 1000)) error("couldn't store ${file.name}")
        Log.i(TAG, "switcher: $emulator's ${path.substringAfterLast('/')} kept for ${g.romRelativePath}")
    }

    /**
     * Which picture [g] shows, and its file: the picture of the save state its emulator starts it from, if it's set to
     * (RetroArch's auto state, or PPSSPP's copied picture); else the newer of its save-state picture (RetroArch's, or a
     * copy of PPSSPP's or ARMSX2's) and the one kept when it was quit; else the scraped image, whose file [screenshot]
     * finds.
     */
    private fun pictureOf(g: RecentGame): Pair<PictureSource, String?> {
        val zone = ZoneId.systemDefault()
        // RetroArch's Auto Load State loads the auto state; a numbered slot only when the player picks it.
        val auto = statePicture(states.filter { ".state.auto." in it.second.substringAfterLast('/') }, g, zone)?.takeIf { retroarchResumes }
        fun copied(emulator: String) = copiedPicture(emulator, g.system, g.path)?.let { it.lastModified() / 1000 to it.path }?.takeIf { it.first > 0 }
        val ppsspp = copied("ppsspp")
        // ponytail: ARMSX2 counts as never starting from a state (the Nova's has SaveStateOnShutdown off); read its settings if one turns up that does.
        val armsx2 = copied("armsx2")
        val (saved, resumes) = when {
            auto != null -> auto to true
            // ponytail: any Auto Load Savestate but Off counts as resuming from the newest picture; its Oldest and slot choices would need the slot read too.
            ppsspp != null && ppssppResumes -> ppsspp to true
            else -> listOfNotNull(statePicture(states, g, zone), ppsspp, armsx2).maxByOrNull { it.first } to false
        }
        val kept = keptPicture(g.system, g.path)
        return when (pictureSource(saved?.first, kept?.lastModified()?.takeIf { it > 0 }?.div(1000), resumes)) {
            PictureSource.SAVED -> PictureSource.SAVED to saved!!.second
            PictureSource.LEFT -> PictureSource.LEFT to kept!!.path
            PictureSource.SCRAPED -> PictureSource.SCRAPED to null
        }
    }

    /**
     * Picture thread: [g]'s picture for the switcher, as [pictureOf] chooses, else the game's scraped image; null when
     * it has none. Black bars baked into it are trimmed off.
     */
    private fun switcherPicture(g: RecentGame): Bitmap? = runCatching {
        val picture = pictureOf(g).second?.let { path ->
            BitmapFactory.decodeFile(path) ?: ShizukuShell.output("cat ${ShizukuShell.quote(path)}")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } ?: screenshot(GameRef(g.system, g.path)) ?: return@runCatching null
        val started = SystemClock.uptimeMillis()
        val (left, top, right, bottom) = contentBounds(picture.width, picture.height) { x, y ->
            picture.getPixel(x, y).let { Color.red(it) < 24 && Color.green(it) < 24 && Color.blue(it) < 24 }
        }
        val trimmed = if (left == 0 && top == 0 && right == picture.width && bottom == picture.height) picture
        else Bitmap.createBitmap(picture, left, top, right - left, bottom - top).also {
            Log.i(TAG, "switcher: bars trimmed off ${g.name}, ${picture.width}×${picture.height} to ${it.width}×${it.height}, in ${SystemClock.uptimeMillis() - started} ms")
        }
        val (width, height) = displaySize(trimmed.width, trimmed.height)
        if (height == trimmed.height) trimmed else trimmed.scale(width, height).also {
            Log.i(TAG, "switcher: ${g.name}'s ${trimmed.width}×${trimmed.height} picture shown at ${it.width}×${it.height}")
        }
    }.onFailure { Log.w(TAG, "switcher: no picture for ${g.name}", it) }.getOrNull()

    /**
     * Main thread: has the pictures of game [at] of [games] and of the two either side, or as many as the strip
     * shows, read, nearest first, unless they're in or on their way; the switcher takes each as it arrives. A read
     * whose game the switcher has moved away from by then is skipped.
     */
    private fun wantPictures(games: List<RecentGame>, at: Int) {
        shownGame = at
        val reach = maxOf(2, switcher.neighbours)
        for (offset in (0..reach).flatMap { listOf(it, -it) }.distinct()) {
            val i = Math.floorMod(at + offset, games.size)
            val key = games[i].logSuffix
            if (pictures.get(key) != null || !reading.add(key)) continue
            val round = pictureRound
            pictureReader.execute {
                try {
                    val away = Math.floorMod(i - shownGame, games.size)
                    if (away > reach && away < games.size - reach) return@execute
                    val read = switcherPicture(games[i])
                    if (round != pictureRound) {
                        // Read before the games were read again: dropped, and asked for again.
                        main.post { showing?.let { wantPictures(it, shownGame) } }
                        return@execute
                    }
                    if (read != null) pictures.put(key, read) else stale = stale - key
                    if (awaited?.let { (k, after) -> k == key && round > after } == true) awaited = null
                    main.post { switcher.pictureArrived(i) }
                } finally {
                    reading.remove(key)
                }
            }
        }
    }

    /**
     * Main thread: shows the recent games over ES-DE at once, as last read; picking one has ES-DE launch it ([steer]).
     * Before any read (the service has just started) it reads them first. Back from a game, the list was read before it
     * was played and is being read again, past [pictureRound] [rereadFrom].
     */
    private fun openSwitcher(rereadFrom: Int? = null) {
        val started = SystemClock.uptimeMillis()
        val (read, theme) = recent ?: run {
            val token = Any().also { opening = it }
            io.execute {
                runCatching { loadRecent() }.onFailure { Log.w(TAG, "switcher: reading the recent games failed", it) }
                recentStale = false
                main.post {
                    if (opening !== token) return@post
                    opening = null
                    if (recent != null) openSwitcher(rereadFrom)
                }
            }
            return
        }
        val games = game?.let { playedFirst(read, it.system, it.romRelativePath, LocalDateTime.now(), justPlayed = rereadFrom != null) } ?: read
        // The newest as many as set; steering gets them all, which tells Last Played from other lists as well as ever.
        val shown = games.take(prefs.switcherGames)
        // Steering knows Last Played by two neighbours, so a list of one can't be steered to.
        if (shown.size < 2) return toast("ES-DE has fewer than two recently played games")
        switching = true
        showing = shown
        // Back from a game, its saved or kept picture is shown until the one it has now is read after the reread.
        awaited = rereadFrom?.let { round -> (shown[0].logSuffix to round).takeIf { pictureOf(shown[0]).first != PictureSource.SCRAPED } }
        val names = prefs.systemNames()
        val today = LocalDate.now()
        switcher.show(
            theme, prefs.switcherStrip, shown.map { it.name }, { i: Int -> detailLine(shown[i], names, today, pictureOf(shown[i]).first) }.takeIf { prefs.switcherDetails }, 0,
            picture = { pictures.get(shown[it].logSuffix) ?: stale[shown[it].logSuffix] },
            updating = { shown[it].logSuffix == awaited?.first },
            onSelect = { wantPictures(shown, it) },
            onPick = { steer(games, it, theme.look) },
            onClose = ::closeMenu,
        )
        Log.i(TAG, "switcher: open in ${SystemClock.uptimeMillis() - started} ms")
    }

    /**
     * Main thread: opens the switcher again over ES-DE, back from a game it launched, unless something else of the
     * overlay's is up or the switcher can't open now; the games are being read again past [rereadFrom].
     */
    private fun reopenSwitcher(rereadFrom: Int) {
        if (capture != null || starting || steering || menu.isOpen || switcher.isOpen || opening != null || !switcherReady(front.read(this))) return
        Log.i(TAG, "switcher: opening again after its game")
        openSwitcher(rereadFrom)
    }

    /**
     * Main thread: keeps the switcher up, showing the picked game, while ES-DE is walked to it and launches it. The
     * launch is then followed through ES-DE's log ([readLog]).
     */
    private fun steer(games: List<RecentGame>, target: Int, look: Look) {
        if (steering) return
        steering = true
        val started = SystemClock.uptimeMillis()
        // A launch in a RetroArch that's already running can fail, and is then pressed again.
        runningAtPick = FutureTask { retroarchRunning().orEmpty() }.also { thread(name = "switcher processes", block = it::run) }
        val mine = Pick(started).also(pick::set)
        switcher.yieldFocus {
            val yielded = SystemClock.uptimeMillis() - started
            Thread {
                val launched = runCatching { steerEsde(games.map { it.logSuffix }, target, look) }
                    .onFailure { Log.w(TAG, "switcher: steering failed", it) }
                    .getOrDefault(false)
                Log.i(TAG, "switcher: ${games[target].name} ${if (launched) "launching" else "not reached"} ${SystemClock.uptimeMillis() - started} ms after the pick (keys given up in $yielded ms)")
                if (!launched) pick.compareAndSet(mine, null)
                main.post {
                    steering = false
                    closeMenu()
                    if (!launched) toast("Couldn't reach ${games[target].name} in ES-DE")
                }
            }.start()
        }
    }

    /**
     * Its own thread: presses ES-DE's way to row [target] of its Last Played list ([recents], as ROM path endings)
     * with keys sent through Shizuku, each press waiting for the selection ES-DE logs, then has ES-DE launch it.
     * The keys are those ES-DE moves with in [look]'s layouts under its Quick System Select setting.
     * True once ES-DE logs that press. Pressing blind doesn't work: ES-DE drops presses sent faster than it logs them.
     */
    private fun steerEsde(recents: List<String>, target: Int, look: Look): Boolean {
        val tree = prefs.esdeTree!!
        val quickSystemSelect = contentResolver.openInputStream(treeChild(tree, "settings/es_settings.xml"))
            ?.use { esSetting(it.reader().readText(), "QuickSystemSelect") }
        val keys = steerKeys(look.gamelistLayout, look.systemLayout, quickSystemSelect)
        val pfd = contentResolver.openFileDescriptor(treeChild(tree, "logs/es_log.txt"), "r")!!
        ParcelFileDescriptor.AutoCloseInputStream(pfd).channel.use { log ->
            ShizukuShell.Session().use { shell ->
                var offset = log.size()
                // The view ES-DE shows, by the last selection it logged in the log's tail.
                val tail = ByteBuffer.allocate(minOf(offset, 65_536L).toInt())
                log.read(tail, offset - tail.capacity())
                val start = tail.array().decodeToString(0, tail.position()).lineSequence().mapNotNull(::selection).lastOrNull()
                var partial = ""
                // The whole lines ES-DE has logged since the last read.
                fun lines(): List<String> {
                    val size = log.size()
                    if (size <= offset) return emptyList()
                    val buffer = ByteBuffer.allocate((size - offset).toInt())
                    val read = log.read(buffer, offset).coerceAtLeast(0)
                    offset += read
                    val lines = (partial + buffer.array().decodeToString(0, read)).split('\n')
                    partial = lines.last()
                    return lines.dropLast(1)
                }
                // What [wanted] picks from the last line ES-DE logs within [ms] that it picks anything from.
                fun <T : Any> await(ms: Long, wanted: (String) -> T?): T? {
                    val until = SystemClock.uptimeMillis() + ms
                    while (SystemClock.uptimeMillis() < until) {
                        lines().mapNotNull(wanted).lastOrNull()?.let { return it }
                        Thread.sleep(4)
                    }
                    return null
                }
                var presses = 0
                var selected = start
                // A press ES-DE doesn't log is sent again: the first can arrive before ES-DE has the keys back. A in
                // the system view isn't: sent twice, the second could launch the game the opened list is on.
                fun press(step: Step): Selection? {
                    // What ES-DE logged before this press is passed over.
                    lines()
                    repeat(if (step == Step.OPEN) 1 else 3) {
                        presses++
                        shell.send("input keyevent ${keys.key(step)}")
                        // ES-DE logs a key before what it selects for it, so only a selection after the key's own line
                        // counts. One before it is a nudged D-pad's: taken for this press's, every later press would
                        // be taken for the one before it.
                        var keyed = false
                        val logged = { line: String ->
                            if ("logInput(Keyboard)" in line && "value=1" in line) keyed = true
                            val found = selection(line)
                            if (found != null && !keyed) Log.i(TAG, "switcher: nudged to $found")
                            found?.takeIf { keyed }
                        }
                        await(if (step == Step.OPEN) 1_000 else 300, logged)?.let { found ->
                            selected = found
                            return found
                        }
                    }
                    Log.i(TAG, "switcher: ES-DE logged nothing for $step at $selected ($presses sent)")
                    return null
                }
                // Back by the previous-system key, or in the system view when there's no next-system key either.
                val ring = prefs.systemRing
                val walk = Steering(
                    recents,
                    target,
                    canChangeSystem = keys.nextSystem != null,
                    canGoBack = keys.previousSystem != null || keys.nextSystem == null,
                    ring = ring,
                )
                val launched = steer(
                    walk,
                    start,
                    ::press,
                    // About two of ES-DE's frames for a nudged D-pad's selection, which steering then walks back from.
                    settle = { await(40, ::selection)?.also { selected = it; Log.i(TAG, "switcher: nudged to $it") } },
                    launch = {
                        Log.i(TAG, "switcher: on $selected after $presses presses")
                        // ES-DE notes the key at once, then shows its own launch screen for a few seconds before it
                        // logs the launch itself.
                        shell.send("input keyevent ${KeyEvent.KEYCODE_ENTER}")
                        await(1_000) { line -> line.takeIf { "isMappedTo=a," in it || "Launching game" in it } } != null
                    },
                )
                if (!launched) Log.i(TAG, "switcher: lost at $selected after $presses presses (started at $start, keys $keys)")
                walk.learned?.takeIf { it != ring }?.let {
                    prefs.systemRing = it
                    Log.i(TAG, "switcher: learned ${it.ahead} with ${it.size ?: "no"} ring size")
                }
                return launched
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
                "Quit Game" -> if (prefs.confirmQuit) {
                    // The cursor starts on the safe row: A pressed out of habit keeps the game.
                    menu.show(listOf("Keep Playing", "Quit \"$name\""), { if (it == 1) quitGame(g) else back() }, back)
                } else quitGame(g)
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
        menu.show(listOf("Keep", "Delete \"${doc.name}\""), { i ->
            if (i == 0) {
                keep()
                return@show
            }
            io.execute {
                val deleted = runCatching { deleteGuide(g, doc) }.onFailure { Log.w(TAG, "deleting ${doc.key} failed", it) }
                main.post {
                    if (deleted.getOrDefault(false)) relist(g, name, at) else if (menu.isOpen) menu.show(listOf("Couldn't delete ${doc.name}"), {}, keep)
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

    /**
     * Main thread: ends the game and goes to ES-DE. RetroArch, with Exit on the controller, quits through its own Exit,
     * saving as it's set to; any other app is force-stopped, with no chance to save. The screen read as the menu
     * opened, if it was, is then kept as [g]'s picture.
     */
    private fun quitGame(g: GameRef) {
        val pkg = launch?.pkg ?: return
        leave.forget()
        io.execute { launch = null }
        val retroarch = hotkeys?.takeIf { it.pkg == pkg && it.exits && ShizukuShell.ready }
        val screen = menuScreen
        Log.i(TAG, "quit: " + (if (retroarch != null) "exiting $pkg through RetroArch" else "ending $pkg") +
            if (screen != null) ", keeping the picture read as the menu opened" else "")
        if (retroarch != null) {
            exitRetroArch(retroarch)
        } else {
            ButtonService.forceStop(pkg)
            pollSoon()
        }
        if (screen == null) return
        val file = keptPicture(g.system, g.romRelativePath) ?: return
        thread(name = "quit picture") { screen.get()?.let { keepPicture(g, file, it) } }
    }

    /**
     * Main thread: closes the menu, which has the keys, then presses RetroArch's Exit, its shader back on first if the
     * overlay turned it off (RetroArch saves its settings as it quits). Force-stops RetroArch if it's still in front a
     * while later.
     */
    private fun exitRetroArch(retroarch: RetroArchHotkeys) {
        closeMenu()
        presses.execute {
            Thread.sleep(RetroArchHotkeys.KEYS_BACK_MS)
            if (shaderOff && retroarch.toggleShader()) shaderOff = false
            val started = SystemClock.uptimeMillis()
            val pressed = retroarch.pressExit()
            while (pressed && SystemClock.uptimeMillis() - started < RetroArchHotkeys.EXIT_MS && front.read(this) == retroarch.pkg) {
                Thread.sleep(100)
            }
            val waited = SystemClock.uptimeMillis() - started
            if (front.read(this) != retroarch.pkg) {
                Log.i(TAG, "quit: RetroArch exited $waited ms after Exit")
                pollSoon()
                return@execute
            }
            Log.w(TAG, "quit: RetroArch still in front $waited ms after Exit (pressed: $pressed), force-stopping it")
            ButtonService.forceStop(retroarch.pkg)
        }
    }

    /**
     * Main thread: turns RetroArch's shader back on, if the overlay turned it off and RetroArch is in front ([fg]) with
     * nothing of the overlay's taking the keys, then runs [then]. Otherwise runs [then] at once, leaving the shader for
     * when RetroArch is in front again, or for Quit Game.
     */
    private fun restoreShader(fg: String? = front.read(this), then: () -> Unit = {}) {
        val retroarch = hotkeys
        if (!shaderOff || retroarch == null || fg != retroarch.pkg || capture != null || starting || menu.isOpen || switcher.isOpen || opening != null) {
            return then()
        }
        shaderOff = false
        presses.execute {
            if (!retroarch.toggleShader()) shaderOff = true
            then()
        }
    }

    /**
     * Off the main thread: the screen read with RetroArch's shader turned off, and on again after; null without a read
     * when the shader couldn't be turned off.
     */
    private fun shaderFreeScreen(retroarch: RetroArchHotkeys): Pair<ByteBuffer, Int>? {
        if (!retroarch.toggleShader()) return null
        Thread.sleep(RetroArchHotkeys.CLEAN_MS)
        val screen = readScreen()
        // Left for the next chance if it couldn't be turned on.
        shaderOff = !retroarch.toggleShader()
        return screen
    }

    /** Whether a game ended through the overlay has its picture kept: the setting is on and Shizuku can read the screen. */
    private fun keepsPicture() = prefs.keepQuitPicture && ShizukuShell.ready

    /**
     * Any thread: the screen as `screencap` reads it through Shizuku, raw (a PNG takes longer to make): a 12- or 16-byte
     * header (width, height and format as little-endian ints), then RGBA rows, which start at the second value; null
     * when it can't be read.
     */
    private fun readScreen(): Pair<ByteBuffer, Int>? = runCatching {
        val started = SystemClock.uptimeMillis()
        val raw = ShizukuShell.output("screencap")?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) } ?: return null
        val pixels = raw.getInt(0) * raw.getInt(4) * 4
        // PixelFormat.RGBA_8888.
        if (raw.getInt(8) != 1 || raw.capacity() - pixels !in 12..16) error("screencap gave format ${raw.getInt(8)}, ${raw.capacity()} bytes")
        Log.i(TAG, "screen read in ${SystemClock.uptimeMillis() - started} ms")
        raw to raw.capacity() - pixels
    }.onFailure { Log.w(TAG, "reading the screen failed", it) }.getOrNull()

    /** Its own thread: keeps [screen], as [readScreen] gave it, as [g]'s picture for the switcher in [file]. */
    private fun keepPicture(g: GameRef, file: File, screen: Pair<ByteBuffer, Int>) {
        val started = SystemClock.uptimeMillis()
        val (raw, start) = screen
        runCatching {
            // The mean of red, green and blue over a spread of pixels, 0 to 255, for telling a dark picture in the log.
            val samples = (start until raw.capacity() step 4 * 97)
            val mean = samples.sumOf { i -> (0..2).sumOf { raw.get(i + it).toLong() and 255 } } / (3L * samples.count())
            val picture = createBitmap(raw.getInt(0), raw.getInt(4))
            picture.copyPixelsFromBuffer(raw.position(start))
            file.parentFile!!.mkdirs()
            val part = File(file.path + ".part")
            part.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            picture.recycle()
            if (!part.renameTo(file)) error("couldn't rename ${part.name}")
            pictures.remove("/${g.system}/${g.romRelativePath}")
            Log.i(TAG, "picture of ${g.romRelativePath} kept in ${SystemClock.uptimeMillis() - started} ms (mean $mean)")
        }.onFailure { Log.w(TAG, "keeping the picture of ${g.romRelativePath} failed", it) }
    }

    /**
     * Main thread: the controller's Home, as it goes down: keeps a picture of the game in front for the switcher, if
     * leaving it with Home ends it and [keepsPicture], unless the menu or switcher is over it.
     */
    private fun onHomeDown() {
        pollSoon()
        if (!prefs.quitOnHome || !keepsPicture() || menu.isOpen || switcher.isOpen || opening != null) return
        val g = gameInFront() ?: return
        val file = keptPicture(g.system, g.romRelativePath) ?: return
        Log.i(TAG, "home: keeping a picture of ${g.romRelativePath}")
        thread(name = "home picture") { readScreen()?.let { keepPicture(g, file, it) } }
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
     * The sites only a browser gets into, which open over this menu at the game's search, or ZeldaDungeon's walkthrough,
     * then a web search for guides elsewhere. Closing the browser shows the menu again; a guide saved there opens in the
     * viewer.
     */
    private fun browserRows(g: GameRef, name: String): List<Pair<String, () -> Unit>> {
        fun browse(url: String) = { startService(ViewerService.browse(this, url, g)); Unit }
        return GuideSite.entries.mapNotNull { site ->
            startUrl(site, name)?.let { url -> (if (site == GuideSite.ZELDADUNGEON) site.label else "Search ${site.label}") to browse(url) }
        } + ("Search the Web" to browse(webSearchUrl(name)))
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
     * Off the main thread: the game's image for the menu's art box, of the Menu image type (PNG, else JPG, as ES-DE looks), or
     * null when it has none.
     * One larger than the box both ways, like a full-screen capture, is decoded at half size.
     */
    private fun screenshot(g: GameRef): Bitmap? {
        val tree = prefs.esdeTree ?: return null
        val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
        for (ext in listOf("png", "jpg")) {
            val uri = treeChild(tree, (listOf("downloaded_media") + mediaPath(g, prefs.menuImage, ext, dir)).joinToString("/"))
            fun decode(options: BitmapFactory.Options) =
                contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Opening a missing file throws.
            if (runCatching { decode(size) }.isFailure) continue
            return decode(BitmapFactory.Options().apply { if (size.outWidth > menu.artW && size.outHeight > menu.artH) inSampleSize = 2 })
        }
        return null
    }

    /**
     * Captures the game and saves the screenshot when [keepShot] and the clip when [keepVideo]. Without consent yet,
     * opens Android's prompt and captures nothing: an emulator can stay paused behind the prompt (Eden waits for a tap
     * on its play button), which would record a still frame with no sound, so once it's granted Capture is picked again.
     */
    private fun startCapture(g: GameRef, keepShot: Boolean, keepVideo: Boolean) {
        val p = projection ?: run {
            afterConsent = {
                toast("Capture allowed: pick Capture again")
                notify(this, RESULT_ID, "Capture allowed. Pick Capture again to capture the game.")
            }
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_GRANT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        menu.beginCapture()
        starting = true
        val begin = Runnable {
            starting = false
            try {
                // A screenshot alone stops as soon as its frame is taken, too soon for a usable clip.
                val onRecording = { if (!keepVideo) capture?.stop() }
                // Only the game's app is recorded, never everything playing; without its app the clip has no sound.
                val gameUid = launch?.pkg?.takeIf { audioGranted() }?.let { runCatching { packageManager.getPackageUid(it, 0) }.getOrNull() }
                noGameSound = audioGranted() && gameUid == null
                if (noGameSound) Log.w(TAG, "no sound: no app found for ${launch?.pkg}")
                capture = Capture(windowContext, p, prefs.clipSeconds, gameUid, menu::nudge, onRecording) { png, mp4 ->
                    capture = null
                    menu.endCapture()
                    // RetroArch's shader goes back on before a menu asking about a file there can take the keys.
                    restoreShader { io.execute { save(g, png?.takeIf { keepShot }, mp4?.takeIf { keepVideo }) } }
                }.also { it.start() }
                Log.i(TAG, "capturing $g, sound from uid $gameUid")
            } catch (e: Exception) {
                Log.e(TAG, "capture failed to start", e)
                menu.endCapture()
                restoreShader()
                toast("Capture failed: ${e.message}")
                notify(this, RESULT_ID, "Capture failed: ${e.message}")
            }
        }
        val retroarch = hotkeys?.takeIf { it.pkg == launch?.pkg && it.togglesShader && !shaderOff && ShizukuShell.ready }
        if (retroarch == null) {
            // Let the closed menu leave the screen before the first frame.
            main.postDelayed(begin, 100)
            return
        }
        // RetroArch's shader masks and darkens frames: it's turned off once RetroArch has the keys back from the menu.
        presses.execute {
            Thread.sleep(RetroArchHotkeys.KEYS_BACK_MS)
            if (retroarch.toggleShader()) {
                shaderOff = true
                Thread.sleep(RetroArchHotkeys.CLEAN_MS)
            }
            main.post(begin)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /**
     * A system screenshot: when that's turned on, file it like a captured frame, or leave it where it is when no game
     * is in front. The setting is read here, so turning it off takes effect at once.
     */
    private fun onScreenshot(uri: Uri, mime: String) {
        if (!prefs.systemScreenshots) return
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
            }.onSuccess {
                // No menu is open to ask in, so Ask keeps the file already there.
                save(g, png, null, prefs.overwrite.takeIf { it != Overwrite.ASK } ?: Overwrite.SKIP)
            }.onFailure {
                Log.e(TAG, "reading screenshot failed", it)
                notify(this, RESULT_ID, "Couldn't read screenshot: ${it.message}")
            }
        }
    }

    /**
     * io thread: file the capture under the game's ES-DE media folders under [policy]. Under Ask, files that would
     * replace one wait for the answer to [ask].
     */
    private fun save(g: GameRef, png: File?, mp4: File?, policy: Overwrite = prefs.overwrite) {
        val written = mutableListOf<MediaWriter.Written>()
        val result = runCatching {
            val writer = MediaWriter(this, prefs.esdeTree!!)
            val dir = isDirectory(this, prefs.romsTree!!, "${g.system}/${g.romRelativePath}")
            fun put(file: File?, type: String, ext: String, mime: String) {
                if (file == null) return
                writer.write(listOf("downloaded_media") + mediaPath(g, type, ext, dir), mime, policy) { out ->
                    file.inputStream().use { input ->
                        // Debug builds: `adb shell run-as com.clintonium.es_de_overlay touch files/fail-next-write` stops the next write half-way.
                        if (BuildConfig.DEBUG && File(filesDir, "fail-next-write").delete()) {
                            out.write(input.readNBytes(file.length().toInt() / 2))
                            throw IOException("stopped half-way on purpose")
                        }
                        input.copyTo(out)
                    }
                }?.let { written += it }
                file.delete()
            }
            put(png, prefs.mediaType, "png", "image/png")
            put(mp4, "videos", "mp4", "video/mp4")
        }
        result.exceptionOrNull()?.let { Log.e(TAG, "save failed", it) }
        val pending = written.filter { it.finish != null }
        written -= pending
        if (pending.isNotEmpty()) {
            if (result.isSuccess) {
                val art = runCatching { screenshot(g) }.getOrNull()
                main.post {
                    menu.title = launch?.name.orEmpty()
                    menu.art = art
                    menu.cropArt = prefs.menuImageCrops
                    ask(g, pending, written)
                }
                return
            }
            // A capture that failed part-way keeps the files already there.
            pending.forEach { runCatching { it.finish!!(false) } }
        }
        report(g, result.exceptionOrNull(), written, png != null || mp4 != null)
    }

    /**
     * Main thread: for each of [pending] in turn, asks in the menu whether it replaces the file already there, with
     * the cursor on keeping it. Then swaps in those to [replace], drops the rest and reports them with [done]. B,
     * leaving the game or the menu closing any other way keeps the file the open question is about.
     */
    private fun ask(
        g: GameRef,
        pending: List<MediaWriter.Written>,
        done: List<MediaWriter.Written>,
        replace: Set<MediaWriter.Written> = emptySet(),
        at: Int = 0,
    ) {
        fun end() {
            unanswered = null
            menu.release()
            io.execute {
                val finished = pending.mapNotNull { w ->
                    runCatching { w.finish!!(w in replace) }.onFailure { Log.w(TAG, "finishing ${w.path} failed", it) }.getOrNull()
                }
                report(g, null, done + finished, true)
            }
        }
        val w = pending.getOrNull(at)
        if (w == null || gameInFront() != g) return end()
        unanswered = ::end
        val kind = if (w.path.endsWith(".mp4")) "Video" else "Screenshot"
        menu.show(listOf("Keep Existing", "Replace $kind"), { i ->
            ask(g, pending, done, if (i == 1) replace + w else replace, at + 1)
        }, ::end)
    }

    /** io thread: says in a toast and a notification what [written] holds, or why nothing was saved. */
    private fun report(g: GameRef, failure: Throwable?, written: List<MediaWriter.Written>, captured: Boolean) {
        val text = when {
            failure != null -> "Couldn't save: ${failure.message}"
            written.isNotEmpty() -> {
                val (replaced, saved) = written.partition { it.replaced }
                // Replaced first: a toast shows only two lines.
                listOf("Replaced" to replaced, "Saved" to saved).filter { it.second.isNotEmpty() }.joinToString("\n") { (label, files) ->
                    files.joinToString(prefix = "$label: ") { it.path.removePrefix("downloaded_media/") }
                }
            }
            !captured -> "Nothing captured"
            else -> "Skipped: files already exist"
        } + if (noGameSound && written.any { it.path.endsWith(".mp4") }) "\nThe clip has no sound: the game's app wasn't found" else ""
        main.post { toast(text) }
        notify(this, RESULT_ID, text)
        Log.i(TAG, "saved ${written.map { "${it.path} (replaced: ${it.replaced})" }} for $g")
    }

    /** Folds the lines ES-DE logged since the last poll into its latest launch. */
    private fun readLog() {
        val tree = prefs.esdeTree ?: return
        val lines = log.read(this, tree) {
            launch = null
            pick.set(null)
        }
        followSwitcherLaunch(lines)
        prefs.learnSystemNames(systemNames(lines.asSequence()))
        // A theme or variant changed in ES-DE's menu: the switcher's look and the layouts it steers by are read again.
        if (lines.any { "Finished loading theme" in it }) recentStale = true
        val before = launch
        var latest = before
        lines.forEach { latest = nextLaunch(latest, it) }
        if (latest != before && latest != null) {
            launch = latest
            game = resolve(latest)
            if (latest.stamp != before?.stamp || latest.pkg != before.pkg) {
                hotkeys = latest.pkg?.let { pkg -> runCatching { RetroArchHotkeys.read(pkg) }.onFailure { Log.w(TAG, "reading RetroArch's hotkeys failed", it) }.getOrNull() }
                shaderOff = false
            }
            // Pictures PPSSPP or ARMSX2 saves from now until ES-DE is back are this game's.
            val emulator = when {
                latest.pkg?.startsWith("org.ppsspp.") == true -> "ppsspp"
                latest.pkg == "com.armsx2" -> "armsx2"
                else -> null
            }
            discSession = game?.let { g -> emulator?.let { Triple(g, it, System.currentTimeMillis() / 1000) } }
            // Its folders are looked for again when none is known for it or one known is gone.
            val known = prefs.discStates.filter { it.endsWith(".ini") == (emulator == "armsx2") }
            if (discSession != null && (known.isEmpty() || ShizukuShell.output("ls -d " + known.joinToString(" ") { ShizukuShell.quote(it) }) == null)) {
                findDiscStates()
            }
            Log.i(TAG, "launch: ${latest.name} (${latest.system}) pkg=${latest.pkg} -> $game")
            // Copies a downloaded theme and reads the look now, so the menu doesn't when Back opens it.
            game?.let { g ->
                runCatching { themeLook.warm(g.system, prefs.menuImage) }.onFailure { Log.w(TAG, "warming the look failed", it) }
            }
        }
    }

    /**
     * io thread: follows the switcher's launch through [lines], the lines ES-DE logged since the last poll, to the end
     * of its game, by when ES-DE has written the game's play to its gamelist.
     */
    private fun followSwitcherLaunch(lines: List<String>) {
        for (line in lines) {
            val followed = pick.get() ?: return
            val (next, ended) = followPick(followed, line, SystemClock.uptimeMillis())
            pick.compareAndSet(followed, next)
            if (ended) {
                recentStale = true
                switcherGameEnded = true
            }
        }
    }

    /**
     * io thread: with [fg] in front, notes the switcher's game as played once it's up. With ES-DE in front after it,
     * the game has been left, and the switcher opens again at once, on the games as last read with the game just
     * played first, whether or not ES-DE has logged the game's end yet; they're read again once it has. Before the
     * game is up, presses launch once more if it failed in a RetroArch that was already running ([retryPick]).
     */
    private fun followSwitcherFront(fg: String?) {
        val followed = pick.get() ?: return
        if (fg != null && fg == followed.launch?.pkg) {
            if (!followed.played) pick.compareAndSet(followed, followed.copy(played = true))
            return
        }
        if (fg != ESDE) return
        if (followed.played) {
            if (followed.reopenedAt == 0L) {
                if (pick.compareAndSet(followed, followed.copy(reopenedAt = SystemClock.uptimeMillis()))) {
                    val round = pictureRound
                    main.post { reopenSwitcher(round) }
                }
            } else if (followed.ended) {
                // Reopened, and its end logged: nothing more to follow.
                pick.compareAndSet(followed, null)
            }
            return
        }
        val retry = retryPick(followed, SystemClock.uptimeMillis(), ::runningBefore, ::retroarchRunning) ?: return
        if (!pick.compareAndSet(followed, retry)) return
        val pressed = ShizukuShell.output("input keyevent ${KeyEvent.KEYCODE_ENTER}", 5) != null
        Log.i(TAG, "switcher: ${followed.launch?.name} didn't start, RetroArch quit instead; " + if (pressed) "launching it again" else "couldn't launch it again")
        if (!pressed) pick.compareAndSet(retry, null)
    }

    /** io thread: RetroArch's packages running as the switcher's last pick was made; none if they couldn't be read. */
    private fun runningBefore(): Set<String> = runningAtPick?.let { runCatching { it.get(5, TimeUnit.SECONDS) }.getOrNull() }.orEmpty()

    /** Off the main thread: RetroArch's packages running now, through Shizuku; null when they can't be read. */
    private fun retroarchRunning(): Set<String>? = ShizukuShell.output("ps -A -o NAME", 5)?.decodeToString()?.let(::retroarchProcesses)

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
            // The next Capture asks again.
            projection = null
        }
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        /**
         * From the viewer's process: opening [EXTRA_NAME] (the document at [EXTRA_KEY], if it was one) failed, for
         * [EXTRA_REASON] if given, which the menu underneath shows.
         */
        const val ACTION_OPEN_FAILED = "com.clintonium.es_de_overlay.OPEN_FAILED"
        /** From the viewer's process: the viewer on a guide just saved from the browser has closed, so list the guides again. */
        const val ACTION_GUIDE_SAVED = "com.clintonium.es_de_overlay.GUIDE_SAVED"
        /** From the viewer's process: the document at [EXTRA_KEY] is on screen, and becomes the game's last opened. */
        const val ACTION_DOC_OPENED = "com.clintonium.es_de_overlay.DOC_OPENED"
        const val EXTRA_KEY = "key"
        const val EXTRA_NAME = "name"
        const val EXTRA_REASON = "reason"
        /** Open Menu on the service's notification. */
        const val ACTION_OPEN_MENU = "com.clintonium.es_de_overlay.OPEN_MENU"
        var running by mutableStateOf(false)

        // Main thread only; set while the service exists.
        @SuppressLint("StaticFieldLeak")
        private var instance: HelperService? = null

        /**
         * Main thread: whether the controller's Back is the overlay's: a capture is running, the menu is open, ES-DE's
         * game is in front, or ES-DE is and the switcher can open over it.
         */
        fun takesBack(): Boolean = instance?.run {
            running && (capture != null || menu.isOpen || switcher.isOpen || front.read(this).let { currentGame(it) != null || switcherReady(it) })
        } == true

        /** Main thread: the controller's Back, taken because [takesBack] said so. */
        fun back() {
            instance?.onBack()
        }

        /**
         * Main thread: closes the menu now rather than at the next poll. For the controller's Home, which Android still
         * gets, and for Quit Game once the game is gone.
         */
        fun closeMenuNow() {
            instance?.closeMenu()
        }

        /** Main thread: the controller's Home has gone down, which Android still gets. */
        fun homeDown() {
            instance?.onHomeDown()
        }

        /** ES-DE's window has come to the front. */
        fun esdeShown() {
            instance?.pollSoon()
        }

        /**
         * Main thread: whether the controller's buttons are kept from ES-DE: the switcher is steering it, and a press
         * of A meanwhile would launch whatever its cursor is passing.
         */
        fun steering(): Boolean = instance?.steering == true
    }
}
