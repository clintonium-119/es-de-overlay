package com.clintonium.es_de_overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Listens for Retroid's Home guard: with "Prevent pressing Home accidentally" on, the first Home press
 * in a game shows a `com.rp.settings` toast instead of leaving. That toast opens the menu. No keys are
 * filtered, so the controller reaches the game untouched.
 */
class HomeGuardService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == "com.rp.settings" && event.className == "android.widget.Toast" && HelperService.running) {
            startService(Intent(this, HelperService::class.java).setAction(HelperService.ACTION_HOME_GUARD))
        }
    }

    override fun onInterrupt() {}
}
