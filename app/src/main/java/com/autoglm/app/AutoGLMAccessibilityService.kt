package com.autoglm.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AutoGLM 无障碍服务
 * 提供屏幕截图、点击、滑动、输入等功能
 */
class AutoGLMAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoGLM-Service"
        
        @Volatile
        private var instance: AutoGLMAccessibilityService? = null
        
        fun getInstance(): AutoGLMAccessibilityService? {
            Log.d(TAG, "getInstance() called, instance = $instance")
            return instance
        }
        
        fun isEnabled(): Boolean {
            val enabled = instance != null
            Log.d(TAG, "isEnabled() = $enabled")
            return enabled
        }
        
        /**
         * 检查系统设置中是否启用了无障碍服务
         */
        fun isEnabledInSettings(context: android.content.Context): Boolean {
            return try {
                val enabledServices = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                
                val isInSettings = enabledServices.contains(context.packageName)
                Log.d(TAG, "isEnabledInSettings: $isInSettings")
                Log.d(TAG, "enabledServices: $enabledServices")
                Log.d(TAG, "packageName: ${context.packageName}")
                isInSettings
            } catch (e: Exception) {
                Log.e(TAG, "Error checking settings: ${e.message}")
                false
            }
        }
        
        /**
         * 使用 AccessibilityManager 检查服务是否真正运行
         */
        fun isServiceRunning(context: android.content.Context): Boolean {
            return try {
                val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                Log.d(TAG, "AccessibilityManager.isEnabled = ${am.isEnabled}")
                
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                Log.d(TAG, "Found ${enabledServices.size} enabled accessibility services:")
                
                for (service in enabledServices) {
                    val servicePkg = service.resolveInfo?.serviceInfo?.packageName
                    val serviceName = service.resolveInfo?.serviceInfo?.name
                    Log.d(TAG, "  - Package: $servicePkg, Name: $serviceName")
                    if (servicePkg == context.packageName) {
                        Log.i(TAG, "✅ 我们的服务在 AccessibilityManager 中被找到！")
                        return true
                    }
                }
                
                Log.w(TAG, "❌ 我们的服务未在 AccessibilityManager 中找到")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking AccessibilityManager: ${e.message}", e)
                false
            }
        }
    }

    init {
        Log.i(TAG, ">>> AutoGLMAccessibilityService CONSTRUCTOR called <<<")
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, ">>> onCreate() called <<<")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, ">>> onServiceConnected() called <<<")
        Log.i(TAG, ">>> Setting instance = this <<<")
        instance = this
        
        // 打印服务信息
        try {
            val info = serviceInfo
            Log.i(TAG, "ServiceInfo - flags: ${info?.flags}")
            Log.i(TAG, "ServiceInfo - feedbackType: ${info?.feedbackType}")
            Log.i(TAG, "ServiceInfo - packageNames: ${info?.packageNames?.joinToString()}")
            Log.i(TAG, "ServiceInfo - eventTypes: ${info?.eventTypes}")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting service info: ${e.message}")
        }
        
        Log.i(TAG, ">>> Service connected successfully, instance is now: $instance <<<")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, ">>> onUnbind() called <<<")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 仅在第一次收到事件时打印日志
        Log.v(TAG, "onAccessibilityEvent: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.w(TAG, ">>> onInterrupt() called <<<")
    }

    override fun onDestroy() {
        Log.i(TAG, ">>> onDestroy() called <<<")
        instance = null
        super.onDestroy()
        Log.i(TAG, ">>> Service destroyed, instance is now null <<<")
    }

    /**
     * 执行点击操作
     */
    fun performTap(x: Int, y: Int): Boolean {
        return try {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            
            val latch = CountDownLatch(1)
            var success = false
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    success = false
                    latch.countDown()
                }
            }, null)
            
            latch.await(5, TimeUnit.SECONDS)
            Log.d(TAG, "Tap at ($x, $y): $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform tap", e)
            false
        }
    }

    /**
     * 执行滑动操作
     */
    fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): Boolean {
        return try {
            val path = Path()
            path.moveTo(x1.toFloat(), y1.toFloat())
            path.lineTo(x2.toFloat(), y2.toFloat())
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
                .build()
            
            val latch = CountDownLatch(1)
            var success = false
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    success = false
                    latch.countDown()
                }
            }, null)
            
            latch.await(10, TimeUnit.SECONDS)
            Log.d(TAG, "Swipe from ($x1, $y1) to ($x2, $y2): $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform swipe", e)
            false
        }
    }

    /**
     * 执行输入操作
     */
    fun performInput(text: String): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val focusedNode = findFocusedEditText(rootNode)
            
            if (focusedNode != null) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                focusedNode.recycle()
                Log.d(TAG, "Input text: $success")
                success
            } else {
                Log.w(TAG, "No focused EditText found")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform input", e)
            false
        }
    }

    /**
     * 执行返回操作
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * 执行回到主屏幕操作
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    /**
     * 执行确认/回车操作
     * 模拟按下输入法的确认键（搜索、发送、下一步等）
     */
    fun performEnter(): Boolean {
        return try {
            val rootNode = rootInActiveWindow ?: return false
            val focusedNode = findFocusedEditText(rootNode)
            
            if (focusedNode != null) {
                // 尝试执行 IME_ACTION（输入法确认键）
                val imeActionResult = focusedNode.performAction(
                    AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                )
                
                // 如果 IME 动作不可用，尝试用 ACTION_CLICK 模拟
                if (!imeActionResult) {
                    // 备用：发送回车键事件
                    val arguments = android.os.Bundle()
                    arguments.putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                    )
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, arguments)
                }
                
                focusedNode.recycle()
                Log.d(TAG, "performEnter: true")
                true
            } else {
                // 没有焦点的输入框，尝试全局返回（模拟确认）
                Log.w(TAG, "No focused EditText, trying global action")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform enter", e)
            false
        }
    }
    
    /**
     * 执行长按操作
     */
    fun performLongPress(x: Int, y: Int, duration: Int = 1000): Boolean {
        return try {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
                .build()
            
            val latch = CountDownLatch(1)
            var success = false
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    success = false
                    latch.countDown()
                }
            }, null)
            
            latch.await(duration.toLong() + 2000, TimeUnit.MILLISECONDS)
            Log.d(TAG, "Long press at ($x, $y) for ${duration}ms: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform long press", e)
            false
        }
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditText(child)
            if (result != null) {
                return result
            }
            child.recycle()
        }
        
        return null
    }

    /**
     * 截取屏幕并返回 Bitmap
     */
    suspend fun takeScreenshotBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "takeScreenshot not supported on Android < 11")
            return null
        }
        
        return suspendCoroutine { continuation ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )
                            screenshotResult.hardwareBuffer.close()
                            continuation.resume(bitmap)
                        }
                        
                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "Screenshot failed with error code: $errorCode")
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take screenshot", e)
                continuation.resume(null)
            }
        }
    }
}
