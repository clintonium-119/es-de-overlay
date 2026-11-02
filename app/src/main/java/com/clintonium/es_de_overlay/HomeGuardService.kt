package com.clintonium.es_de_overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Listens for Retroid's Home guard: with "Prevent pressing Home accidentally" on, the first Home press
 * in a game shows a `com.rp.settings` toast instead of leaving. That toast opens the menu. No keys are
 * filtered, so the controller reaches the game untouched.
 *
 * Also ends a game's app on request ([forceStop]): Android only lets system apps force-stop another app,
 * so this presses Force stop on the app's App Info page in Settings, then goes home to ES-DE.
 */
class HomeGuardService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == "com.rp.settings" && event.className == "android.widget.Toast" && HelperService.running) {
            startService(Intent(this, HelperService::class.java).setAction(HelperService.ACTION_HOME_GUARD))
        }
    }

    override fun onInterrupt() {}

    /** Main thread: opens [pkg]'s App Info, presses Force stop, then OK in its dialog, and goes home. */
    private fun stop(pkg: String) {
        Log.i(TAG, "force-stopping $pkg")
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            ),
        )
        val until = SystemClock.uptimeMillis() + 5_000
        var pressed = false
        fun find(id: String) = rootInActiveWindow?.takeIf { it.packageName == SETTINGS }?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
        fun step() {
            if (!pressed) {
                val forceStop = find("$SETTINGS:id/button3")
                if (forceStop != null && !forceStop.isEnabled) return home("already stopped")
                pressed = forceStop?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            } else if (find("android:id/button1")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return home("stopped")
            }
            if (SystemClock.uptimeMillis() > until) return home("timed out")
            main.postDelayed(::step, 100)
        }
        step()
    }

    private fun home(result: String) {
        Log.i(TAG, "force stop: $result")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    companion object {
        private const val SETTINGS = "com.android.settings"

        // Set only while Android has the service connected, and cleared when it's destroyed.
        @SuppressLint("StaticFieldLeak")
        private var instance: HomeGuardService? = null

        /** Any thread: force-stops [pkg] and returns to ES-DE. Does nothing while the service is off. */
        fun forceStop(pkg: String) {
            Handler(Looper.getMainLooper()).post { instance?.stop(pkg) }
        }
    }
}
