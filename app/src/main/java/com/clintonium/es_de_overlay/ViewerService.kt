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
 * Runs the viewer in its own `:viewer` process. A WebView's library, and the GPU and heap memory big pages
 * leave behind, can't be released from a live process, so the process ends when the viewer closes.
 * Started with [intent] for a document; stopping the service closes the viewer.
 */
class ViewerService : Service() {
    private lateinit var viewer: ViewerOverlay

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        viewer = ViewerOverlay(overlayContext(this), Prefs(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val name = intent?.getStringExtra(EXTRA_NAME)
        val key = intent?.getStringExtra(EXTRA_KEY)
        if (uri == null || name == null || key == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching { viewer.open(uri, name, key) { stopSelf() } }.onFailure {
            Log.e(TAG, "opening $name failed", it)
            Toast.makeText(this, "Couldn't open $name: ${it.message}", Toast.LENGTH_SHORT).show()
            // Ending the process at once could take the toast with it.
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 2_500)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        viewer.close()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val EXTRA_KEY = "key"

        /**
         * Opens [uri] over whatever is on screen, picking the format from [name]'s extension, where it was left
         * last time; [key] is the document's path below the ES-DE folder.
         */
        fun intent(context: Context, uri: Uri, name: String, key: String): Intent =
            Intent(context, ViewerService::class.java).setData(uri).putExtra(EXTRA_NAME, name).putExtra(EXTRA_KEY, key)
    }
}
