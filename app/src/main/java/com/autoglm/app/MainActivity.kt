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
import com.autoglm.app.core.ApiConfig
import com.autoglm.app.core.ExecutionStatus
import com.autoglm.app.core.LogEntry
import com.autoglm.app.core.LogType
import com.autoglm.app.core.RetryConfig
import com.autoglm.app.core.ShizukuTaskExecutor
import com.autoglm.app.core.TaskExecutor
import com.autoglm.app.core.TaskResult
import com.autoglm.app.data.PreferencesManager
import com.autoglm.app.databinding.ActivityMainBinding
import com.autoglm.app.service.FloatingWindowService
import com.autoglm.app.shizuku.ShizukuHelper
import com.autoglm.app.ui.WebAppInterface
import com.autoglm.app.ui.WebAppListener
import com.autoglm.app.util.FileLogger
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), WebAppListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_EXPORT_FILE = 10001
        private const val REQUEST_CODE_IMPORT_FILE = 10002
    }

    private lateinit var binding: ActivityMainBinding
    private val webView: WebView
        get() = binding.webView

    private lateinit var prefsManager: PreferencesManager

    // Core components
    private var aiClient: AIClient? = null
    private var taskExecutor: TaskExecutor? = null
    private var shizukuTaskExecutor: ShizukuTaskExecutor? = null

    // File export pending content
    private var pendingExportContent: String? = null

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
        initShizuku()

        // Handle back button: call JS first
        onBackPressedDispatcher.addCallback(
                this,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // Call JS handleBack function
                        webView.evaluateJavascript(
                                "window.handleBack ? window.handleBack() : false"
                        ) { result ->
                            val handled = result?.trim() == "true"
                            if (!handled) {
                                // JS didn't handle it, allow default back behavior (finish
                                // activity)
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        }
                    }
                }
        )
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
    private val statusCheckRunnable =
            object : Runnable {
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
        webView.webChromeClient =
                object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(
                            consoleMessage: android.webkit.ConsoleMessage
                    ): Boolean {
                        val level = consoleMessage.messageLevel()
                        val msg =
                                "JS Console: ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                        when (level) {
                            android.webkit.ConsoleMessage.MessageLevel.ERROR ->
                                    FileLogger.e("WebView", msg)
                            android.webkit.ConsoleMessage.MessageLevel.WARNING ->
                                    FileLogger.w("WebView", msg)
                            else -> FileLogger.d("WebView", msg)
                        }
                        return true
                    }
                }

        // WebViewClient for errors and page loaded
        webView.webViewClient =
                object : android.webkit.WebViewClient() {
                    override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                    ) {
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
                    .withEndAction { overlay.visibility = android.view.View.GONE }
                    .start()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_EXPORT_FILE -> {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        try {
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(
                                        pendingExportContent?.toByteArray() ?: byteArrayOf()
                                )
                            }
                            pushToastToWeb("导出成功")
                        } catch (e: Exception) {
                            FileLogger.e(TAG, "Export write failed: ${e.message}")
                            pushToastToWeb("导出失败: ${e.message}")
                        }
                    }
                }
                pendingExportContent = null
            }
            REQUEST_CODE_IMPORT_FILE -> {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        try {
                            val filename = uri.lastPathSegment ?: "imported.txt"
                            val content =
                                    contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                                        it.readText()
                                    }
                                            ?: ""

                            // Call JavaScript callback
                            webView.post {
                                val escapedFilename =
                                        filename.replace("\"", "\\\"").replace("'", "\\'")
                                val escapedContent =
                                        content.replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\n", "\\n")
                                                .replace("\r", "")
                                webView.evaluateJavascript(
                                        "window.onFileImported && window.onFileImported(\"$escapedFilename\", \"$escapedContent\")",
                                        null
                                )
                            }
                        } catch (e: Exception) {
                            FileLogger.e(TAG, "Import read failed: ${e.message}")
                            pushToastToWeb("导入失败: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    // --- WebAppListener Implementation ---

    override fun onStartTask(task: String) {
        runOnUiThread { executeTask(task) }
    }

    override fun onStartTaskWithMode(task: String, mode: String) {
        runOnUiThread {
            when (mode) {
                "shizuku" -> executeTaskWithShizuku(task)
                else -> executeTask(task)
            }
        }
    }

    override fun onStopTask() {
        runOnUiThread {
            taskExecutor?.stop()
            shizukuTaskExecutor?.stop()
            // 不关闭悬浮窗，只更新状态
            updateFloatingWindowStatus("已停止", "")
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
            shizukuTaskExecutor?.let { executor ->
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
        return taskExecutor?.isPaused() == true || shizukuTaskExecutor?.isPaused() == true
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
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                    )
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
                aiClient =
                        AIClient(
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
            aiClient =
                    AIClient(
                            apiConfigs = prefsManager.apiConfigs,
                            retryConfig = RetryConfig(maxRetries = prefsManager.maxRetries)
                    )
        }
    }

    override fun onGetCommandHistory(): String {
        return gson.toJson(prefsManager.commandHistory)
    }

    override fun onClearCommandHistory() {
        prefsManager.clearCommandHistory()
        runOnUiThread { pushToastToWeb("历史记录已清除") }
    }

    // --- Shizuku Implementation ---

    override fun onCheckShizukuAvailable(): Boolean {
        return ShizukuHelper.isAvailable()
    }

    override fun onCheckShizukuPermission(): Boolean {
        return ShizukuHelper.hasPermission()
    }

    override fun onRequestShizukuPermission() {
        runOnUiThread { ShizukuHelper.requestPermission() }
    }

    override fun onBindShizukuService() {
        runOnUiThread { ShizukuHelper.bindUserService() }
    }

    override fun onExecuteAdbCommand(command: String): String {
        return ShizukuHelper.executeCommand(command) ?: "Error: Service not available"
    }

    override fun onGetShizukuStatus(): String {
        val available = ShizukuHelper.isAvailable()
        val hasPermission = ShizukuHelper.hasPermission()
        val serviceBound = ShizukuHelper.isServiceBound()
        val uid = ShizukuHelper.getUid()
        val privilege =
                when {
                    uid == 0 -> "ROOT"
                    uid == 2000 -> "ADB"
                    else -> "UNKNOWN"
                }
        return gson.toJson(
                mapOf(
                        "available" to available,
                        "hasPermission" to hasPermission,
                        "serviceBound" to serviceBound,
                        "uid" to uid,
                        "privilege" to privilege
                )
        )
    }

    // --- Settings Implementation ---

    override fun onGetLogLevel(): Int {
        return prefsManager.logLevel
    }

    override fun onSetLogLevel(level: Int) {
        prefsManager.logLevel = level
        FileLogger.setLogLevel(level)
    }

    override fun onGetDevMode(): Boolean {
        return prefsManager.devMode
    }

    override fun onSetDevMode(enabled: Boolean) {
        prefsManager.devMode = enabled
    }

    override fun onGetChatHistory(): String {
        return prefsManager.chatHistory
    }

    override fun onSaveChatHistory(json: String) {
        prefsManager.chatHistory = json
    }

    override fun onGetMaxSteps(): Int {
        return prefsManager.maxSteps
    }

    override fun onSetMaxSteps(steps: Int) {
        prefsManager.maxSteps = steps
    }

    override fun onGetSomPreview(): String {
        return try {
            val externalCacheDir = externalCacheDir ?: return ""
            val screenshotPath = "${externalCacheDir.absolutePath}/screenshot_marked_latest.png"
            val file = java.io.File(screenshotPath)

            if (!file.exists()) {
                FileLogger.w(TAG, "SoM preview file not found: $screenshotPath")
                return ""
            }

            // Read the file and convert to Base64
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to read SoM preview: ${e.message}")
            ""
        }
    }

    override fun onGetExecutionMode(): String {
        return prefsManager.executionMode
    }

    override fun onSetExecutionMode(mode: String) {
        prefsManager.executionMode = mode
    }

    override fun onGetVisualStrategy(): String {
        return prefsManager.visualStrategy
    }

    override fun onSetVisualStrategy(strategy: String) {
        prefsManager.visualStrategy = strategy
    }

    override fun onGetFileLogContent(): String {
        val logPath = FileLogger.getLogFilePath() ?: "未知路径"
        val logContent = FileLogger.getLatestLogContent(300)
        return "📁 日志文件路径:\n$logPath\n\n📋 最近日志内容 (最后300行):\n\n$logContent"
    }

    override fun onGetTaskLists(): String {
        return prefsManager.customTaskLists
    }

    override fun onSaveTaskLists(json: String) {
        prefsManager.customTaskLists = json
    }

    override fun onStartCoordPicker(mode: String, x1: Int, y1: Int, x2: Int, y2: Int) {
        // Set up callback to return coordinates to frontend
        com.autoglm.app.service.CoordPickerService.onCoordinatesConfirmed =
                { pickedX1, pickedY1, pickedX2, pickedY2 ->
                    runOnUiThread {
                        val result =
                                if (pickedX2 != null && pickedY2 != null) {
                                    """{"x1":$pickedX1,"y1":$pickedY1,"x2":$pickedX2,"y2":$pickedY2}"""
                                } else {
                                    """{"x1":$pickedX1,"y1":$pickedY1}"""
                                }
                        webView.evaluateJavascript(
                                "window.onCoordPickerResult && window.onCoordPickerResult($result)",
                                null
                        )
                    }
                }
        com.autoglm.app.service.CoordPickerService.onCancelled = {
            runOnUiThread {
                webView.evaluateJavascript(
                        "window.onCoordPickerCancelled && window.onCoordPickerCancelled()",
                        null
                )
            }
        }

        // Start the overlay service
        val intent =
                android.content.Intent(this, com.autoglm.app.service.CoordPickerService::class.java)
                        .apply {
                            action =
                                    if (mode == "swipe") {
                                        com.autoglm.app.service.CoordPickerService
                                                .ACTION_START_SWIPE
                                    } else {
                                        com.autoglm.app.service.CoordPickerService
                                                .ACTION_START_SINGLE
                                    }
                            putExtra(com.autoglm.app.service.CoordPickerService.EXTRA_X1, x1)
                            putExtra(com.autoglm.app.service.CoordPickerService.EXTRA_Y1, y1)
                            putExtra(com.autoglm.app.service.CoordPickerService.EXTRA_X2, x2)
                            putExtra(com.autoglm.app.service.CoordPickerService.EXTRA_Y2, y2)
                        }
        startService(intent)
    }

    override fun onStopCoordPicker() {
        val intent =
                android.content.Intent(this, com.autoglm.app.service.CoordPickerService::class.java)
                        .apply { action = com.autoglm.app.service.CoordPickerService.ACTION_STOP }
        startService(intent)
    }

    override fun onExportToFile(filename: String, content: String) {
        runOnUiThread {
            try {
                // Use SAF to save file in Downloads
                val intent =
                        android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                                .apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TITLE, filename)
                                }
                pendingExportContent = content
                startActivityForResult(intent, REQUEST_CODE_EXPORT_FILE)
            } catch (e: Exception) {
                FileLogger.e(TAG, "Export failed: ${e.message}")
                pushToastToWeb("导出失败: ${e.message}")
            }
        }
    }

    override fun onImportFromFile() {
        runOnUiThread {
            try {
                val intent =
                        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(
                                    android.content.Intent.EXTRA_MIME_TYPES,
                                    arrayOf("text/plain", "text/*", "application/octet-stream")
                            )
                        }
                startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE)
            } catch (e: Exception) {
                FileLogger.e(TAG, "Import failed: ${e.message}")
                pushToastToWeb("导入失败: ${e.message}")
            }
        }
    }

    private fun initShizuku() {
        ShizukuHelper.init(
                onBinderStateChanged = { isAlive ->
                    FileLogger.i(TAG, "Shizuku binder state: $isAlive")
                    if (isAlive && ShizukuHelper.hasPermission()) {
                        ShizukuHelper.bindUserService()
                    }
                },
                onPermissionResult = { granted ->
                    FileLogger.i(TAG, "Shizuku permission result: $granted")
                    runOnUiThread {
                        if (granted) {
                            pushToastToWeb("Shizuku 权限已授予")
                            ShizukuHelper.bindUserService()
                        } else {
                            pushToastToWeb("Shizuku 权限被拒绝")
                            addLogAndSend(LogType.ERROR, "Shizuku 权限被拒绝")
                        }
                    }
                },
                onServiceBinding = { success ->
                    FileLogger.i(TAG, "Shizuku service binding result: $success")
                    runOnUiThread {
                        if (success) {
                            pushToastToWeb("Shizuku 服务已绑定")
                            addLogAndSend(LogType.INFO, "Shizuku 服务绑定成功，可以开始执行任务")
                        } else {
                            pushToastToWeb("Shizuku 服务绑定失败")
                            addLogAndSend(LogType.ERROR, "Shizuku 服务绑定失败，请检查 Shizuku 是否正确启动")
                        }
                    }
                }
        )

        // 如果 Shizuku 已可用且有权限，自动绑定服务
        if (ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission()) {
            ShizukuHelper.bindUserService()
        }
    }

    // --- Logic ---

    private fun executeTaskWithShizuku(task: String) {
        // 检查 Shizuku 是否就绪
        if (!ShizukuHelper.isAvailable()) {
            addLogAndSend(LogType.ERROR, "Shizuku 未启动或不可用，请先启动 Shizuku 应用")
            pushToastToWeb("请先启动 Shizuku 应用")
            return
        }

        if (!ShizukuHelper.hasPermission()) {
            addLogAndSend(LogType.ERROR, "Shizuku 权限未授予，请授权后重试")
            pushToastToWeb("请先授权 Shizuku 权限")
            ShizukuHelper.requestPermission()
            return
        }

        // 自动绑定 UserService
        if (!ShizukuHelper.isServiceBound()) {
            addLogAndSend(LogType.INFO, "正在绑定 Shizuku 服务...")
            ShizukuHelper.bindUserService()
            // 等待绑定完成
            pushToastToWeb("正在绑定 Shizuku 服务...")
            return
        }

        // 调试模式：以 # 开头的命令使用 ADB 执行
        if (task.startsWith("#")) {
            executeShizukuDebugCommand(task.substring(1).trim())
            return
        }

        if (!prefsManager.hasApiConfigs()) {
            pushToastToWeb("请先配置 API Key")
            sendToWeb("openSettings", "")
            return
        }

        // 记录命令历史
        prefsManager.addCommandToHistory(task)

        // 初始化 AIClient
        aiClient =
                AIClient(
                        apiConfigs = prefsManager.apiConfigs,
                        retryConfig = RetryConfig(maxRetries = prefsManager.maxRetries)
                )

        // 创建 ShizukuTaskExecutor
        shizukuTaskExecutor =
                ShizukuTaskExecutor(
                        context = this,
                        aiClient = aiClient!!,
                        prefsManager = prefsManager,
                        maxSteps = prefsManager.maxSteps
                )

        // 设置回调
        setupShizukuTaskCallbacks()
        startFloatingWindow()

        // 收集日志
        lifecycleScope.launch {
            shizukuTaskExecutor?.logs?.collect { logs ->
                currentLogs = logs.toMutableList()
                val json = gson.toJson(logs)
                sendToWeb("updateLogs", json)
            }
        }

        // 收集状态
        lifecycleScope.launch {
            shizukuTaskExecutor?.status?.collectLatest { status ->
                val statusStr =
                        when (status) {
                            is ExecutionStatus.Running -> "running"
                            is ExecutionStatus.Completed -> "completed"
                            is ExecutionStatus.Cancelled -> "cancelled"
                            is ExecutionStatus.Error -> "error"
                            else -> "idle"
                        }
                sendToWeb("updateStatus", statusStr)

                if (status is ExecutionStatus.Error || status is ExecutionStatus.Cancelled) {
                    stopFloatingWindow()
                }
            }
        }

        // 执行任务
        lifecycleScope.launch {
            val result = shizukuTaskExecutor?.executeTask(task)
            when (result) {
                is TaskResult.Success -> pushToastToWeb("任务完成")
                is TaskResult.Failed -> pushToastToWeb("任务失败: ${result.error}")
                else -> {}
            }
        }

        FileLogger.i(TAG, "Shizuku mode task started: $task")
    }

    /**
     * Shizuku 调试命令执行器 命令格式（以 # 开头）： #tap 500,500 - 点击坐标 #swipe 500,800,500,200 - 滑动 #type 你好 - 输入文字
     * #keyevent 66 - 发送按键（66=回车） #back - 返回 #home - 主屏幕 #launch 包名 - 启动应用（需要包名）
     */
    private fun executeShizukuDebugCommand(command: String) {
        val parts = command.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val args = parts.getOrNull(1) ?: ""

        FileLogger.i(TAG, "Shizuku debug command: $cmd, args: $args")

        val adbCommand =
                when (cmd) {
                    "tap" -> {
                        val coords = args.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (coords.size >= 2) {
                            "input tap ${coords[0]} ${coords[1]}"
                        } else {
                            pushToastToWeb("格式错误，使用: #tap x,y")
                            return
                        }
                    }
                    "swipe" -> {
                        val coords = args.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (coords.size >= 4) {
                            val duration = coords.getOrElse(4) { 300 }
                            "input swipe ${coords[0]} ${coords[1]} ${coords[2]} ${coords[3]} $duration"
                        } else {
                            pushToastToWeb("格式错误，使用: #swipe x1,y1,x2,y2[,duration]")
                            return
                        }
                    }
                    "type", "input" -> {
                        if (args.isNotEmpty()) {
                            "input text \"${args.replace("\"", "\\\"")}\""
                        } else {
                            pushToastToWeb("请输入要输入的文字")
                            return
                        }
                    }
                    "keyevent", "key" -> {
                        val keycode = args.toIntOrNull()
                        if (keycode != null) {
                            "input keyevent $keycode"
                        } else {
                            pushToastToWeb("格式错误，使用: #keyevent 键码")
                            return
                        }
                    }
                    "back" -> "input keyevent 4"
                    "home" -> "input keyevent 3"
                    "enter" -> "input keyevent 66"
                    "launch" -> {
                        if (args.isNotEmpty()) {
                            // 尝试启动应用
                            "am start -n ${args}/.MainActivity || am start $(pm resolve-activity --brief $args | tail -n 1)"
                        } else {
                            pushToastToWeb("请输入包名")
                            return
                        }
                    }
                    "shell" -> {
                        if (args.isNotEmpty()) {
                            args // 直接执行 shell 命令
                        } else {
                            pushToastToWeb("请输入命令")
                            return
                        }
                    }
                    else -> {
                        pushToastToWeb("未知命令: $cmd")
                        return
                    }
                }

        // 执行 ADB 命令
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = ShizukuHelper.executeCommand(adbCommand)
                withContext(Dispatchers.Main) {
                    if (result.isNullOrEmpty() || !result.contains("Error")) {
                        pushToastToWeb("命令执行成功")
                        FileLogger.i(TAG, "Shizuku command success: $adbCommand")
                    } else {
                        pushToastToWeb("命令输出: $result")
                        FileLogger.w(TAG, "Shizuku command result: $result")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pushToastToWeb("命令执行失败: ${e.message}")
                    FileLogger.e(TAG, "Shizuku command error: ${e.message}")
                }
            }
        }
    }

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

        aiClient =
                AIClient(
                        apiConfigs = prefsManager.apiConfigs,
                        retryConfig = RetryConfig(maxRetries = prefsManager.maxRetries)
                )

        // 记录命令历史（在任何处理之前）
        prefsManager.addCommandToHistory(task)

        // 调试模式：以 # 开头的命令直接执行，不经过 AI
        if (task.startsWith("#")) {
            executeDebugCommand(task.substring(1).trim(), service)
            return
        }

        taskExecutor =
                TaskExecutor(
                        context = this,
                        aiClient = aiClient!!,
                        accessibilityService = service,
                        maxSteps = prefsManager.maxSteps
                )

        setupTaskCallbacks()
        startFloatingWindow()

        // Logs
        lifecycleScope.launch {
            taskExecutor?.logs?.collect { logs ->
                currentLogs = logs.toMutableList()
                val json = gson.toJson(logs)
                sendToWeb("updateLogs", json)
            }
        }

        // Status
        lifecycleScope.launch {
            taskExecutor?.status?.collectLatest { status ->
                val statusStr =
                        when (status) {
                            is ExecutionStatus.Running -> "running"
                            is ExecutionStatus.Completed -> "completed"
                            is ExecutionStatus.Cancelled -> "cancelled"
                            is ExecutionStatus.Error -> "error"
                            else -> "idle"
                        }
                sendToWeb("updateStatus", statusStr)

                if (status is ExecutionStatus.Error || status is ExecutionStatus.Cancelled) {
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
     * 调试命令执行器 命令格式（以 # 开头）： #tap 500,500 - 点击坐标 #swipe 500,800,500,200 - 滑动 #type 你好 - 输入文字 #enter
     * - 按确认键 #back - 返回 #home - 主屏幕 #longpress 500,500 - 长按 #launch 设置 - 启动应用 #screenshot - 截图测试
     */
    private fun executeDebugCommand(command: String, service: AutoGLMAccessibilityService) {
        lifecycleScope.launch {
            val parts = command.split(" ", limit = 2)
            val cmd = parts[0].lowercase()
            val args = parts.getOrNull(1) ?: ""

            // 添加调试开始日志
            val startEntry =
                    LogEntry(System.currentTimeMillis(), LogType.INFO, "執行調試命令: $cmd args: $args")
            currentLogs.add(startEntry)
            sendToWeb("updateLogs", gson.toJson(currentLogs))

            val result =
                    when (cmd) {
                        "tap" -> {
                            val coords = args.split(",").map { it.trim().toIntOrNull() ?: 0 }
                            if (coords.size >= 2) {
                                currentLogs.add(
                                        LogEntry(
                                                System.currentTimeMillis(),
                                                LogType.INFO,
                                                "点击: (${coords[0]}, ${coords[1]})"
                                        )
                                )
                                sendToWeb("updateLogs", gson.toJson(currentLogs))
                                service.performTap(coords[0], coords[1])
                            } else false
                        }
                        "swipe" -> {
                            val coords = args.split(",").map { it.trim().toIntOrNull() ?: 0 }
                            if (coords.size >= 4) {
                                currentLogs.add(
                                        LogEntry(
                                                System.currentTimeMillis(),
                                                LogType.INFO,
                                                "滑动: (${coords[0]}, ${coords[1]}) -> (${coords[2]}, ${coords[3]})"
                                        )
                                )
                                sendToWeb("updateLogs", gson.toJson(currentLogs))
                                service.performSwipe(
                                        coords[0],
                                        coords[1],
                                        coords[2],
                                        coords[3],
                                        300
                                )
                            } else false
                        }
                        "type" -> {
                            currentLogs.add(
                                    LogEntry(System.currentTimeMillis(), LogType.INFO, "输入: $args")
                            )
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            service.performInput(args)
                        }
                        "enter" -> {
                            currentLogs.add(
                                    LogEntry(System.currentTimeMillis(), LogType.INFO, "执行: 确认/回车")
                            )
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            service.performEnter()
                        }
                        "back" -> {
                            currentLogs.add(
                                    LogEntry(System.currentTimeMillis(), LogType.INFO, "执行: 返回")
                            )
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            service.performBack()
                        }
                        "home" -> {
                            currentLogs.add(
                                    LogEntry(System.currentTimeMillis(), LogType.INFO, "执行: 回主屏幕")
                            )
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            service.performHome()
                        }
                        "longpress" -> {
                            val coords = args.split(",").map { it.trim().toIntOrNull() ?: 0 }
                            if (coords.size >= 2) {
                                currentLogs.add(
                                        LogEntry(
                                                System.currentTimeMillis(),
                                                LogType.INFO,
                                                "长按: (${coords[0]}, ${coords[1]})"
                                        )
                                )
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
                                            launchIntent =
                                                    pm.getLaunchIntentForPackage(app.packageName)
                                            // 仅当能获取到intent才视为成功
                                            if (launchIntent != null) {
                                                currentLogs.add(
                                                        LogEntry(
                                                                System.currentTimeMillis(),
                                                                LogType.INFO,
                                                                "找到精确匹配应用: $label (${app.packageName})"
                                                        )
                                                )
                                                sendToWeb("updateLogs", gson.toJson(currentLogs))
                                                break
                                            }
                                        }

                                        // 2. 收集相似应用
                                        if (label.contains(args, ignoreCase = true) ||
                                                        args.contains(label, ignoreCase = true)
                                        ) {
                                            if (!fuzzyMatches.contains(label)) {
                                                fuzzyMatches.add(label)
                                            }
                                        }
                                    }

                                    // 如果没找到launchIntent但有模糊匹配
                                    if (launchIntent == null && fuzzyMatches.isNotEmpty()) {
                                        fuzzySuggestions =
                                                fuzzyMatches
                                                        .sortedBy {
                                                            Math.abs(it.length - args.length)
                                                        }
                                                        .take(3)
                                                        .joinToString(", ")
                                    }
                                }

                                // 根据结果执行
                                if (launchIntent != null) {
                                    launchIntent.addFlags(
                                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    )
                                    currentLogs.add(
                                            LogEntry(
                                                    System.currentTimeMillis(),
                                                    LogType.INFO,
                                                    "启动应用: $args"
                                            )
                                    )
                                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                                    startActivity(launchIntent)
                                    true
                                } else if (fuzzySuggestions != null) {
                                    currentLogs.add(
                                            LogEntry(
                                                    System.currentTimeMillis(),
                                                    LogType.WARNING,
                                                    "未找到精确匹配。相似应用: $fuzzySuggestions"
                                            )
                                    )
                                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                                    false
                                } else {
                                    currentLogs.add(
                                            LogEntry(
                                                    System.currentTimeMillis(),
                                                    LogType.WARNING,
                                                    "未找到应用: $args"
                                            )
                                    )
                                    sendToWeb("updateLogs", gson.toJson(currentLogs))
                                    false
                                }
                            } catch (e: Exception) {
                                FileLogger.e("MainActivity", "Launch failed: ${e.message}")
                                currentLogs.add(
                                        LogEntry(
                                                System.currentTimeMillis(),
                                                LogType.ERROR,
                                                "启动失败: ${e.message}"
                                        )
                                )
                                sendToWeb("updateLogs", gson.toJson(currentLogs))
                                false
                            }
                        }
                        "screenshot" -> {
                            currentLogs.add(
                                    LogEntry(System.currentTimeMillis(), LogType.INFO, "正在截图...")
                            )
                            sendToWeb("updateLogs", gson.toJson(currentLogs))
                            val bitmap = service.takeScreenshotBitmap()
                            bitmap != null
                        }
                        "help" -> {
                            pushToastToWeb(
                                    "命令: tap, swipe, type, enter, back, home, longpress, launch, screenshot"
                            )
                            true
                        }
                        else -> {
                            pushToastToWeb("未知命令: $cmd，输入 #help 查看帮助")
                            false
                        }
                    }

            // 添加结果日志
            val resultMsg = if (result) "✓ 执行成功" else "✗ 执行失败"
            val resultEntry =
                    LogEntry(
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

        FloatingWindowService.onStopTaskListener = { onStopTask() }

        FloatingWindowService.onPauseTaskListener = { onTogglePause() }

        FloatingWindowService.onCloseWindowListener = {
            runOnUiThread {
                taskExecutor?.onStatusUpdate = null
                taskExecutor?.onNewLog = null
                taskExecutor?.onPauseStateChanged = null
                taskExecutor?.stop()

                shizukuTaskExecutor?.onStatusUpdate = null
                shizukuTaskExecutor?.onNewLog = null
                shizukuTaskExecutor?.onPauseStateChanged = null
                shizukuTaskExecutor?.stop()
            }
        }

        // 悬浮窗新指令回调
        FloatingWindowService.onNewInstructionListener = { newInstruction ->
            handleNewInstruction(newInstruction, isShizukuMode = false)
        }

        // 日志转发到悬浮窗
        taskExecutor?.onNewLog = { entry -> sendLogToFloatingWindow(entry.message) }
    }

    private fun setupShizukuTaskCallbacks() {
        shizukuTaskExecutor?.onStatusUpdate = { status, action ->
            updateFloatingWindowStatus(status, action)
        }

        shizukuTaskExecutor?.onPauseStateChanged = { isPaused ->
            updateFloatingWindowPauseState(isPaused)
            sendToWeb("updatePauseState", isPaused.toString())
        }

        shizukuTaskExecutor?.onHideFloatingWindow = { hideFloatingWindow() }
        shizukuTaskExecutor?.onShowFloatingWindow = { showFloatingWindow() }

        FloatingWindowService.onStopTaskListener = { onStopTask() }
        FloatingWindowService.onPauseTaskListener = { onTogglePause() }
        FloatingWindowService.onCloseWindowListener = {
            runOnUiThread {
                taskExecutor?.onStatusUpdate = null
                taskExecutor?.onNewLog = null
                taskExecutor?.onPauseStateChanged = null
                taskExecutor?.stop()

                shizukuTaskExecutor?.onStatusUpdate = null
                shizukuTaskExecutor?.onNewLog = null
                shizukuTaskExecutor?.onPauseStateChanged = null
                shizukuTaskExecutor?.stop()
            }
        }

        // 悬浮窗新指令回调
        FloatingWindowService.onNewInstructionListener = { newInstruction ->
            handleNewInstruction(newInstruction, isShizukuMode = true)
        }

        // 日志转发到悬浮窗
        shizukuTaskExecutor?.onNewLog = { entry -> sendLogToFloatingWindow(entry.message) }
    }

    private fun sendLogToFloatingWindow(message: String) {
        val intent =
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_ADD_LOG
                    putExtra(FloatingWindowService.EXTRA_LOG_MESSAGE, message)
                }
        startService(intent)
    }

    private fun startFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            pushToastToWeb("需要悬浮窗权限")
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                    )
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

    /** 处理悬浮窗输入的新指令 */
    private fun handleNewInstruction(newInstruction: String, isShizukuMode: Boolean) {
        android.util.Log.d("MainActivity", "新指令: $newInstruction (Shizuku=$isShizukuMode)")

        lifecycleScope.launch {
            // 获取历史摘要
            val historySummary =
                    if (isShizukuMode) {
                        shizukuTaskExecutor?.getHistorySummary() ?: ""
                    } else {
                        taskExecutor?.getHistorySummary() ?: ""
                    }

            // 构建带历史背景的新任务
            val taskWithContext =
                    if (historySummary.isNotEmpty()) {
                        """
【新任务】$newInstruction

【背景】之前执行过: $historySummary
【重要】以上为背景信息，新任务优先级更高
                """.trimIndent()
                    } else {
                        newInstruction
                    }

            // 重新开始任务
            if (isShizukuMode) {
                shizukuTaskExecutor?.stop()
                shizukuTaskExecutor?.executeTask(taskWithContext)
            } else {
                taskExecutor?.stop()
                taskExecutor?.executeTask(taskWithContext)
            }
        }
    }

    private fun hideFloatingWindow() {
        startService(
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_HIDE
                }
        )
    }

    private fun showFloatingWindow() {
        startService(
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_SHOW
                }
        )
    }

    private fun updateFloatingWindowStatus(status: String, action: String) {
        val intent =
                Intent(this, FloatingWindowService::class.java).apply {
                    this.action = FloatingWindowService.ACTION_UPDATE_STATUS
                    putExtra(FloatingWindowService.EXTRA_STATUS, status)
                    putExtra(FloatingWindowService.EXTRA_ACTION, action)
                }
        startService(intent)
    }

    private fun updateFloatingWindowPauseState(isPaused: Boolean) {
        val intent =
                Intent(this, FloatingWindowService::class.java).apply {
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
            // 需要正确转义 JSON 数据中的特殊字符
            val escapedData =
                    data.replace("\\", "\\\\") // 先转义反斜杠
                            .replace("'", "\\'") // 再转义单引号
                            .replace("\n", "\\n") // 转义换行符
                            .replace("\r", "\\r") // 转义回车符
            val js =
                    """
                if (typeof window.$function === 'function') {
                    window.$function('$escapedData');
                } else {
                    console.log('Bridge function not ready: $function');
                }
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    /** 添加日志并发送到 WebView */
    private fun addLogAndSend(type: LogType, message: String) {
        val logEntry =
                LogEntry(timestamp = System.currentTimeMillis(), type = type, message = message)
        currentLogs.add(logEntry)
        val json = gson.toJson(currentLogs)
        sendToWeb("updateLogs", json)
    }
}
