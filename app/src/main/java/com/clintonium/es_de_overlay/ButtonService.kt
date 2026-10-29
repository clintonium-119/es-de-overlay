package com.clintonium.es_de_overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Sees every key before the app in front does. Logs them all, and outside ES-DE takes over Home:
 * the first press only shows a toast (where the in-game menu will open), a second press within
 * 5 s opens the home app (ES-DE) directly, so Retroid's double-press guard never sees it.
 */
class ButtonService : AccessibilityService() {
    private var foreground: String? = null
    private var homeAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Our own windows (the capture button, toasts) aren't the app in front.
        event.packageName?.toString()?.takeIf { it != packageName }?.let { foreground = it }
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.i(TAG, "key ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} repeat=${event.repeatCount} device=${event.device?.name} in front: $foreground")
        // The Nova's controller sends its Home button as MOVE_HOME, not HOME.
        if (event.keyCode != KeyEvent.KEYCODE_MOVE_HOME || foreground == ESDE_PACKAGE) return false
        if (event.action == KeyEvent.ACTION_UP) {
            val now = SystemClock.uptimeMillis()
            if (now - homeAt < 5_000) {
                homeAt = 0
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                homeAt = now
                Toast.makeText(this, "Home caught: press again for ES-DE", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }
}
