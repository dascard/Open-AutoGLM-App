package com.autoglm.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autoglm.app.core.AIClient
import com.autoglm.app.core.ExecutionStatus
import com.autoglm.app.core.RetryConfig
import com.autoglm.app.core.TaskExecutor
import com.autoglm.app.core.ApiConfig
import com.autoglm.app.core.LogEntry
import com.autoglm.app.core.LogType
import com.autoglm.app.core.TaskResult
import com.autoglm.app.data.PreferencesManager
import com.autoglm.app.databinding.ActivityMainBinding
import com.autoglm.app.service.FloatingWindowService
import com.autoglm.app.ui.WebAppInterface
import com.autoglm.app.ui.WebAppListener
import com.autoglm.app.util.FileLogger
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), WebAppListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val webView: WebView get() = binding.webView
    
    private lateinit var prefsManager: PreferencesManager
    
    // Core components
    private var aiClient: AIClient? = null
    private var taskExecutor: TaskExecutor? = null
    
    // Logs
    private var currentLogs = mutableListOf<LogEntry>()
    private val gson = Gson()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FileLogger.init(this)
        prefsManager = PreferencesManager(this)

        setupWebView()
        updateServiceStatusToWeb()
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatusToWeb()
        // 开始定期检查状态 (每1秒检查一次，共检查10次)
        startStatusPolling()
    }
    
    override fun onPause() {
        super.onPause()
        stopStatusPolling()
    }
    
    private var statusCheckCount = 0
    private val statusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val statusCheckRunnable = object : Runnable {
        override fun run() {
            updateServiceStatusToWeb()
            statusCheckCount++
            if (statusCheckCount < 10) {
                statusHandler.postDelayed(this, 1000)
            }
        }
    }
    
    private fun startStatusPolling() {
        statusCheckCount = 0
        statusHandler.removeCallbacks(statusCheckRunnable)
        statusHandler.postDelayed(statusCheckRunnable, 1000)
    }
    
    private fun stopStatusPolling() {
        statusHandler.removeCallbacks(statusCheckRunnable)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Hide ActionBar
        supportActionBar?.hide()
        
        // Configuration
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            
            // Suppress deprecation warnings or check version
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            
            mediaPlaybackRequiresUserGesture = false
        }
        
        // Enable debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // WebChromeClient for console logs
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                val level = consoleMessage.messageLevel()
                val msg = "JS Console: ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                when (level) {
                    android.webkit.ConsoleMessage.MessageLevel.ERROR -> FileLogger.e("WebView", msg)
                    android.webkit.ConsoleMessage.MessageLevel.WARNING -> FileLogger.w("WebView", msg)
                    else -> FileLogger.d("WebView", msg)
                }
                return true
            }
        }
        
        // WebViewClient for errors and page loaded
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                FileLogger.e("WebView", "Loading Error: ${error?.description}")
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成，隐藏启动覆盖层
                hideSplashOverlay()
            }
        }

        val webInterface = WebAppInterface(this)
        webView.addJavascriptInterface(webInterface, "Android")

        val url = "file:///android_asset/www/index.html"
        FileLogger.i("MainActivity", "Loading URL: $url")
        webView.loadUrl(url)
        
        // 启动覆盖层动画
        startSplashAnimation()
    }
    
    private fun startSplashAnimation() {
        binding.splashContent?.let { content ->
            content.alpha = 0f
            content.translationY = 50f
            content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }
    
    private fun hideSplashOverlay() {
        binding.splashOverlay?.let { overlay ->
            overlay.animate()
                .alpha(0f)
                .setDuration(400)
                .setStartDelay(200)
                .withEndAction {
                    overlay.visibility = android.view.View.GONE
                }
                .start()
        }
    }

    // --- WebAppListener Implementation ---

    override fun onStartTask(task: String) {
        runOnUiThread {
            executeTask(task)
        }
    }

    override fun onStopTask() {
        runOnUiThread {
            taskExecutor?.stop()
            stopFloatingWindow()
        }
    }

    override fun onTogglePause() {
        runOnUiThread {
            taskExecutor?.let { executor ->
                if (executor.isPaused()) {
                    executor.resume()
                    pushToastToWeb("任务继续")
                } else {
                    executor.pause()
                    pushToastToWeb("任务暂停")
                }
            }
        }
    }

    override fun isTaskPaused(): Boolean {
        return taskExecutor?.isPaused() == true
    }

    override fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    override fun onRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    override fun onOpenAccessibilitySettings() {
        openAccessibilitySettings()
    }

    override fun onGetApiConfigs(): String {
        return gson.toJson(prefsManager.apiConfigs)
    }

    override fun onSaveApiConfig(configJson: String) {
        try {
            val config = gson.fromJson(configJson, com.autoglm.app.core.ApiConfig::class.java)
            if (prefsManager.apiConfigs.any { it.id == config.id }) {
                prefsManager.updateApiConfig(config)
            } else {
                prefsManager.addApiConfig(config)
            }
            // Re-init AIClient
            runOnUiThread {
                aiClient = AIClient(
                    apiConfigs = prefsManager.apiConfigs,
                    retryConfig = RetryConfig(maxRetries = prefsManager.maxRetries)
                )
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Save config failed: ${e.message}")
        }
    }

    override fun onDeleteApiConfig(id: String) {
        prefsManager.removeApiConfig(id)
        // Re-init AIClient
        runOnUiThread {
            aiClient = AIClient(
                apiConfigs = prefsManager.apiConfigs,
                retryConfig = RetryConfig(maxRetries = prefsManager.maxRetries)
            )
        }
    }
    
    override fun onGetCommandHistory(): String {
        return gson.toJson(prefsManager.commandHistory)
    }

    // --- Logic ---

    private fun executeTask(task: String) {
        if (!AutoGLMAccessibilityService.isEnabled()) {
            pushToastToWeb("请先开启无障碍服务")
            openAccessibilitySettings()
            return
        }

        if (!prefsManager.hasApiConfigs()) {
            pushToastToWeb("请先配置 API Key")
            sendToWeb("openSettings", "")
            return
        }

        val service = AutoGLMAccessibilityService.getInstance()
        if (service == null) {
            pushToastToWeb("无障碍服务未就绪")
            return
        }

        aiClient = AIClient(
            apiConfigs = prefsManager.apiConfigs,
            retryConfig = RetryConfig(maxRetries = prefsManager.maxRetries)
        )

        // 调试模式：以 # 开头的命令直接执行，不经过 AI
        if (task.startsWith("#")) {
            executeDebugCommand(task.substring(1).trim(), service)
            return
        }

        prefsManager.addCommandToHistory(task)
        taskExecutor = TaskExecutor(this, aiClient!!, service)
        
        setupTaskCallbacks()
        startFloatingWindow()

        // Logs
        lifecycleScope.launch {
            taskExecutor?.logs?.collectLatest { logs ->
                currentLogs = logs.toMutableList()
                val json = gson.toJson(logs)
                sendToWeb("updateLogs", json)
            }
        }

        // Status
        lifecycleScope.launch {
            taskExecutor?.status?.collectLatest { status ->
                val statusStr = when(status) {
                    is ExecutionStatus.Running -> "running"
                    is ExecutionStatus.Completed -> "completed"
                    is ExecutionStatus.Cancelled -> "cancelled"
                    is ExecutionStatus.Error -> "error"
                    else -> "idle"
                }
                sendToWeb("updateStatus", "'$statusStr'")
                
                if (status is ExecutionStatus.Completed || status is ExecutionStatus.Error || status is ExecutionStatus.Cancelled) {
                    stopFloatingWindow()
                }
            }
        }

        lifecycleScope.launch {
            val result = taskExecutor?.executeTask(task)
            when (result) {
                is TaskResult.Success -> pushToastToWeb("任务完成")
                is TaskResult.Failed -> pushToastToWeb("任务失败: ${result.error}")
                else -> {}
            }
        }
    }
    
    /**
     * 调试命令执行器
     * 命令格式（以 # 开头）：
     * #tap 500,500       - 点击坐标
     * #swipe 500,800,500,200 - 滑动
     * #type 你好         - 输入文字
     * #enter             - 按确认键
     * #back              - 返回
     * #home              - 主屏幕
     * #longpress 500,500 - 长按
     * #launch 设置       - 启动应用
     * #screenshot        - 截图测试
     */
    private fun executeDebugCommand(command: String, service: AutoGLMAccessibilityService) {
        lifecycleScope.launch {
            val parts = command.split(" ", limit = 2)
            val cmd = parts[0].lowercase()
            val args = parts.getOrNull(1) ?: ""
            
            // 添加调试开始日志
            val startEntry = LogEntry(System.currentTimeMillis(), LogType.INFO, "執行調試命令: $cmd args: $args")
            currentLogs.add(startEntry)
            sendToWeb("updateLogs", gson.toJson(currentLogs))
            
            val result = when (cmd) {
                "tap" -> {
                    val coords = args.split(",").map { it.trim().toIntOrNull() ?: 0 }
                    if (coords.size >= 2) {
                        currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "点击: (${coords[0]}, ${coords[1]})"))
                        sendToWeb("updateLogs", gson.toJson(currentLogs))
                        service.performTap(coords[0], coords[1])
                    } else false
                }
                "swipe" -> {
                    val coords = args.split(",").map { it.trim().toIntOrNull() ?: 0 }
                    if (coords.size >= 4) {
                        currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "滑动: (${coords[0]}, ${coords[1]}) -> (${coords[2]}, ${coords[3]})"))
                        sendToWeb("updateLogs", gson.toJson(currentLogs))
                        service.performSwipe(coords[0], coords[1], coords[2], coords[3], 300)
                    } else false
                }
                "type" -> {
                    currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "输入: $args"))
                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                    service.performInput(args)
                }
                "enter" -> {
                    currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "执行: 确认/回车"))
                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                    service.performEnter()
                }
                "back" -> {
                    currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "执行: 返回"))
                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                    service.performBack()
                }
                "home" -> {
                    currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "执行: 回主屏幕"))
                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                    service.performHome()
                }
                "longpress" -> {
                    val coords = args.split(",").map { it.trim().toIntOrNull() ?: 0 }
                    if (coords.size >= 2) {
                        currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "长按: (${coords[0]}, ${coords[1]})"))
                        sendToWeb("updateLogs", gson.toJson(currentLogs))
                        service.performLongPress(coords[0], coords[1])
                    } else false
                }
                "launch" -> {
                    try {
                        val pm = packageManager
                        var launchIntent = pm.getLaunchIntentForPackage(args)
                        var fuzzySuggestions: String? = null
                        
                        // 如果不是直接的包名，尝试查找
                        if (launchIntent == null) {
                            val apps = pm.getInstalledApplications(0)
                            val fuzzyMatches = mutableListOf<String>()
                            
                            for (app in apps) {
                                val label = pm.getApplicationLabel(app).toString()
                                
                                // 1. 精确匹配
                                if (label.equals(args, ignoreCase = true)) {
                                    launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                    // 仅当能获取到intent才视为成功
                                    if (launchIntent != null) {
                                        currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "找到精确匹配应用: $label (${app.packageName})"))
                                        sendToWeb("updateLogs", gson.toJson(currentLogs))
                                        break
                                    }
                                }
                                
                                // 2. 收集相似应用
                                if (label.contains(args, ignoreCase = true) || args.contains(label, ignoreCase = true)) {
                                    if (!fuzzyMatches.contains(label)) {
                                        fuzzyMatches.add(label)
                                    }
                                }
                            }
                            
                            // 如果没找到launchIntent但有模糊匹配
                            if (launchIntent == null && fuzzyMatches.isNotEmpty()) {
                                fuzzySuggestions = fuzzyMatches.sortedBy { Math.abs(it.length - args.length) }.take(3).joinToString(", ")
                            }
                        }
                        
                        // 根据结果执行
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "启动应用: $args"))
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            startActivity(launchIntent)
                            true
                        } else if (fuzzySuggestions != null) {
                            currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.WARNING, "未找到精确匹配。相似应用: $fuzzySuggestions"))
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            false
                        } else {
                            currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.WARNING, "未找到应用: $args"))
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            false
                        }
                    } catch (e: Exception) {
                        FileLogger.e("MainActivity", "Launch failed: ${e.message}")
                        currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.ERROR, "启动失败: ${e.message}"))
                        sendToWeb("updateLogs", gson.toJson(currentLogs))
                        false
                    }
                }

                "screenshot" -> {
                    currentLogs.add(LogEntry(System.currentTimeMillis(), LogType.INFO, "正在截图..."))
                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                    val bitmap = service.takeScreenshotBitmap()
                    bitmap != null
                }
                "help" -> {
                    pushToastToWeb("命令: tap, swipe, type, enter, back, home, longpress, launch, screenshot")
                    true
                }
                else -> {
                    pushToastToWeb("未知命令: $cmd，输入 #help 查看帮助")
                    false
                }
            }
            
            // 添加结果日志
            val resultMsg = if (result) "✓ 执行成功" else "✗ 执行失败"
            val resultEntry = LogEntry(
                System.currentTimeMillis(), 
                if (result) LogType.ACTION else LogType.ERROR, 
                resultMsg
            )
            currentLogs.add(resultEntry)
            sendToWeb("updateLogs", gson.toJson(currentLogs))
            
            pushToastToWeb(resultMsg)
        }
    }
    
    private fun setupTaskCallbacks() {
        taskExecutor?.onStatusUpdate = { status, action ->
            updateFloatingWindowStatus(status, action)
        }
        
        taskExecutor?.onPauseStateChanged = { isPaused ->
            updateFloatingWindowPauseState(isPaused)
            sendToWeb("updatePauseState", isPaused.toString())
        }
        
        taskExecutor?.onHideFloatingWindow = { hideFloatingWindow() }
        taskExecutor?.onShowFloatingWindow = { showFloatingWindow() }
        
        FloatingWindowService.onStopTaskListener = {
            onStopTask()
        }
        
        FloatingWindowService.onPauseTaskListener = {
            onTogglePause()
        }
    }

    private fun startFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            pushToastToWeb("需要悬浮窗权限")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopFloatingWindow() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }
    
    private fun hideFloatingWindow() {
        startService(Intent(this, FloatingWindowService::class.java).apply { action = FloatingWindowService.ACTION_HIDE })
    }
    
    private fun showFloatingWindow() {
        startService(Intent(this, FloatingWindowService::class.java).apply { action = FloatingWindowService.ACTION_SHOW })
    }

    private fun updateFloatingWindowStatus(status: String, action: String) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            this.action = FloatingWindowService.ACTION_UPDATE_STATUS
            putExtra(FloatingWindowService.EXTRA_STATUS, status)
            putExtra(FloatingWindowService.EXTRA_ACTION, action)
        }
        startService(intent)
    }

    private fun updateFloatingWindowPauseState(isPaused: Boolean) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_SET_PAUSED
            putExtra(FloatingWindowService.EXTRA_IS_PAUSED, isPaused)
        }
        startService(intent)
    }
    
    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to open accessibility settings: ${e.message}")
        }
    }
    
    private fun updateServiceStatusToWeb() {
        FileLogger.i(TAG, "=== 无障碍服务诊断 ===")
        
        val isInstanceAlive = AutoGLMAccessibilityService.isEnabled()
        val isInSettings = AutoGLMAccessibilityService.isEnabledInSettings(this)
        val isRunningInSystem = AutoGLMAccessibilityService.isServiceRunning(this)
        
        FileLogger.i(TAG, "① 服务实例存在 (instance): $isInstanceAlive")
        FileLogger.i(TAG, "② 系统设置中已启用: $isInSettings")
        FileLogger.i(TAG, "③ AccessibilityManager: $isRunningInSystem")
        
        // 使用最可靠的方法判断
        val isEnabled = isInstanceAlive || isRunningInSystem
        FileLogger.i(TAG, "最终状态: $isEnabled")
        
        if (isInSettings && !isEnabled) {
            FileLogger.w(TAG, "⚠️ 异常：系统显示已开启但服务未运行！")
            FileLogger.w(TAG, "可能原因：onServiceConnected() 未被调用")
            pushToastToWeb("无障碍服务异常，请关闭后重新开启")
        }
        
        sendToWeb("updateServiceStatus", isEnabled.toString())
    }

    private fun pushToastToWeb(message: String) {
        val safeMessage = message.replace("'", "\\'")
        sendToWeb("showToast", "'$safeMessage'")
    }

    private fun sendToWeb(function: String, data: String) {
        runOnUiThread {
            // 确保函数存在后再调用，并传递字符串参数
            val js = """
                if (typeof window.$function === 'function') {
                    window.$function('$data');
                } else {
                    console.log('Bridge function not ready: $function');
                }
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }
}
