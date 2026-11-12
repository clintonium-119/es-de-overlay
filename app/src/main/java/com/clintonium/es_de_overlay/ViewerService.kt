package com.clintonium.es_de_overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import com.clintonium.es_de_overlay.core.GameRef

/**
 * Runs the viewer and the guide browser in their own `:viewer` process. A WebView's library, and the GPU and
 * heap memory big pages leave behind, can't be released from a live process, so the process ends when the
 * viewer or browser closes. Started with [intent] for a document or [browse] for a web page; a guide saved
 * from the browser opens in the viewer, and a guide's YouTube link in the browser over it. Stopping the service
 * closes them.
 */
class ViewerService : Service() {
    private lateinit var prefs: Prefs
    private lateinit var viewer: ViewerOverlay
    private lateinit var browser: BrowserOverlay
    private lateinit var themeLook: ThemeLook

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val windows = overlayContext(this)
        prefs = Prefs(this)
        viewer = ViewerOverlay(windows, prefs)
        browser = BrowserOverlay(windows, prefs)
        themeLook = ThemeLook(windows, prefs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val system = intent?.getStringExtra(EXTRA_SYSTEM)
        val rom = intent?.getStringExtra(EXTRA_ROM)
        val uri = intent?.data
        val name = intent?.getStringExtra(EXTRA_NAME)
        val key = intent?.getStringExtra(EXTRA_KEY)
        when {
            url != null && system != null && rom != null -> show(url) {
                browser.open(url, GameRef(system, rom), theme(system).look, ::showSaved) { stopSelf() }
            }
            uri != null && name != null && key != null -> show(name) {
                val theme = theme(systemOf(key))
                viewer.open(uri, name, key, theme, { video(it, theme) }) { stopSelf() }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * Swaps the browser for the viewer on the guide it just saved at [path] below the ES-DE folder. Closing the viewer
     * has the menu underneath list the guides again, now with this one.
     */
    private fun showSaved(path: String) {
        browser.close()
        val name = path.substringAfterLast('/')
        show(name) {
            val theme = theme(systemOf(path))
            viewer.open(treeChild(prefs.esdeTree!!, path), name, path, theme, { video(it, theme) }) {
                startService(Intent(this, HelperService::class.java).setAction(HelperService.ACTION_GUIDE_SAVED))
                stopSelf()
            }
        }
    }

    /** Plays the YouTube page [url] in the browser, over the guide whose link it was; closing it shows the guide again. */
    private fun video(url: String, theme: ThemeLook.Current) {
        runCatching { browser.open(url, null, theme.look, {}, theme) { } }.onFailure { Log.e(TAG, "opening $url failed", it) }
    }

    // The look for a game of [system], before its window is up; the default if the theme can't be read.
    private fun theme(system: String) = runCatching { themeLook.current(system, prefs.menuImage) }
        .onFailure { Log.w(TAG, "reading the theme failed", it) }
        .getOrDefault(ThemeLook.DEFAULT)

    // A manual's or guide's system, from its path below the ES-DE folder: downloaded_media/<system>/….
    private fun systemOf(key: String) = key.removePrefix("downloaded_media/").substringBefore('/')

    /** Runs [open] for [name]; if it fails, says so in the menu underneath (a toast would draw below it) and ends the service. */
    private fun show(name: String, open: () -> Unit) {
        runCatching(open).onFailure {
            Log.e(TAG, "opening $name failed", it)
            startService(
                Intent(this, HelperService::class.java).setAction(HelperService.ACTION_OPEN_FAILED).putExtra(HelperService.EXTRA_NAME, name),
            )
            stopSelf()
        }
    }

    override fun onDestroy() {
        viewer.close()
        browser.close()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_URL = "url"
        private const val EXTRA_SYSTEM = "system"
        private const val EXTRA_ROM = "rom"

        /**
         * Opens [uri] over whatever is on screen, picking the format from [name]'s extension, where it was left
         * last time; [key] is the document's path below the ES-DE folder.
         */
        fun intent(context: Context, uri: Uri, name: String, key: String): Intent =
            Intent(context, ViewerService::class.java).setData(uri).putExtra(EXTRA_NAME, name).putExtra(EXTRA_KEY, key)

        /** Opens [url] in the touch browser over whatever is on screen; a guide saved there goes to [game]'s guides. */
        fun browse(context: Context, url: String, game: GameRef): Intent = Intent(context, ViewerService::class.java)
            .putExtra(EXTRA_URL, url).putExtra(EXTRA_SYSTEM, game.system).putExtra(EXTRA_ROM, game.romRelativePath)
    }
}
