
package com.absolute.drvcam

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class TargetAppAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var popupView: LinearLayout? = null
    private var lastPopupTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null ||
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val openedPackage = event.packageName?.toString() ?: return
        if (openedPackage == packageName) return

        val prefs = getSharedPreferences("absolute_drvcam", MODE_PRIVATE)
        val selectedPackage = prefs.getString("target_package", null)
        val enabled = prefs.getBoolean("target_popup_enabled", false)

        if (!enabled || selectedPackage.isNullOrBlank()) return
        if (openedPackage != selectedPackage) return
        if (popupView != null) return

        val now = System.currentTimeMillis()
        if (now - lastPopupTime < 2000L) return

        lastPopupTime = now
        handler.post { showPopup() }
    }

    private fun showPopup() {
        if (popupView != null) return

        try {
            val wm = windowManager
                ?: (getSystemService(WINDOW_SERVICE) as WindowManager)

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 28, 36, 24)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(20, 29, 35))
                elevation = 16f
            }

            val title = TextView(this).apply {
                text = "ABSOLUTE DRVCAM"
                textSize = 22f
                setTextColor(Color.rgb(70, 220, 195))
                setPadding(0, 0, 0, 14)
            }

            val message = TextView(this).apply {
                text = "Target app opened."
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 16)
            }

            val openButton = Button(this).apply {
                text = "OPEN DRVCAM"
                setOnClickListener {
                    removePopup()

                    val intent = Intent(
                        this@TargetAppAccessibilityService,
                        MainActivity::class.java
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }

                    startActivity(intent)
                }
            }

            val closeButton = Button(this).apply {
                text = "CLOSE"
                setOnClickListener { removePopup() }
            }

            layout.addView(title)
            layout.addView(message)
            layout.addView(openButton)
            layout.addView(closeButton)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            wm.addView(layout, params)
            popupView = layout

        } catch (_: Exception) {
            removePopup()
        }
    }

    private fun removePopup() {
        val view = popupView ?: return
        popupView = null

        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        removePopup()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removePopup()
        super.onDestroy()
    }
}
