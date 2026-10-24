
package com.absolute.drvcam

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class TargetAppAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage = ""
    private var lastPopupTime = 0L
    private var popup: AlertDialog? = null

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
        val popupEnabled = settings.getBoolean("target_popup_enabled", false)

        if (!popupEnabled || selectedPackage == null ||
            openedPackage != selectedPackage
        ) return

        val now = System.currentTimeMillis()
        if (now - lastPopupTime < 3000L) return

        lastPackage = openedPackage
        lastPopupTime = now

        handler.post {
            showPopup()
        }
    }

    private fun showPopup() {
        if (popup?.isShowing == true) return

        try {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 30, 40, 20)
                setBackgroundColor(Color.rgb(20, 29, 35))
            }

            val title = TextView(this).apply {
                text = "ABSOLUTE DRVCAM"
                textSize = 22f
                setTextColor(Color.rgb(70, 220, 195))
                setPadding(0, 0, 0, 12)
            }

            val message = TextView(this).apply {
                text = "Target app opened."
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 18)
            }

            val openButton = Button(this).apply {
                text = "OPEN DRVCAM"
                setOnClickListener {
                    popup?.dismiss()
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
                setOnClickListener {
                    popup?.dismiss()
                }
            }

            layout.addView(title)
            layout.addView(message)
            layout.addView(openButton)
            layout.addView(closeButton)

            popup = AlertDialog.Builder(this)
                .setView(layout)
                .create()

            popup?.window?.setType(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            )
            popup?.show()

        } catch (_: Exception) {
            Toast.makeText(
                applicationContext,
                "Could not show DRVCAM popup.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        popup?.dismiss()
        popup = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        popup?.dismiss()
        popup = null
        super.onDestroy()
    }
}
