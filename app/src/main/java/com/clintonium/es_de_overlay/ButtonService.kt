package com.clintonium.es_de_overlay

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Test: logs what the system reports to accessibility services, without filtering keys (filtering
 * them stopped RetroArch from seeing the controller at all). Looks for what Retroid's Home guard
 * shows on the first Home press in a game, so the menu can open on that instead of on the key.
 */
class ButtonService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == packageName) return
        Log.i(TAG, "a11y ${AccessibilityEvent.eventTypeToString(event.eventType)} ${event.packageName} ${event.className} ${event.text}")
    }

    override fun onInterrupt() {}
}
