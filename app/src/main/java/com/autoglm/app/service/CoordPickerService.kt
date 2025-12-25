package com.autoglm.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.autoglm.app.R

/**
 * Service for displaying draggable coordinate picker markers as system overlay Used by the script
 * editor to let users pick coordinates on the actual screen
 */
class CoordPickerService : Service() {

    companion object {
        const val ACTION_START_SINGLE = "com.autoglm.app.COORD_PICKER_SINGLE"
        const val ACTION_START_SWIPE = "com.autoglm.app.COORD_PICKER_SWIPE"
        const val ACTION_STOP = "com.autoglm.app.COORD_PICKER_STOP"

        const val EXTRA_X1 = "x1"
        const val EXTRA_Y1 = "y1"
        const val EXTRA_X2 = "x2"
        const val EXTRA_Y2 = "y2"

        // Callback to return coordinates to MainActivity
        var onCoordinatesConfirmed: ((Int, Int, Int?, Int?) -> Unit)? = null
        var onCancelled: (() -> Unit)? = null

        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    // Marker views
    private var marker1: View? = null
    private var marker2: View? = null
    private var confirmBtn: View? = null
    private var cancelBtn: View? = null
    private var coordLabel: TextView? = null
    private var lineView: View? = null

    private var isSwipeMode = false
    private var screenWidth = 0
    private var screenHeight = 0

    // Marker positions (in screen pixels)
    private var markerX1 = 500
    private var markerY1 = 800
    private var markerX2 = 500
    private var markerY2 = 1400

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_SINGLE -> {
                    isSwipeMode = false
                    markerX1 = it.getIntExtra(EXTRA_X1, screenWidth / 2)
                    markerY1 = it.getIntExtra(EXTRA_Y1, screenHeight / 2)
                    createOverlay()
                }
                ACTION_START_SWIPE -> {
                    isSwipeMode = true
                    markerX1 = it.getIntExtra(EXTRA_X1, screenWidth / 2)
                    markerY1 = it.getIntExtra(EXTRA_Y1, screenHeight / 3)
                    markerX2 = it.getIntExtra(EXTRA_X2, screenWidth / 2)
                    markerY2 = it.getIntExtra(EXTRA_Y2, screenHeight * 2 / 3)
                    createOverlay()
                }
                ACTION_STOP -> {
                    onCancelled?.invoke()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Get screen dimensions
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.currentWindowMetrics?.bounds
            screenWidth = bounds?.width() ?: 1080
            screenHeight = bounds?.height() ?: 1920
        } else {
            @Suppress("DEPRECATION") windowManager?.defaultDisplay?.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }

        // Initialize marker positions based on screen if not set
        if (markerX1 == 500) markerX1 = screenWidth / 2
        if (markerY1 == 800) markerY1 = screenHeight / 3
        if (markerX2 == 500) markerX2 = screenWidth / 2
        if (markerY2 == 1400) markerY2 = screenHeight * 2 / 3

        // Create full-screen transparent overlay
        overlayView =
                FrameLayout(this).apply {
                    setBackgroundColor(0x33000000) // Semi-transparent background
                }

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else WindowManager.LayoutParams.TYPE_PHONE,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply { gravity = Gravity.TOP or Gravity.START }

        // Create marker 1 (green)
        marker1 =
                createMarker(0xFF4CAF50.toInt(), if (isSwipeMode) "起" else "●").also {
                    it.x = markerX1.toFloat() - 30
                    it.y = markerY1.toFloat() - 30
                    setupDrag(it, true)
                    (overlayView as FrameLayout).addView(it)
                }

        // Create marker 2 (red) - only for swipe mode
        if (isSwipeMode) {
            marker2 =
                    createMarker(0xFFF44336.toInt(), "终").also {
                        it.x = markerX2.toFloat() - 30
                        it.y = markerY2.toFloat() - 30
                        setupDrag(it, false)
                        (overlayView as FrameLayout).addView(it)
                    }
        }

        // Create coordinate label at top
        coordLabel =
                TextView(this).apply {
                    setBackgroundColor(0xCC000000.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(32, 16, 32, 16)
                    textSize = 16f
                    updateCoordLabel()
                }
        val labelParams =
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            topMargin = 100
                        }
        (overlayView as FrameLayout).addView(coordLabel, labelParams)

        // Create button container at bottom
        val buttonContainer =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(16, 16, 16, 16)
                }

        // Cancel button
        cancelBtn =
                createButton("取消", 0xFF757575.toInt()) {
                    onCancelled?.invoke()
                    stopSelf()
                }
        buttonContainer.addView(
                cancelBtn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 8
                }
        )

        // Confirm button
        confirmBtn =
                createButton("确认", 0xFF4CAF50.toInt()) {
                    if (isSwipeMode) {
                        onCoordinatesConfirmed?.invoke(markerX1, markerY1, markerX2, markerY2)
                    } else {
                        onCoordinatesConfirmed?.invoke(markerX1, markerY1, null, null)
                    }
                    stopSelf()
                }
        buttonContainer.addView(
                confirmBtn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 8
                }
        )

        val btnContainerParams =
                FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                        .apply {
                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            bottomMargin = 100
                        }
        (overlayView as FrameLayout).addView(buttonContainer, btnContainerParams)

        windowManager?.addView(overlayView, params)
    }

    private fun createMarker(color: Int, text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            val size = 60
            layoutParams = FrameLayout.LayoutParams(size, size)
            // Create circular background using GradientDrawable
            background =
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                    }
        }
    }

    private fun createButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(color)
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setOnClickListener { onClick() }
        }
    }

    private fun setupDrag(view: View, isMarker1: Boolean) {
        var initialX = 0f
        var initialY = 0f
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = v.x
                    initialY = v.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    v.x = initialX + dx
                    v.y = initialY + dy

                    // Update stored position (center of marker)
                    if (isMarker1) {
                        markerX1 = (v.x + 30).toInt().coerceIn(0, screenWidth)
                        markerY1 = (v.y + 30).toInt().coerceIn(0, screenHeight)
                    } else {
                        markerX2 = (v.x + 30).toInt().coerceIn(0, screenWidth)
                        markerY2 = (v.y + 30).toInt().coerceIn(0, screenHeight)
                    }
                    updateCoordLabel()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateCoordLabel() {
        val text =
                if (isSwipeMode) {
                    "起点: ($markerX1, $markerY1)  →  终点: ($markerX2, $markerY2)"
                } else {
                    "坐标: ($markerX1, $markerY1)"
                }
        coordLabel?.text = text
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        marker1 = null
        marker2 = null
        coordLabel = null
        confirmBtn = null
        cancelBtn = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        isRunning = false
    }
}
