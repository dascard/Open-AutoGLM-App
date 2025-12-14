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
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.autoglm.app.MainActivity
import com.autoglm.app.R

/**
 * 悬浮窗服务
 * 显示 AI 执行状态并提供停止按钮
 */
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
        
        private var instance: FloatingWindowService? = null
        
        fun isRunning(): Boolean = instance != null
        
        // 任务停止回调
        var onStopTaskListener: (() -> Unit)? = null
        // 暂停/继续回调
        var onPauseTaskListener: (() -> Unit)? = null
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isMinimized = false
    private var isPaused = false
    
    private var tvStatus: TextView? = null
    private var tvAction: TextView? = null
    private var btnPause: Button? = null
    private var btnStop: Button? = null
    private var btnMinimize: Button? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeFloatingWindow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_STATUS -> {
                val status = intent.getStringExtra(EXTRA_STATUS)
                val action = intent.getStringExtra(EXTRA_ACTION)
                updateStatus(status ?: "")
                if (action != null) updateAction(action)
            }
            ACTION_UPDATE_ACTION -> {
                val action = intent.getStringExtra(EXTRA_ACTION)
                updateAction(action ?: "")
            }
            ACTION_SET_PAUSED -> {
                isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                updatePauseButton()
            }
            ACTION_HIDE -> hideFloatingWindow()
            ACTION_SHOW -> showFloatingWindow()
            ACTION_STOP_TASK -> {
                onStopTaskListener?.invoke()
            }
            ACTION_PAUSE_TASK -> {
                onPauseTaskListener?.invoke()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoGLM 执行状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示任务执行状态"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoGLM 运行中")
            .setContentText("正在执行自动化任务")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.layout_floating_window, null)
        
        // 获取视图引用
        tvStatus = floatingView?.findViewById(R.id.tvStatus)
        tvAction = floatingView?.findViewById(R.id.tvAction)
        btnPause = floatingView?.findViewById(R.id.btnPause)
        btnStop = floatingView?.findViewById(R.id.btnStop)
        btnMinimize = floatingView?.findViewById(R.id.btnMinimize)
        
        // 暂停按钮点击
        btnPause?.setOnClickListener {
            onPauseTaskListener?.invoke()
        }
        
        // 停止按钮点击
        btnStop?.setOnClickListener {
            onStopTaskListener?.invoke()
        }
        
        // 最小化按钮点击
        btnMinimize?.setOnClickListener {
            if (isMinimized) {
                expandFloatingWindow()
            } else {
                minimizeFloatingWindow()
            }
        }
        
        // 窗口参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }
        
        // 支持拖动
        setupDragListener(params)
        
        windowManager?.addView(floatingView, params)
    }

    private fun setupDragListener(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun removeFloatingWindow() {
        floatingView?.let {
            windowManager?.removeView(it)
        }
        floatingView = null
    }

    private fun updateStatus(status: String) {
        tvStatus?.text = status
    }

    private fun updateAction(action: String) {
        tvAction?.text = action
    }
    
    private fun updatePauseButton() {
        btnPause?.text = if (isPaused) "继续" else "暂停"
    }

    private fun hideFloatingWindow() {
        floatingView?.visibility = View.GONE
    }

    private fun showFloatingWindow() {
        floatingView?.visibility = View.VISIBLE
    }

    private fun minimizeFloatingWindow() {
        isMinimized = true
        tvStatus?.visibility = View.GONE
        tvAction?.visibility = View.GONE
        btnPause?.visibility = View.GONE
        btnStop?.visibility = View.GONE
        btnMinimize?.text = "+"
    }

    private fun expandFloatingWindow() {
        isMinimized = false
        tvStatus?.visibility = View.VISIBLE
        tvAction?.visibility = View.VISIBLE
        btnPause?.visibility = View.VISIBLE
        btnStop?.visibility = View.VISIBLE
        btnMinimize?.text = "−"
    }
}
