package com.clintonium.es_de_overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Capture consent needs a person, so after boot we only offer a one-tap notification to start. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs(context).bootStart) {
            notify(context, BOOT_ID, "Tap to start ES-DE Overlay", grant = true)
        }
    }

    companion object {
        const val BOOT_ID = 3
    }
}
