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
 * from the browser opens in the viewer. Stopping the service closes them.
 */
class ViewerService : Service() {
    private lateinit var prefs: Prefs
    private lateinit var viewer: ViewerOverlay
    private lateinit var browser: BrowserOverlay

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val windows = overlayContext(this)
        prefs = Prefs(this)
        viewer = ViewerOverlay(windows, prefs)
        browser = BrowserOverlay(windows, prefs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val system = intent?.getStringExtra(EXTRA_SYSTEM)
        val rom = intent?.getStringExtra(EXTRA_ROM)
        val uri = intent?.data
        val name = intent?.getStringExtra(EXTRA_NAME)
        val key = intent?.getStringExtra(EXTRA_KEY)
        when {
            url != null && system != null && rom != null -> show(url) { browser.open(url, GameRef(system, rom), ::showSaved) { stopSelf() } }
            uri != null && name != null && key != null -> show(name) { viewer.open(uri, name, key) { stopSelf() } }
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
            viewer.open(treeChild(prefs.esdeTree!!, path), name, path) {
                startService(Intent(this, HelperService::class.java).setAction(HelperService.ACTION_GUIDE_SAVED))
                stopSelf()
            }
        }
    }

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
