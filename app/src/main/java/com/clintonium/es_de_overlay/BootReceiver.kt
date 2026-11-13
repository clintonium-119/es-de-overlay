package com.clintonium.es_de_overlay

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the service after boot when that's set and both folders are, with no capture consent: the first Capture asks.
 * If Android refuses the start, a notification starts it with one tap.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !prefs.bootStart || prefs.esdeTree == null || prefs.romsTree == null) return
        val service = Intent(context, HelperService::class.java)
        runCatching { context.startForegroundService(service) }.onFailure {
            Log.w(TAG, "start after boot refused", it)
            val start = PendingIntent.getForegroundService(context, BOOT_ID, service, PendingIntent.FLAG_IMMUTABLE)
            notify(context, BOOT_ID, "Tap to start ES-DE Overlay", tap = start)
        }
    }

    companion object {
        const val BOOT_ID = 3
    }
}
