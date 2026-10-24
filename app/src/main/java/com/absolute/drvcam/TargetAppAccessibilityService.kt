package com.absolute.drvcam

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class TargetAppAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage = ""
    private var lastPopupTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null ||
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val openedPackage = event.packageName?.toString() ?: return

        if (openedPackage == packageName || openedPackage == lastPackage) {
            return
        }

        val settings = getSharedPreferences("absolute_drvcam", MODE_PRIVATE)
        val selectedPackage = settings.getString("target_package", null)
        val popupEnabled = settings.getBoolean("target_popup_enabled", true)

        if (!popupEnabled || selectedPackage == null ||
            openedPackage != selectedPackage
        ) return

        val now = System.currentTimeMillis()
        if (now - lastPopupTime < 3000L) return

        lastPackage = openedPackage
        lastPopupTime = now

        handler.post {
            Toast.makeText(
                applicationContext,
                "ABSOLUTE DRVCAM: Target app opened",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }
}
