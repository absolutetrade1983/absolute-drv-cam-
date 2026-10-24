package com.absolute.drvcam

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
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

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.rgb(20, 29, 35))
                    cornerRadius = 20f
                    setStroke(2, Color.rgb(70, 220, 195))
                }
                elevation = 16f
            }

            val title = TextView(this).apply {
                text = "ABSOLUTE DRVCAM"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(70, 220, 195))
                setPadding(0, 0, 0, 10)
            }

            val photoFrame = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            val photoView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.MATRIX
                setBackgroundColor(Color.BLACK)
                contentDescription = "Selected DRVCAM photo preview"
            }

            photoFrame.addView(
                photoView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            val prefs = getSharedPreferences("absolute_drvcam", MODE_PRIVATE)
            val photoUriText = prefs.getString("selected_photo_uri", null)

            if (!photoUriText.isNullOrBlank()) {
                try {
                    val uri = Uri.parse(photoUriText)
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            photoView.setImageBitmap(bitmap)
                            photoView.post {
                                val viewW = photoView.width.toFloat()
                                val viewH = photoView.height.toFloat()
                                if (viewW > 0 && viewH > 0) {
                                    val scale = minOf(
                                        viewW / bitmap.width,
                                        viewH / bitmap.height
                                    )
                                    val matrix = Matrix()
                                    matrix.setScale(scale, scale)
                                    matrix.postTranslate(
                                        (viewW - bitmap.width * scale) / 2f,
                                        (viewH - bitmap.height * scale) / 2f
                                    )
                                    photoView.imageMatrix = matrix
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // URI permission or image decoding may fail.
                }
            }

            if (photoView.drawable == null) {
                val noPhoto = TextView(this).apply {
                    text = "Photo unavailable.\nSelect the photo again in DRVCAM."
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    textSize = 15f
                }
                photoFrame.addView(
                    noPhoto,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }

            val matrix = Matrix()
            var scaleFactor = 1f
            var translateX = 0f
            var translateY = 0f
            var lastX = 0f
            var lastY = 0f

            val scaleDetector = ScaleGestureDetector(
                this,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        scaleFactor = (scaleFactor * detector.scaleFactor)
                            .coerceIn(0.5f, 5f)
                        applyPhotoMatrix(photoView, scaleFactor, translateX, translateY)
                        return true
                    }
                }
            )

            photoView.setOnTouchListener { view, event ->
                scaleDetector.onTouchEvent(event)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                            translateX += event.x - lastX
                            translateY += event.y - lastY
                            applyPhotoMatrix(photoView, scaleFactor, translateX, translateY)
                        }
                        lastX = event.x
                        lastY = event.y
                    }
                }
                true
            }

            val hint = TextView(this).apply {
                text = "Pinch to zoom • Drag to move"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.LTGRAY)
                setPadding(0, 8, 0, 8)
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

            root.addView(title)

            root.addView(
                photoFrame,
                LinearLayout.LayoutParams(
                    280,
                    320
                )
            )

            root.addView(hint)
            root.addView(openButton)
            root.addView(closeButton)

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

            wm.addView(root, params)
            popupView = root

        } catch (_: Exception) {
            removePopup()
        }
    }

    private fun applyPhotoMatrix(
        imageView: ImageView,
        scale: Float,
        x: Float,
        y: Float
    ) {
        val drawable = imageView.drawable ?: return
        val width = drawable.intrinsicWidth.toFloat()
        val height = drawable.intrinsicHeight.toFloat()
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()

        if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0) return

        val fitScale = minOf(viewWidth / width, viewHeight / height)
        val finalScale = fitScale * scale

        val matrix = Matrix().apply {
            setScale(finalScale, finalScale)
            postTranslate(
                (viewWidth - width * finalScale) / 2f + x,
                (viewHeight - height * finalScale) / 2f + y
            )
        }
        imageView.imageMatrix = matrix
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
