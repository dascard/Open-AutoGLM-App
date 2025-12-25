package com.autoglm.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.autoglm.app.shizuku.ShizukuHelper
import com.autoglm.app.util.FileLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Shizuku 任务执行器 使用 ADB 命令代替无障碍服务执行操作 */
class ShizukuTaskExecutor(
        private val context: Context,
        private val aiClient: AIClient,
        private val maxSteps: Int = 50
) {
    companion object {
        private const val TAG = "ShizukuTaskExecutor"
        private const val STEP_DELAY = 500L

        // 别名对照表：将常用别名映射到标准应用名（用于在已安装应用中精确匹配）
        val APP_ALIASES =
                mapOf(
                        // 哔哩哔哩的别名
                        "b站" to "哔哩哔哩",
                        "bilibili" to "哔哩哔哩",
                        "bili" to "哔哩哔哩",

                        // 微信的别名
                        "wechat" to "微信",
                        "weixin" to "微信",

                        // 支付宝的别名
                        "alipay" to "支付宝",
                        "zhifubao" to "支付宝",

                        // 抖音的别名
                        "tiktok" to "抖音",
                        "douyin" to "抖音",

                        // 淘宝的别名
                        "taobao" to "淘宝",

                        // 京东的别名
                        "jd" to "京东",
                        "jingdong" to "京东",

                        // QQ的别名
                        "腾讯qq" to "QQ",

                        // Chrome的别名
                        "chrome" to "Chrome",
                        "谷歌浏览器" to "Chrome",
                        "google浏览器" to "Chrome",

                        // 设置的别名
                        "settings" to "设置",
                        "系统设置" to "设置"
                )
    }

    private var executionJob: Job? = null
    private val executedActions = mutableListOf<String>()

    private val _status = MutableStateFlow<ExecutionStatus>(ExecutionStatus.Idle)
    val status: StateFlow<ExecutionStatus> = _status

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    @Volatile private var isPaused = false

    private val detailedLogs = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    var onStatusUpdate: ((status: String, action: String) -> Unit)? = null
    var onPauseStateChanged: ((isPaused: Boolean) -> Unit)? = null
    var onHideFloatingWindow: (() -> Unit)? = null
    var onShowFloatingWindow: (() -> Unit)? = null

    // 新增日志回调
    var onNewLog: ((LogEntry) -> Unit)? = null

    fun getFullLogText(): String = detailedLogs.toString()

    fun pause() {
        if (_status.value is ExecutionStatus.Running && !isPaused) {
            isPaused = true
            addLog(LogType.INFO, "⏸️ 任务已暂停")
            onPauseStateChanged?.invoke(true)
            onStatusUpdate?.invoke("已暂停", "点击继续恢复执行")
        }
    }

    fun resume() {
        if (_status.value is ExecutionStatus.Running && isPaused) {
            isPaused = false
            addLog(LogType.INFO, "▶️ 任务继续执行")
            onPauseStateChanged?.invoke(false)
            onStatusUpdate?.invoke("继续执行中...", "")
        }
    }

    fun isPaused(): Boolean = isPaused

    /** 获取设备上安装的所有应用包名 */
    fun getInstalledPackages(): List<String> {
        val result = ShizukuHelper.executeCommand("pm list packages")
        return result?.lines()?.filter { it.startsWith("package:") }?.map {
            it.removePrefix("package:").trim()
        }
                ?: emptyList()
    }

    /** 执行任务 */
    suspend fun executeTask(task: String): TaskResult =
            withContext(Dispatchers.IO) {
                if (_status.value is ExecutionStatus.Running) {
                    return@withContext TaskResult.Failed("任务正在执行中")
                }

                // 检查 Shizuku 状态
                if (!ShizukuHelper.isAvailable()) {
                    return@withContext TaskResult.Failed("Shizuku 服务不可用")
                }
                if (!ShizukuHelper.isServiceBound()) {
                    return@withContext TaskResult.Failed("Shizuku UserService 未绑定")
                }

                isPaused = false
                executedActions.clear()
                _logs.value = emptyList()
                detailedLogs.clear()
                _status.value = ExecutionStatus.Running

                addLog(LogType.INFO, "===== Shizuku 任务开始 =====")
                addLog(LogType.INFO, "开始执行任务: $task")
                addLog(LogType.INFO, "模式: ADB (Shizuku)")
                addLog(
                        LogType.INFO,
                        "时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
                )

                try {
                    executionJob = coroutineScope { launch { executeLoop(task) } }
                    executionJob?.join()

                    if (_status.value is ExecutionStatus.Cancelled) {
                        TaskResult.Cancelled
                    } else {
                        _status.value = ExecutionStatus.Completed
                        addLog(LogType.INFO, "===== 任务完成 =====")
                        TaskResult.Success("任务执行完成")
                    }
                } catch (e: CancellationException) {
                    _status.value = ExecutionStatus.Cancelled
                    addLog(LogType.INFO, "任务已取消")
                    TaskResult.Cancelled
                } catch (e: Exception) {
                    _status.value = ExecutionStatus.Error(e.message ?: "未知错误")
                    addLog(LogType.ERROR, "===== 执行失败 =====")
                    addLog(LogType.ERROR, "错误: ${e.message}")
                    TaskResult.Failed(e.message ?: "未知错误")
                }
            }

    private suspend fun executeLoop(task: String) {
        var stepCount = 0
        var consecutiveFatalErrors = 0

        // 先回到主屏幕
        addLog(LogType.INFO, "正在回到主屏幕...")
        executeAdbCommand("input keyevent 3")
        delay(1000)
        addLog(LogType.INFO, "已回到主屏幕，开始执行任务")

        while (stepCount < maxSteps && _status.value is ExecutionStatus.Running) {
            while (isPaused && _status.value is ExecutionStatus.Running) {
                delay(200)
            }

            stepCount++
            addLog(LogType.INFO, "")
            addLog(LogType.INFO, "--- 步骤 $stepCount/$maxSteps ---")

            // 1. 截图并标记 UI 元素
            addLog(LogType.INFO, "正在获取 UI 层级并截图...")
            onStatusUpdate?.invoke("步骤 $stepCount: 分析界面", "")

            val markedResult =
                    getMarkedScreenshot(
                            onBeforeScreenshot = {
                                // 截图前的回调：隐藏悬浮窗
                                onHideFloatingWindow?.invoke()
                            },
                            onAfterScreenshot = {
                                // 截图后立即显示悬浮窗（标注在悬浮窗可见时进行）
                                onShowFloatingWindow?.invoke()
                            }
                    )

            if (markedResult == null) {
                addLog(LogType.ERROR, "截图失败！")
                throw Exception("Shizuku 截图失败")
            }
            val screenshot = markedResult.first
            addLog(LogType.INFO, "发现 ${markedResult.second.size} 个可点击元素")
            addLog(LogType.INFO, "截图成功: ${screenshot.width}x${screenshot.height}")

            // 2. AI 分析（使用 Shizuku 模式提示词）
            addLog(LogType.INFO, "正在调用 AI 分析...")
            onStatusUpdate?.invoke("步骤 $stepCount: AI 思考中...", "分析屏幕内容")

            val response =
                    try {
                        val startTime = System.currentTimeMillis()
                        val result =
                                aiClient.analyzeScreenAndPlanForShizuku(
                                        screenshot,
                                        task,
                                        executedActions,
                                        markedResult.second // 传递 UI 元素列表
                                )
                        val duration = System.currentTimeMillis() - startTime
                        addLog(LogType.INFO, "AI 响应耗时: ${duration}ms")
                        consecutiveFatalErrors = 0
                        result
                    } catch (e: Exception) {
                        addLog(LogType.WARNING, "AI 分析出错: ${e.message?.take(100)}")

                        if (aiClient.isFatalError(e)) {
                            consecutiveFatalErrors++
                            if (consecutiveFatalErrors >= 3) {
                                addLog(LogType.ERROR, "❌ 连续3次致命错误，终止任务")
                                break
                            }
                        }

                        // 排除配置类错误
                        val errorMessage = e.message ?: ""
                        if (!errorMessage.contains("API 配置") && !errorMessage.contains("设置中添加")) {
                            executedActions.add("【上次操作失败】${errorMessage.take(50)}")
                        } else {
                            // 系统配置错误，记录日志但不加入 AI 上下文，并直接终止任务
                            addLog(LogType.ERROR, "系统配置错误: $errorMessage")
                            onStatusUpdate?.invoke("配置错误 ⚠️", "请检查设置")
                            break
                        }
                        delay(500)
                        continue
                    }

            val action = response.action
            val allActions = response.getAllActions()

            if (_status.value is ExecutionStatus.Cancelled) {
                addLog(LogType.INFO, "任务已取消")
                break
            }

            addLog(LogType.ACTION, "AI 返回 ${allActions.size} 个动作")

            response.thinking?.let { think ->
                addLog(LogType.INFO, "💭 AI 思考: $think")
                // 发送思考摘要到悬浮窗 (取前100字符)
                val thinkingSummary = if (think.length > 100) think.take(100) + "..." else think
                onStatusUpdate?.invoke("💭 AI思考", thinkingSummary)
            }

            // 3. 检查是否完成
            if (action is Action.Done) {
                addLog(LogType.INFO, "✅ AI 判断任务已完成")
                onStatusUpdate?.invoke("任务完成 ✅", action.message)
                break
            }

            if (action is Action.AskUser) {
                addLog(LogType.WARNING, "⚠️ AI 请求用户介入: ${action.reason}")
                isPaused = true
                onPauseStateChanged?.invoke(true)
                onStatusUpdate?.invoke("需要用户介入 ⚠️", action.reason)
                executedActions.add("【等待用户】${action.reason}")

                while (isPaused && _status.value is ExecutionStatus.Running) {
                    delay(200)
                }
                continue
            }

            // 4. 执行动作
            for ((index, actionItem) in allActions.withIndex()) {
                // 每个动作前检查暂停状态
                while (isPaused && _status.value is ExecutionStatus.Running) {
                    delay(200)
                }
                if (_status.value !is ExecutionStatus.Running) break

                if (actionItem is Action.Done || actionItem is Action.AskUser) continue

                addLog(LogType.INFO, "执行动作 ${index + 1}/${allActions.size}: $actionItem")
                onStatusUpdate?.invoke("执行中", "$actionItem")

                // 传递隐藏/显示回调给 executeAction
                val resultMsg = executeActionWithHide(actionItem)

                if (resultMsg.isEmpty()) {
                    addLog(LogType.INFO, "✓ 动作执行成功")
                    executedActions.add(actionItem.toString())
                    onStatusUpdate?.invoke("执行成功 ✓", "$actionItem")
                } else {
                    addLog(LogType.WARNING, "✗ 动作执行失败: $resultMsg")
                    executedActions.add("【操作失败】$resultMsg")
                    onStatusUpdate?.invoke("执行失败 ✗", resultMsg)
                }

                if (index < allActions.size - 1) {
                    delay(300)
                }
            }

            delay(STEP_DELAY)
        }

        if (stepCount >= maxSteps) {
            addLog(LogType.WARNING, "⚠️ 达到最大步数限制")
        }
    }

    /** 通过 Shizuku 截图 - 使用外部缓存目录 */
    private suspend fun takeScreenshot(): Bitmap? =
            withContext(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()

                    // 使用应用的外部缓存目录（在 /sdcard/Android/data/<package>/cache/）
                    // 这个目录对 shell 可写，对应用也可读
                    val externalCacheDir = context.externalCacheDir
                    if (externalCacheDir == null) {
                        Log.e(TAG, "External cache dir is null, fallback to /sdcard/")
                        // 降级到 /sdcard/Pictures/
                        return@withContext takeScreenshotFallback(timestamp)
                    }

                    val screenshotPath =
                            "${externalCacheDir.absolutePath}/screenshot_$timestamp.png"
                    Log.d(TAG, "Screenshot path: $screenshotPath")

                    // 1. 使用 screencap 截图
                    val captureResult = ShizukuHelper.executeCommand("screencap -p $screenshotPath")
                    Log.d(TAG, "screencap result: $captureResult")

                    if (captureResult?.contains("Error") == true ||
                                    captureResult?.contains("Permission denied") == true
                    ) {
                        Log.e(TAG, "screencap failed: $captureResult")
                        return@withContext takeScreenshotFallback(timestamp)
                    }

                    // 2. 设置权限
                    ShizukuHelper.executeCommand("chmod 666 $screenshotPath")

                    // 3. 等待文件写入完成
                    delay(100)

                    // 4. 从应用端读取文件
                    val screenshotFile = java.io.File(screenshotPath)
                    Log.d(
                            TAG,
                            "File exists: ${screenshotFile.exists()}, canRead: ${screenshotFile.canRead()}"
                    )

                    if (!screenshotFile.exists()) {
                        Log.e(TAG, "Screenshot file not found: $screenshotPath")
                        return@withContext takeScreenshotFallback(timestamp)
                    }

                    Log.d(TAG, "Screenshot file size: ${screenshotFile.length()} bytes")

                    val bitmap = BitmapFactory.decodeFile(screenshotPath)
                    Log.d(TAG, "Bitmap decoded: ${bitmap != null}")

                    // 5. 清理
                    screenshotFile.delete()

                    bitmap
                } catch (e: Exception) {
                    Log.e(TAG, "Screenshot failed", e)
                    addLog(LogType.ERROR, "截图异常: ${e.message}")
                    null
                }
            }

    /** 备用截图方法：使用 /sdcard/Pictures/ 目录 */
    private suspend fun takeScreenshotFallback(timestamp: Long): Bitmap? {
        val screenshotPath = "/sdcard/Pictures/screenshot_autoglm_$timestamp.png"
        Log.d(TAG, "Fallback screenshot path: $screenshotPath")

        // 确保目录存在
        ShizukuHelper.executeCommand("mkdir -p /sdcard/Pictures")

        // 截图
        val captureResult = ShizukuHelper.executeCommand("screencap -p $screenshotPath")
        Log.d(TAG, "Fallback screencap result: $captureResult")

        // 设置权限
        ShizukuHelper.executeCommand("chmod 666 $screenshotPath")

        delay(100)

        val screenshotFile = java.io.File(screenshotPath)
        if (!screenshotFile.exists()) {
            Log.e(TAG, "Fallback screenshot also failed")
            return null
        }

        val bitmap = BitmapFactory.decodeFile(screenshotPath)
        screenshotFile.delete()
        ShizukuHelper.executeCommand("rm -f $screenshotPath")

        return bitmap
    }

    /** 执行动作（带自动隐藏悬浮窗） */
    private suspend fun executeActionWithHide(action: Action): String {
        // 判断是否需要隐藏悬浮窗（点击和滑动类操作）
        val needHide =
                action is Action.TapMark ||
                        action is Action.Tap ||
                        action is Action.Swipe ||
                        action is Action.LongPress

        return withContext(Dispatchers.IO) {
            when (action) {
                is Action.TapMark -> {
                    addLog(LogType.INFO, "执行: Mark 点击 [${action.markId}]")
                    clickByMarkWithHide(action.markId, needHide)
                }
                is Action.Tap -> {
                    addLog(LogType.INFO, "执行: ADB 点击 (${action.x}, ${action.y})")
                    executeAdbWithHide("input tap ${action.x} ${action.y}", needHide)
                }
                is Action.Swipe -> {
                    addLog(LogType.INFO, "执行: ADB 滑动")
                    executeAdbWithHide(
                            "input swipe ${action.x1} ${action.y1} ${action.x2} ${action.y2} ${action.duration}",
                            needHide
                    )
                }
                is Action.LongPress -> {
                    addLog(LogType.INFO, "执行: ADB 长按")
                    executeAdbWithHide(
                            "input swipe ${action.x} ${action.y} ${action.x} ${action.y} ${action.duration}",
                            needHide
                    )
                }
                is Action.Input -> {
                    addLog(LogType.INFO, "执行: ADB 输入 \"${action.text}\"")
                    inputTextViaClipboard(action.text)
                }
                is Action.Back -> {
                    addLog(LogType.INFO, "执行: ADB 返回")
                    executeAdbCommand("input keyevent 4")
                }
                is Action.Home -> {
                    addLog(LogType.INFO, "执行: ADB 回到主屏幕")
                    executeAdbCommand("input keyevent 3")
                }
                is Action.Wait -> {
                    addLog(LogType.INFO, "执行: 等待 ${action.milliseconds}ms")
                    delay(action.milliseconds)
                    ""
                }
                is Action.Launch -> {
                    addLog(LogType.INFO, "执行: ADB 启动应用")
                    launchAppByAdb(action.appName)
                }
                is Action.Enter -> {
                    addLog(LogType.INFO, "执行: ADB 回车")
                    executeAdbCommand("input keyevent 66")
                }
                is Action.Done -> ""
                is Action.AskUser -> ""
            }
        }
    }

    /** 执行 ADB 命令并自动隐藏/显示悬浮窗 */
    private suspend fun executeAdbWithHide(command: String, hide: Boolean): String {
        if (hide) {
            withContext(Dispatchers.Main) { onHideFloatingWindow?.invoke() }
            delay(20)
        }
        val result = executeAdbCommand(command)
        if (hide) {
            withContext(Dispatchers.Main) { onShowFloatingWindow?.invoke() }
        }
        return result
    }

    /** 通过 mark 点击并自动隐藏/显示悬浮窗 */
    private suspend fun clickByMarkWithHide(markId: Int, hide: Boolean): String {
        val element = currentUIElements.find { it.id == markId }
        return if (element != null) {
            val x = element.centerX
            val y = element.centerY
            addLog(
                    LogType.INFO,
                    "Mark[$markId] 点击: ($x, $y) ${element.text ?: element.description ?: ""}"
            )
            executeAdbWithHide("input tap $x $y", hide)
        } else {
            addLog(LogType.WARNING, "未找到 mark=$markId 的元素")
            "错误: 未找到编号 $markId 的元素"
        }
    }

    /** 执行动作 */
    private suspend fun executeAction(action: Action): String =
            withContext(Dispatchers.IO) {
                when (action) {
                    is Action.TapMark -> {
                        addLog(LogType.INFO, "执行: Mark 点击 [${action.markId}]")
                        clickByMark(action.markId)
                    }
                    is Action.Tap -> {
                        addLog(LogType.INFO, "执行: ADB 点击 (${action.x}, ${action.y})")
                        executeAdbCommand("input tap ${action.x} ${action.y}")
                    }
                    is Action.Swipe -> {
                        addLog(LogType.INFO, "执行: ADB 滑动")
                        executeAdbCommand(
                                "input swipe ${action.x1} ${action.y1} ${action.x2} ${action.y2} ${action.duration}"
                        )
                    }
                    is Action.Input -> {
                        addLog(LogType.INFO, "执行: ADB 输入 \"${action.text}\"")
                        // 使用剪贴板方式输入（支持中文）
                        inputTextViaClipboard(action.text)
                    }
                    is Action.Back -> {
                        addLog(LogType.INFO, "执行: ADB 返回")
                        executeAdbCommand("input keyevent 4")
                    }
                    is Action.Home -> {
                        addLog(LogType.INFO, "执行: ADB 回到主屏幕")
                        executeAdbCommand("input keyevent 3")
                    }
                    is Action.Wait -> {
                        addLog(LogType.INFO, "执行: 等待 ${action.milliseconds}ms")
                        delay(action.milliseconds)
                        ""
                    }
                    is Action.Launch -> {
                        addLog(LogType.INFO, "执行: ADB 启动应用")
                        launchAppByAdb(action.appName)
                    }
                    is Action.Enter -> {
                        addLog(LogType.INFO, "执行: ADB 回车")
                        executeAdbCommand("input keyevent 66")
                    }
                    is Action.LongPress -> {
                        addLog(LogType.INFO, "执行: ADB 长按")
                        // ADB 长按使用 swipe 起点=终点
                        executeAdbCommand(
                                "input swipe ${action.x} ${action.y} ${action.x} ${action.y} ${action.duration}"
                        )
                    }
                    is Action.Done -> ""
                    is Action.AskUser -> ""
                }
            }

    /** 执行 ADB 命令 */
    private fun executeAdbCommand(command: String): String {
        return try {
            val result = ShizukuHelper.executeCommand(command)
            result ?: ""
        } catch (e: Exception) {
            "命令执行失败: ${e.message}"
        }
    }

    /** 通过 ADB 启动应用 */
    private fun launchAppByAdb(appNameOrPackage: String): String {
        val lowerName = appNameOrPackage.lowercase()

        // 1. 检查别名表，转换为标准名
        val standardName = APP_ALIASES[lowerName] ?: appNameOrPackage
        Log.d(TAG, "Launch: input='$appNameOrPackage', standardName='$standardName'")

        // 2. 获取已安装应用列表（应用名 -> 包名）
        val installedApps = getInstalledAppsMap()

        // 3. 精确匹配应用名（忽略大小写）
        val packageName =
                installedApps.entries.find { it.key.equals(standardName, ignoreCase = true) }?.value
                        ?: appNameOrPackage // 如果匹配失败，尝试直接作为包名使用

        Log.d(TAG, "Launch: resolved packageName='$packageName'")

        // 4. 使用 monkey 启动
        val result =
                ShizukuHelper.executeCommand(
                        "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
                )

        if (result?.contains("No activities found") == true) {
            // 提供应用名建议
            val suggestions =
                    installedApps
                            .keys
                            .filter { it.contains(standardName, ignoreCase = true) }
                            .take(3)

            return if (suggestions.isNotEmpty()) {
                "未找到应用'$appNameOrPackage'。相似应用: ${suggestions.joinToString(", ")}"
            } else {
                "未找到应用'$appNameOrPackage'"
            }
        }

        return ""
    }

    /** 获取已安装应用列表（应用名 -> 包名） */
    private fun getInstalledAppsMap(): Map<String, String> {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            apps.associate {
                val label = pm.getApplicationLabel(it).toString()
                label to it.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
            emptyMap()
        }
    }

    /** 通过剪贴板输入文本（支持中文） */
    private suspend fun inputTextViaClipboard(text: String): String {
        return withContext(Dispatchers.Main) {
            try {
                // 使用应用的 ClipboardManager 写入剪贴板
                val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("autoglm_input", text)
                clipboard.setPrimaryClip(clip)
                Log.d(TAG, "Text copied to clipboard: $text")

                // 等待剪贴板同步
                delay(100)

                // 发送粘贴键事件 (Ctrl+V = KEYCODE_V + META_CTRL_ON)
                // ADB 方式: input keyevent --longpress 278 (KEYCODE_PASTE)
                withContext(Dispatchers.IO) {
                    val result = executeAdbCommand("input keyevent 279") // KEYCODE_PASTE = 279
                    if (result.contains("Error") || result.contains("not found")) {
                        // 如果 KEYCODE_PASTE 不工作，尝试 Ctrl+V
                        Log.d(TAG, "KEYCODE_PASTE failed, trying Ctrl+V")
                        executeAdbCommand("input keyevent --meta CTRL_ON KEYCODE_V")
                    }
                    result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard input failed", e)
                "输入失败: ${e.message}"
            }
        }
    }

    fun stop() {
        executionJob?.cancel()
        _status.value = ExecutionStatus.Cancelled
        addLog(LogType.INFO, "用户手动停止任务")
    }

    /** 获取历史动作摘要（用于新指令上下文） */
    fun getHistorySummary(): String {
        return executedActions.takeLast(5).joinToString(" -> ")
    }

    private fun addLog(type: LogType, message: String) {
        val timestamp = System.currentTimeMillis()
        val timeStr = dateFormat.format(Date(timestamp))

        val entry = LogEntry(timestamp = timestamp, type = type, message = message)
        _logs.value = _logs.value + entry

        // 触发回调
        onNewLog?.invoke(entry)

        val prefix =
                when (type) {
                    LogType.INFO -> "[INFO]"
                    LogType.ACTION -> "[ACTION]"
                    LogType.WARNING -> "[WARN]"
                    LogType.ERROR -> "[ERROR]"
                }
        detailedLogs.appendLine("$timeStr $prefix $message")
        FileLogger.d(TAG, message)
    }

    /** ADB input text 转义：处理空格和特殊字符 */
    private fun escapeForAdbInput(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            when {
                char == ' ' -> sb.append("%s") // 空格用 %s
                char == '"' -> sb.append("\\\"")
                char == '\'' -> sb.append("\\'")
                char == '\\' -> sb.append("\\\\")
                char == '&' -> sb.append("\\&")
                char == ';' -> sb.append("\\;")
                char == '|' -> sb.append("\\|")
                char == '<' -> sb.append("\\<")
                char == '>' -> sb.append("\\>")
                char == '`' -> sb.append("\\`")
                char == '$' -> sb.append("\\$")
                char == '(' -> sb.append("\\(")
                char == ')' -> sb.append("\\)")
                char.code > 127 -> {
                    // 中文等非 ASCII 字符：逐字符输入会失败
                    // 输出警告并尝试原样输入（可能失败）
                    sb.append(char)
                }
                else -> sb.append(char)
            }
        }
        return sb.toString()
    }

    // ==================== Set-of-Marks 相关 ====================

    /** 当前可点击元素列表（每次截图后更新） */
    private var currentUIElements: List<UIElement> = emptyList()

    /**
     * 获取带标记的截图
     * @param onBeforeScreenshot 截图前回调（隐藏悬浮窗）
     * @param onAfterScreenshot 截图后回调（显示悬浮窗）
     * @return Pair<标注后的Bitmap, 元素列表> 或 null
     */
    suspend fun getMarkedScreenshot(
            onBeforeScreenshot: (() -> Unit)? = null,
            onAfterScreenshot: (() -> Unit)? = null
    ): Pair<Bitmap, List<UIElement>>? {
        // 1. 获取 UI 层级（悬浮窗可以显示）
        val elements = dumpUiHierarchy()
        if (elements.isEmpty()) {
            addLog(LogType.WARNING, "未找到可点击元素")
        }
        currentUIElements = elements

        // 2. 截图前隐藏悬浮窗
        onBeforeScreenshot?.invoke()
        delay(20) // 短暂延迟确保悬浮窗已隐藏

        // 3. 截图
        val screenshot = takeScreenshot()

        // 4. 截图后立即显示悬浮窗（标注在悬浮窗可见的情况下进行）
        onAfterScreenshot?.invoke()

        if (screenshot == null) return null

        // 5. 标注（此时悬浮窗已显示）
        val markedBitmap =
                if (elements.isNotEmpty()) {
                    SetOfMarks.drawMarks(screenshot, elements)
                } else {
                    screenshot
                }

        // DEBUG: 保存带标记的截图到本地 (仅保存最新一张)
        try {
            val externalCacheDir = context.externalCacheDir
            if (externalCacheDir != null) {
                // 使用固定文件名，覆盖旧文件
                val debugPath = "${externalCacheDir.absolutePath}/screenshot_marked_latest.png"
                val file = java.io.File(debugPath)
                val fos = java.io.FileOutputStream(file)
                markedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()
                addLog(LogType.INFO, "已保存最新标记截图")
                Log.d(TAG, "Saved latest marked screenshot to $debugPath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save debug screenshot", e)
        }

        addLog(LogType.INFO, "Set-of-Marks: 标注了 ${elements.size} 个可点击元素")
        return Pair(markedBitmap, elements)
    }

    /** 通过 uiautomator dump 获取可点击 UI 元素 */
    private fun dumpUiHierarchy(): List<UIElement> {
        return try {
            val dumpPath = "/sdcard/autoglm_ui.xml"

            // 先删除旧文件
            executeAdbCommand("rm -f $dumpPath")
            Thread.sleep(100)

            // 执行 uiautomator dump（使用完整路径）
            val dumpResult = executeAdbCommand("/system/bin/uiautomator dump $dumpPath")
            addLog(LogType.INFO, "uiautomator dump 结果: ${dumpResult.take(200)}")

            // 等待文件写入（增加等待时间）
            Thread.sleep(1000)

            // 检查文件是否存在
            val checkFile = executeAdbCommand("ls -la $dumpPath 2>&1")
            addLog(LogType.INFO, "文件检查: ${checkFile.take(100)}")

            if (checkFile.contains("No such file")) {
                addLog(LogType.WARNING, "XML 文件未生成，尝试其他路径")
                // 尝试 /data/local/tmp 路径
                val altPath = "/data/local/tmp/autoglm_ui.xml"
                executeAdbCommand("/system/bin/uiautomator dump $altPath")
                Thread.sleep(1000)
                val altContent = executeAdbCommand("cat $altPath 2>&1")
                if (altContent.isNotBlank() && !altContent.contains("No such file")) {
                    addLog(LogType.INFO, "使用备用路径成功，XML 长度: ${altContent.length}")
                    return parseClickableElements(altContent)
                }
                return emptyList()
            }

            // 读取 XML
            val xmlContent = executeAdbCommand("cat $dumpPath")
            addLog(LogType.INFO, "XML 长度: ${xmlContent.length}, 前100字符: ${xmlContent.take(100)}")

            if (xmlContent.isBlank() ||
                            xmlContent.contains("ERROR") ||
                            xmlContent.contains("No such file")
            ) {
                Log.w(TAG, "uiautomator dump 失败: $xmlContent")
                addLog(LogType.WARNING, "uiautomator dump 失败: ${xmlContent.take(200)}")
                return emptyList()
            }

            // 解析 XML
            val elements = parseClickableElements(xmlContent)
            addLog(LogType.INFO, "解析到 ${elements.size} 个可点击元素")
            elements
        } catch (e: Exception) {
            Log.e(TAG, "UI dump 失败", e)
            addLog(LogType.ERROR, "UI dump 异常: ${e.message}")
            emptyList()
        }
    }

    /** 解析 XML 获取可点击元素 (使用 XmlPullParser) */
    private fun parseClickableElements(xml: String): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        var markId = 1
        var totalNodes = 0
        var clickableNodes = 0

        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(java.io.StringReader(xml))

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "node") {
                    totalNodes++

                    val isClickable = parser.getAttributeValue(null, "clickable") == "true"
                    // 某些情况下 checkable 或 long-clickable 也应该被视为交互元素，但目前主要关注 clickable

                    if (isClickable) {
                        clickableNodes++
                        val boundsStr = parser.getAttributeValue(null, "bounds")

                        if (boundsStr != null) {
                            val bounds = parseBounds(boundsStr)
                            if (bounds != null) {
                                val width = bounds.width()
                                val height = bounds.height()

                                // 过滤无效或过大的元素
                                if (width > 0 && height > 0 && width < 3000 && height < 3000) {
                                    val text = parser.getAttributeValue(null, "text")
                                    val desc = parser.getAttributeValue(null, "content-desc")
                                    val className = parser.getAttributeValue(null, "class")
                                    val resourceId = parser.getAttributeValue(null, "resource-id")

                                    // 仅当有意义的信息时才添加（或者本身是可点击的容器）
                                    // 放宽限制，只在完全透明且无内容时才过滤？
                                    // 目前策略：只要 clickable 且有 bounds 就添加，确保不遗漏

                                    elements.add(
                                            UIElement(
                                                    id = markId++,
                                                    bounds = bounds,
                                                    text = text?.takeIf { it.isNotBlank() },
                                                    description = desc?.takeIf { it.isNotBlank() },
                                                    className = className
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "XML Parsing Error", e)
            addLog(LogType.ERROR, "XML 解析异常: ${e.message}")
        }

        addLog(LogType.INFO, "XML解析: 总节点$totalNodes, 可点击$clickableNodes, 有效标记${elements.size}")
        return elements
    }

    /** 解析 bounds 字符串: "[x1,y1][x2,y2]" */
    private fun parseBounds(boundsVal: String): android.graphics.Rect? {
        return try {
            // [0,0][1080,2400]
            val split = boundsVal.split("][", ",", "[", "]").filter { it.isNotEmpty() }
            if (split.size == 4) {
                val left = split[0].toInt()
                val top = split[1].toInt()
                val right = split[2].toInt()
                val bottom = split[3].toInt()
                android.graphics.Rect(left, top, right, bottom)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 根据 mark 编号点击 */
    fun clickByMark(markId: Int): String {
        val element = currentUIElements.find { it.id == markId }
        return if (element != null) {
            val x = element.centerX
            val y = element.centerY
            addLog(
                    LogType.INFO,
                    "Mark[$markId] 点击: (${x}, ${y}) ${element.text ?: element.description ?: ""}"
            )
            executeAdbCommand("input tap $x $y")
        } else {
            addLog(LogType.WARNING, "未找到 mark=$markId 的元素")
            "错误: 未找到编号 $markId 的元素"
        }
    }
}
