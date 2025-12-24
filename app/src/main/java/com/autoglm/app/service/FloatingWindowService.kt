package com.autoglm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.autoglm.app.MainActivity
import com.autoglm.app.R

class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "autoglm_floating"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_STATUS = "com.autoglm.app.UPDATE_STATUS"
        const val ACTION_UPDATE_ACTION = "com.autoglm.app.UPDATE_ACTION"
        const val ACTION_STOP_TASK = "com.autoglm.app.STOP_TASK"
        const val ACTION_PAUSE_TASK = "com.autoglm.app.PAUSE_TASK"
        const val ACTION_SET_PAUSED = "com.autoglm.app.SET_PAUSED"
        const val ACTION_HIDE = "com.autoglm.app.HIDE_FLOATING"
        const val ACTION_SHOW = "com.autoglm.app.SHOW_FLOATING"

        const val EXTRA_STATUS = "status"
        const val EXTRA_ACTION = "action"
        const val EXTRA_IS_PAUSED = "is_paused"
        const val EXTRA_LOG_MESSAGE = "log_message"
        const val ACTION_ADD_LOG = "com.autoglm.app.ADD_LOG"

        private var instance: FloatingWindowService? = null
        fun isRunning(): Boolean = instance != null

        var onStopTaskListener: (() -> Unit)? = null
        var onPauseTaskListener: (() -> Unit)? = null
        var onCloseWindowListener: (() -> Unit)? = null
        var onNewInstructionListener: ((String) -> Unit)? = null
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Views
    private var ivMinimizedOrb: ImageView? = null
    private var layoutExpanded: LinearLayout? = null
    private var layoutLogs: LinearLayout? = null
    private var layoutBar: LinearLayout? = null
    private var viewDragHandle: View? = null
    private var svLogs: ScrollView? = null
    private var llMessages: LinearLayout? = null
    private var ivKeyboard: ImageView? = null
    private var ivOrbInput: ImageView? = null
    private var ivOrbCenter: ImageView? = null
    private var etInput: EditText? = null
    private var btnMinimize: ImageView? = null
    private var btnCloseLogs: ImageView? = null

    // State
    private var isMinimized = false
    private var isInputMode = false
    private var isLogsVisible = true
    private var isPaused = false

    // Drag
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Resize Drag
    private var initialLogHeight = 0
    private var resizeTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        instance = this
        instance = this
        startForeground(NOTIFICATION_ID, createNotification())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(CHANNEL_ID, "AutoGLM", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AutoGLM 运行中")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView =
                (getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(
                        R.layout.layout_floating_window,
                        null
                )

        // Bind views
        ivMinimizedOrb = floatingView?.findViewById(R.id.iv_minimized_orb)
        layoutExpanded = floatingView?.findViewById(R.id.layout_expanded)
        layoutLogs = floatingView?.findViewById(R.id.layout_logs)
        layoutBar = floatingView?.findViewById(R.id.layout_bar)
        svLogs = floatingView?.findViewById(R.id.sv_logs)
        llMessages = floatingView?.findViewById(R.id.ll_messages)
        ivKeyboard = floatingView?.findViewById(R.id.iv_keyboard)
        ivOrbInput = floatingView?.findViewById(R.id.iv_orb_input)
        ivOrbCenter = floatingView?.findViewById(R.id.iv_orb_center)
        etInput = floatingView?.findViewById(R.id.et_input)
        btnMinimize = floatingView?.findViewById(R.id.btn_minimize)
        btnCloseLogs = floatingView?.findViewById(R.id.btn_close_logs)
        viewDragHandle = floatingView?.findViewById(R.id.view_drag_handle)

        // Window params
        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels

        layoutParams =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                else WindowManager.LayoutParams.TYPE_PHONE,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.TOP or Gravity.START
                            x = 50
                            y = (screenHeight * 0.4).toInt() // Initial position at 40% height
                        }

        // 默认显示日志区域
        layoutLogs?.visibility = View.VISIBLE

        setupListeners()
        windowManager?.addView(floatingView, layoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingView == null) {
            createFloatingWindow()
        }

        intent?.let {
            when (it.action) {
                ACTION_SHOW -> {
                    if (isMinimized) expand()
                    floatingView?.visibility = View.VISIBLE
                }
                ACTION_HIDE -> {
                    floatingView?.visibility = View.GONE
                }
                ACTION_SET_PAUSED -> {
                    isPaused = it.getBooleanExtra(EXTRA_IS_PAUSED, false)
                    updatePauseButton()
                }
                ACTION_ADD_LOG -> {
                    val message = it.getStringExtra(EXTRA_LOG_MESSAGE)
                    if (!message.isNullOrEmpty()) {
                        addLog(message)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun setupListeners() {
        // 最小化球 (拖拽 + 长按关闭 + 点击展开)
        ivMinimizedOrb?.setOnTouchListener { v, event ->
            handleDragWithLongPress(
                    v,
                    event,
                    onClick = { expand() },
                    onLongPress = {
                        onCloseWindowListener?.invoke()
                        stopSelf()
                    }
            )
        }

        // 键盘图标 -> 切换输入模式
        ivKeyboard?.setOnClickListener { setInputMode(true) }

        // 中间光环触摸 (拖拽 + 点击暂停) - 取消长按关闭
        ivOrbCenter?.setOnTouchListener { v, event ->
            handleDragWithClick(
                    v,
                    event,
                    onClick = {
                        onPauseTaskListener?.invoke()
                        updatePauseButton()
                    }
            )
        }

        // 输入模式下的光环 -> 隐藏输入
        ivOrbInput?.setOnClickListener { setInputMode(false) }

        // 最小化按钮
        btnMinimize?.setOnClickListener { minimize() }

        // 关闭日志
        btnCloseLogs?.setOnClickListener {
            if (isLogsVisible) {
                val logHeight = layoutLogs?.height ?: 0
                isLogsVisible = false
                layoutLogs?.visibility = View.GONE

                // 调整 Y 坐标，使底部保持位置不变 (向下移动)
                layoutParams.y += logHeight
                try {
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                } catch (e: Exception) {}
            }
        }

        // 拖拽手柄 -> 调整日志高度
        viewDragHandle?.setOnTouchListener { _, event -> handleResizeLog(event) }

        // 输入框回车发送
        etInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = etInput?.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    onNewInstructionListener?.invoke(text)
                    etInput?.text?.clear()
                    addLog("指令: $text")
                }
                true
            } else false
        }

        // 输入框焦点变化
        etInput?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                layoutParams.flags =
                        layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                try {
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                } catch (e: Exception) {}
            }
        }
    }

    private fun handleDrag(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (dx * dx + dy * dy > 100) isDragging = true
                if (isDragging) {
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                    } catch (e: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) v.performClick()
                return true
            }
        }
        return false
    }

    private fun handleDragWithClick(v: View, event: MotionEvent, onClick: () -> Unit): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (dx * dx + dy * dy > 100) isDragging = true
                if (isDragging) {
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                    } catch (e: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) onClick()
                return true
            }
        }
        return false
    }

    private var longPressHandler: android.os.Handler? = null
    private var longPressRunnable: Runnable? = null
    private val LONG_PRESS_DELAY = 500L

    private fun handleDragWithLongPress(
            v: View,
            event: MotionEvent,
            onClick: () -> Unit,
            onLongPress: () -> Unit
    ): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false

                // 启动长按计时
                longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
                longPressRunnable = Runnable {
                    if (!isDragging) {
                        onLongPress()
                    }
                }
                longPressHandler?.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (dx * dx + dy * dy > 100) {
                    isDragging = true
                    // 取消长按
                    longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
                }
                if (isDragging) {
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                    } catch (e: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 取消长按计时
                longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
                if (!isDragging) onClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
                return true
            }
        }
        return false
    }

    private fun minimize() {
        isMinimized = true
        layoutExpanded?.visibility = View.GONE
        ivMinimizedOrb?.visibility = View.VISIBLE
    }

    private fun expand() {
        isMinimized = false
        ivMinimizedOrb?.visibility = View.GONE
        layoutExpanded?.visibility = View.VISIBLE
    }

    private fun setInputMode(enabled: Boolean) {
        isInputMode = enabled
        if (enabled) {
            ivKeyboard?.visibility = View.GONE
            ivOrbInput?.visibility = View.VISIBLE
            ivOrbCenter?.visibility = View.GONE
            etInput?.visibility = View.VISIBLE

            // 允许获取焦点
            layoutParams.flags =
                    layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            try {
                windowManager?.updateViewLayout(floatingView, layoutParams)
            } catch (e: Exception) {}

            etInput?.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            etInput?.postDelayed(
                    { imm?.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT) },
                    100
            )
        } else {
            ivKeyboard?.visibility = View.VISIBLE
            ivOrbInput?.visibility = View.GONE
            ivOrbCenter?.visibility = View.VISIBLE
            etInput?.visibility = View.GONE

            // 恢复不可获取焦点
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try {
                windowManager?.updateViewLayout(floatingView, layoutParams)
            } catch (e: Exception) {}

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(etInput?.windowToken, 0)
        }
    }

    private fun toggleLogs() {
        isLogsVisible = !isLogsVisible
        layoutLogs?.visibility = if (isLogsVisible) View.VISIBLE else View.GONE
    }

    private fun updatePauseButton() {
        // 暂停时改变 orb 的透明度来表示状态
        ivOrbCenter?.alpha = if (isPaused) 0.5f else 1.0f
    }

    private fun addLog(message: String) {
        // Filter logs: only show AI thinking and execution related
        if (!message.contains("AI 思考") &&
                        !message.contains("AI Think") &&
                        !message.startsWith("执行") &&
                        !message.startsWith("指令:")
        ) {
            return
        }

        // Create message bubble
        val bubble = TextView(this)
        bubble.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        bubble.setPadding(24, 16, 24, 16)

        val params =
                LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        params.setMargins(0, 8, 0, 0)

        // Style based on message type
        if (message.contains("AI 思考") || message.contains("AI Think")) {
            bubble.setBackgroundResource(R.drawable.bg_bubble_think)
            bubble.setTextColor(0xFF6A1B9A.toInt())
            params.marginEnd = 48

            // 提取思考内容（去除前缀）
            val thinkContent =
                    message.replace("💭 AI 思考:", "")
                            .replace("💭 AI Think:", "")
                            .replace("AI 思考:", "")
                            .replace("AI Think:", "")
                            .trim()

            // 默认折叠：显示前50字符
            val preview =
                    if (thinkContent.length > 50) thinkContent.take(50) + "..." else thinkContent
            bubble.text = "💭 $preview"

            // 点击展开/折叠
            var isExpanded = false
            bubble.setOnClickListener {
                isExpanded = !isExpanded
                bubble.text = if (isExpanded) "💭 $thinkContent" else "💭 $preview"
                // 滚动到底部
                svLogs?.post { svLogs?.fullScroll(View.FOCUS_DOWN) }
            }
            // 添加点击提示
            if (thinkContent.length > 50) {
                bubble.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        0,
                        android.R.drawable.arrow_down_float,
                        0
                )
            }
        } else if (message.startsWith("执行")) {
            bubble.text = message
            bubble.setBackgroundResource(R.drawable.bg_bubble_left)
            bubble.setTextColor(0xFF1565C0.toInt())
            params.marginEnd = 48
        } else if (message.startsWith("指令:")) {
            bubble.text = message
            bubble.setBackgroundResource(R.drawable.bg_bubble_right)
            bubble.setTextColor(0xFFFFFFFF.toInt())
            params.marginStart = 48
            params.gravity = android.view.Gravity.END
        } else {
            bubble.text = message
            bubble.setBackgroundResource(R.drawable.bg_bubble_left)
            bubble.setTextColor(0xFF424242.toInt())
            params.marginEnd = 48
        }

        bubble.layoutParams = params
        llMessages?.addView(bubble)

        // Limit message count to prevent memory issues
        if ((llMessages?.childCount ?: 0) > 50) {
            llMessages?.removeViewAt(0)
        }

        // Auto scroll to bottom
        svLogs?.post { svLogs?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun removeFloatingWindow() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {}
        floatingView = null
    }

    private fun handleResizeLog(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialLogHeight = layoutLogs?.height ?: 0
                // 如果当前不可见，初始高度视为0
                if (layoutLogs?.visibility != View.VISIBLE) initialLogHeight = 0
                resizeTouchY = event.rawY
                initialY = layoutParams.y // 记录初始窗口位置
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // 向上拖动 -> rawY减小 -> deltaY > 0
                val deltaY = resizeTouchY - event.rawY
                var newHeight = initialLogHeight + deltaY.toInt()

                // 限制高度范围
                if (newHeight < 0) newHeight = 0
                if (newHeight > 1000) newHeight = 1000 // 最大高度限制

                // 计算实际变化量
                val effectiveDelta = newHeight - initialLogHeight

                if (newHeight > 50) { // 阈值以上显示
                    if (layoutLogs?.visibility != View.VISIBLE) {
                        layoutLogs?.visibility = View.VISIBLE
                        isLogsVisible = true
                    }
                    val params = layoutLogs?.layoutParams
                    params?.height = newHeight
                    layoutLogs?.layoutParams = params
                } else { // 阈值以下隐藏
                    if (layoutLogs?.visibility == View.VISIBLE) {
                        layoutLogs?.visibility = View.GONE
                        isLogsVisible = false
                    }
                }

                // 关键：同时调整窗口 Y 坐标，使底部保持固定
                // 高度增加多少，Y 坐标就减小多少（向上移动）
                layoutParams.y = initialY - effectiveDelta

                // 更新窗口布局
                try {
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                } catch (e: Exception) {}

                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingWindow()
    }
}
