package com.clintonium.es_de_overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast

/**
 * Runs the viewer and the guide browser in their own `:viewer` process. A WebView's library, and the GPU and
 * heap memory big pages leave behind, can't be released from a live process, so the process ends when the
 * viewer or browser closes. Started with [intent] for a document or [browse] for a web page; stopping the
 * service closes them.
 */
class ViewerService : Service() {
    private lateinit var viewer: ViewerOverlay
    private lateinit var browser: BrowserOverlay

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val windows = overlayContext(this)
        viewer = ViewerOverlay(windows, Prefs(this))
        browser = BrowserOverlay(windows)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val uri = intent?.data
        val name = url ?: intent?.getStringExtra(EXTRA_NAME)
        val key = intent?.getStringExtra(EXTRA_KEY)
        if (url == null && (uri == null || name == null || key == null)) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            if (url != null) browser.open(url) { stopSelf() } else viewer.open(uri!!, name!!, key!!) { stopSelf() }
        }.onFailure {
            Log.e(TAG, "opening $name failed", it)
            Toast.makeText(this, "Couldn't open $name: ${it.message}", Toast.LENGTH_SHORT).show()
            // Ending the process at once could take the toast with it.
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 2_500)
        }
        return START_NOT_STICKY
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

        /**
         * Opens [uri] over whatever is on screen, picking the format from [name]'s extension, where it was left
         * last time; [key] is the document's path below the ES-DE folder.
         */
        fun intent(context: Context, uri: Uri, name: String, key: String): Intent =
            Intent(context, ViewerService::class.java).setData(uri).putExtra(EXTRA_NAME, name).putExtra(EXTRA_KEY, key)

        /** Opens [url] in the touch browser over whatever is on screen. */
        fun browse(context: Context, url: String): Intent = Intent(context, ViewerService::class.java).putExtra(EXTRA_URL, url)
    }
}
