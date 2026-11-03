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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Catches the controller's Back for the menu. Key filtering is on, so Android hands this every key first and waits
 * for the answer; anything but the controller's Back is passed on at once.
 *
 * Also ends a game's app on request ([forceStop]): Android only lets system apps force-stop another app,
 * so this presses Force stop on the app's App Info page in Settings, then goes home to ES-DE.
 */
class ButtonService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    /** Whether the overlay took the Back press in progress, decided on its first down. */
    private var backTaken = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (BuildConfig.DEBUG && event.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "key held ${SystemClock.uptimeMillis() - event.eventTime} ms: ${KeyEvent.keyCodeToString(event.keyCode)} source=0x${event.source.toString(16)}")
        }
        // The controller's Back only: the back swipe and other apps' Back have no gamepad source.
        if (event.keyCode != KeyEvent.KEYCODE_BACK || !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        // Decide on the first down and keep that answer for repeats and the up, so the app never gets half a press.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) backTaken = HelperService.takesBack()
        if (backTaken && event.action == KeyEvent.ACTION_UP) HelperService.back()
        return backTaken
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
        private var instance: ButtonService? = null

        /** Any thread: force-stops [pkg] and returns to ES-DE. Does nothing while the service is off. */
        fun forceStop(pkg: String) {
            Handler(Looper.getMainLooper()).post { instance?.stop(pkg) }
        }
    }
}
