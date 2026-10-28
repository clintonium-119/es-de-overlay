package com.clintonium.esderecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val TAG = "ESDEShot"
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

class HelperService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?) = null

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
        projection?.stop()
        super.onDestroy()
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
