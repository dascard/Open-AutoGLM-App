package com.autoglm.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import com.autoglm.app.AutoGLMAccessibilityService
import com.autoglm.app.util.FileLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 任务执行器
 * 负责循环执行：截图 → AI 分析 → 执行动作
 */
class TaskExecutor(
    private val context: Context,
    private val aiClient: AIClient,
    private val accessibilityService: AutoGLMAccessibilityService
) {
    companion object {
        private const val TAG = "TaskExecutor"
        private const val MAX_STEPS = 50  // 最大执行步数
        private const val STEP_DELAY = 500L  // 每步之间的延迟
    }
    
    private var executionJob: Job? = null
    private val executedActions = mutableListOf<String>()
    
    private val _status = MutableStateFlow<ExecutionStatus>(ExecutionStatus.Idle)
    val status: StateFlow<ExecutionStatus> = _status
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs
    
    // 暂停状态
    @Volatile
    private var isPaused = false
    
    // 详细日志存储（用于复制）
    private val detailedLogs = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    // 悬浮窗状态更新回调
    var onStatusUpdate: ((status: String, action: String) -> Unit)? = null
    
    // 暂停状态变化回调
    var onPauseStateChanged: ((isPaused: Boolean) -> Unit)? = null
    
    // 截图时隐藏/显示悬浮窗回调
    var onHideFloatingWindow: (() -> Unit)? = null
    var onShowFloatingWindow: (() -> Unit)? = null
    
    /**
     * 获取完整日志文本（用于复制）
     */
    fun getFullLogText(): String = detailedLogs.toString()
    
    /**
     * 暂停任务
     */
    fun pause() {
        if (_status.value is ExecutionStatus.Running && !isPaused) {
            isPaused = true
            addLog(LogType.INFO, "⏸️ 任务已暂停")
            onPauseStateChanged?.invoke(true)
            onStatusUpdate?.invoke("已暂停", "点击继续恢复执行")
        }
    }
    
    /**
     * 继续任务
     */
    fun resume() {
        if (_status.value is ExecutionStatus.Running && isPaused) {
            isPaused = false
            addLog(LogType.INFO, "▶️ 任务继续执行")
            onPauseStateChanged?.invoke(false)
            onStatusUpdate?.invoke("继续执行中...", "")
        }
    }
    
    /**
     * 检查是否暂停中
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * 执行任务 - 在 IO 线程执行避免 ANR
     */
    suspend fun executeTask(task: String): TaskResult = withContext(Dispatchers.IO) {
        if (_status.value is ExecutionStatus.Running) {
            return@withContext TaskResult.Failed("任务正在执行中")
        }
        
        // 重置暂停状态
        isPaused = false
        
        executedActions.clear()
        _logs.value = emptyList()
        detailedLogs.clear()
        _status.value = ExecutionStatus.Running
        
        addLog(LogType.INFO, "===== 任务开始 =====")
        addLog(LogType.INFO, "任务: $task")
        addLog(LogType.INFO, "时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        
        try {
            executionJob = coroutineScope {
                launch {
                    executeLoop(task)
                }
            }
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
            addLog(LogType.ERROR, "堆栈: ${e.stackTraceToString().take(500)}")
            TaskResult.Failed(e.message ?: "未知错误")
        }
    }
    
    private suspend fun executeLoop(task: String) {
        var stepCount = 0
        var consecutiveFatalErrors = 0  // 连续致命错误计数
        
        // 先回到主屏幕，避免 AI 看到 AutoGLM 自己的界面
        addLog(LogType.INFO, "正在回到主屏幕...")
        withContext(Dispatchers.Main) {
            accessibilityService.performHome()
        }
        delay(1000)  // 等待动画完成
        addLog(LogType.INFO, "已回到主屏幕，开始执行任务")
        
        while (stepCount < MAX_STEPS && _status.value is ExecutionStatus.Running) {
            // 等待暂停结束
            while (isPaused && _status.value is ExecutionStatus.Running) {
                delay(200)
            }
            
            stepCount++
            addLog(LogType.INFO, "")
            addLog(LogType.INFO, "--- 步骤 $stepCount/$MAX_STEPS ---")
            
            // 1. 截图（先隐藏悬浮窗，避免 AI 看到）
            addLog(LogType.INFO, "正在截图...")
            onHideFloatingWindow?.invoke()
            delay(100)  // 等待悬浮窗隐藏动画完成
            
            val screenshot = takeScreenshot()
            
            // 恢复悬浮窗显示
            onShowFloatingWindow?.invoke()
            onStatusUpdate?.invoke("步骤 $stepCount: 截图完成", "")
            
            if (screenshot == null) {
                addLog(LogType.ERROR, "截图失败！请检查无障碍权限")
                throw Exception("截图失败")
            }
            addLog(LogType.INFO, "截图成功: ${screenshot.width}x${screenshot.height}")
            
            // 2. AI 分析
            addLog(LogType.INFO, "正在调用 AI 分析...")
            onStatusUpdate?.invoke("步骤 $stepCount: AI 思考中...", "分析屏幕内容")
            
            val response = try {
                val startTime = System.currentTimeMillis()
                val result = aiClient.analyzeScreenAndPlan(screenshot, task, executedActions)
                val duration = System.currentTimeMillis() - startTime
                addLog(LogType.INFO, "AI 响应耗时: ${duration}ms")
                // 成功则重置致命错误计数
                consecutiveFatalErrors = 0
                result
            } catch (e: Exception) {
                addLog(LogType.WARNING, "AI 分析出错: ${e.message?.take(100)}")
                
                // 检查是否为致命错误（余额不足、额度耗尽等）
                if (aiClient.isFatalError(e)) {
                    consecutiveFatalErrors++
                    addLog(LogType.ERROR, "⚠️ 检测到致命错误 ($consecutiveFatalErrors/3): ${e.message?.take(50)}")
                    
                    if (consecutiveFatalErrors >= 3) {
                        addLog(LogType.ERROR, "❌ 连续3次致命错误，终止任务")
                        addLog(LogType.ERROR, "请检查 API 余额或账户状态")
                        onStatusUpdate?.invoke("任务终止 ⛔", "API 账户问题")
                        break
                    }
                }
                
                onStatusUpdate?.invoke("步骤 $stepCount: 重试中", "上次分析失败")
                
                // 将错误作为反馈添加到历史，让 AI 知道上次尝试失败了
                val errorFeedback = "【上次操作失败】${e.message?.take(50)}，请换一种方式尝试"
                executedActions.add(errorFeedback)
                
                // 不直接抛出异常，而是继续下一步尝试
                addLog(LogType.INFO, "将继续尝试...")
                delay(500)
                continue  // 跳过本次循环，重新截图让 AI 重试
            }
            
            val action = response.action
            val allActions = response.getAllActions()
            val statusMsg = response.status.ifEmpty { "执行中..." }
            
            // 检查任务是否已取消（用户在 AI 分析期间点击了停止）
            if (_status.value is ExecutionStatus.Cancelled) {
                addLog(LogType.INFO, "任务已取消，忽略 AI 响应")
                break
            }
            
            addLog(LogType.ACTION, "AI 返回 ${allActions.size} 个动作")
            
            // 输出原始响应以便调试
            response.rawResponse?.let {
                addLog(LogType.INFO, "AI 原始响应: $it")
            }
            
            if (response.status.isNotEmpty()) {
                addLog(LogType.INFO, "状态: ${response.status}")
            }
            
            // 输出 AI 返回的每个动作详情
            allActions.forEachIndexed { index, act ->
                addLog(LogType.INFO, "  动作${index + 1}: $act")
            }
            
            // 记录 AI 思考过程并显示在悬浮窗（如果有）
            response.thinking?.let { think ->
                addLog(LogType.INFO, "💭 AI 思考: $think")
                // 在悬浮窗显示思考内容（截取前30字符）
                val thinkPreview = if (think.length > 30) think.take(30) + "..." else think
                onStatusUpdate?.invoke(statusMsg, "💭 $thinkPreview")
            }
            
            // 3. 检查是否完成
            if (action is Action.Done) {
                addLog(LogType.INFO, "✅ AI 判断任务已完成")
                onStatusUpdate?.invoke("任务完成 ✅", action.message)
                break
            }
            
            // 3.5 检查是否请求用户介入
            if (action is Action.AskUser) {
                addLog(LogType.WARNING, "⚠️ AI 请求用户介入")
                addLog(LogType.INFO, "原因: ${action.reason}")
                if (action.suggestion.isNotEmpty()) {
                    addLog(LogType.INFO, "建议: ${action.suggestion}")
                }
                
                // 暂停任务等待用户处理
                isPaused = true
                onPauseStateChanged?.invoke(true)
                onStatusUpdate?.invoke("需要用户介入 ⚠️", action.reason)
                
                // 记录到已执行动作中
                executedActions.add("【等待用户】${action.reason}")
                
                // 等待用户恢复
                while (isPaused && _status.value is ExecutionStatus.Running) {
                    delay(200)
                }
                
                // 用户恢复后，继续循环（重新截图分析）
                addLog(LogType.INFO, "用户已处理，继续执行...")
                continue
            }
            
            // 4. 执行所有动作（隐藏悬浮窗避免误触）
            onHideFloatingWindow?.invoke()
            delay(50)
            onStatusUpdate?.invoke(statusMsg, "${allActions.size} 个操作")
            
            for ((index, actionItem) in allActions.withIndex()) {
                // 跳过 Done 和 AskUser（已处理）
                if (actionItem is Action.Done || actionItem is Action.AskUser) continue
                
                addLog(LogType.INFO, "执行动作 ${index + 1}/${allActions.size}: $actionItem")
                
                val resultMsg = executeAction(actionItem)
                if (resultMsg.isEmpty()) {
                    addLog(LogType.INFO, "✓ 动作执行成功")
                    executedActions.add(actionItem.toString())
                } else {
                    addLog(LogType.WARNING, "✗ 动作执行失败")
                    // 添加失败反馈，让 AI 知道这个操作失败了
                    // 对于 Launch，resultMsg 包含了相似应用的建议
                    val failureMsg = if (actionItem is Action.Launch) {
                        "【Launch失败】$resultMsg"
                    } else {
                        "【操作失败】$resultMsg"
                    }
                    executedActions.add(failureMsg)
                    addLog(LogType.INFO, "已添加失败反馈: $failureMsg")
                }
                
                // 多操作之间短暂延迟
                if (index < allActions.size - 1) {
                    delay(300)
                }
            }
            
            // 恢复悬浮窗显示
            onShowFloatingWindow?.invoke()
            onStatusUpdate?.invoke(statusMsg, "操作完成")
            
            // 5. 延迟
            addLog(LogType.INFO, "等待 ${STEP_DELAY}ms...")
            delay(STEP_DELAY)
        }
        
        if (stepCount >= MAX_STEPS) {
            addLog(LogType.WARNING, "⚠️ 达到最大步数限制 ($MAX_STEPS)")
        }
    }
    
    private suspend fun takeScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        try {
            accessibilityService.takeScreenshotBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed", e)
            addLog(LogType.ERROR, "截图异常: ${e.message}")
            null
        }
    }
    
    private suspend fun executeAction(action: Action): String = withContext(Dispatchers.Main) {
        when (action) {
            is Action.Tap -> {
                addLog(LogType.INFO, "执行: 点击 (${action.x}, ${action.y})")
                if (accessibilityService.performTap(action.x, action.y)) "" else "Tap执行失败"
            }
            is Action.Swipe -> {
                addLog(LogType.INFO, "执行: 滑动 (${action.x1},${action.y1})→(${action.x2},${action.y2})")
                if (accessibilityService.performSwipe(
                    action.x1, action.y1, action.x2, action.y2, action.duration
                )) "" else "Swipe执行失败"
            }
            is Action.Input -> {
                addLog(LogType.INFO, "执行: 输入 \"${action.text}\"")
                if (accessibilityService.performInput(action.text)) "" else "Input执行失败"
            }
            is Action.Back -> {
                addLog(LogType.INFO, "执行: 返回")
                if (accessibilityService.performBack()) "" else "Back执行失败"
            }
            is Action.Home -> {
                addLog(LogType.INFO, "执行: 回到主屏幕")
                if (accessibilityService.performHome()) "" else "Home执行失败"
            }
            is Action.Wait -> {
                addLog(LogType.INFO, "执行: 等待 ${action.milliseconds}ms")
                delay(action.milliseconds)
                ""
            }
            is Action.Launch -> {
                addLog(LogType.INFO, "执行: 启动应用 \"${action.appName}\"")
                launchApp(action.appName)
            }
            is Action.Enter -> {
                addLog(LogType.INFO, "执行: 确认/回车")
                if (accessibilityService.performEnter()) "" else "Enter执行失败"
            }
            is Action.LongPress -> {
                addLog(LogType.INFO, "执行: 长按 (${action.x}, ${action.y})")
                if (accessibilityService.performLongPress(action.x, action.y, action.duration)) "" else "LongPress执行失败"
            }
            is Action.Done -> ""
            is Action.AskUser -> ""
        }
    }
    
    /**
     * 根据应用名称或包名启动应用
     */
    private fun launchApp(appName: String): String {
        return try {
            val pm = context.packageManager
            
            // 1. 尝试作为包名直接启动
            var launchIntent = pm.getLaunchIntentForPackage(appName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "" // 成功
            }
            
            // 2. 遍历应用查找
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val fuzzyMatches = mutableListOf<String>()
            
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString()
                
                // 2.1 精确匹配 (忽略大小写) - 直接启动
                if (label.equals(appName, ignoreCase = true)) {
                    launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        addLog(LogType.INFO, "找到精确匹配应用: $label")
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return "" // 成功
                    }
                }
                
                // 2.2 收集模糊匹配作为建议
                if (label.contains(appName, ignoreCase = true) || appName.contains(label, ignoreCase = true)) {
                    if (!fuzzyMatches.contains(label)) {
                        fuzzyMatches.add(label)
                    }
                }
            }
            
            // 3. 处理结果
            if (fuzzyMatches.isNotEmpty()) {
                // 找到相似应用，但没有精确匹配
                val suggestions = fuzzyMatches.sortedBy { Math.abs(it.length - appName.length) }.take(3).joinToString(", ")
                addLog(LogType.WARNING, "未找到精确匹配'$appName'，发现相似应用: $suggestions")
                return "未找到完全匹配的应用'$appName'。发现相似应用: [$suggestions]。请尝试使用这些名称，或该应用的英文/系统名称。"
            }
            
            // 完全没找到
            addLog(LogType.WARNING, "未找到应用: $appName")
            return "未找到应用'$appName'，且无相似应用。请确认应用名称正确，或者该应用已安装。"
            
        } catch (e: Exception) {
            FileLogger.e(TAG, "Launch failed: ${e.message}")
            "启动应用出错: ${e.message}"
        }
    }
    
    /**
     * 停止任务
     */
    fun stop() {
        executionJob?.cancel()
        _status.value = ExecutionStatus.Cancelled
        addLog(LogType.INFO, "用户手动停止任务")
    }
    
    private fun addLog(type: LogType, message: String) {
        val timestamp = System.currentTimeMillis()
        val timeStr = dateFormat.format(Date(timestamp))
        
        // 添加到 UI 日志
        val entry = LogEntry(
            timestamp = timestamp,
            type = type,
            message = message
        )
        _logs.value = _logs.value + entry
        
        // 添加到详细日志
        val prefix = when (type) {
            LogType.INFO -> "[INFO]"
            LogType.ACTION -> "[ACTION]"
            LogType.WARNING -> "[WARN]"
            LogType.ERROR -> "[ERROR]"
        }
        detailedLogs.appendLine("$timeStr $prefix $message")
        
        // 同时写入文件日志
        FileLogger.d(TAG, message)
    }
}

/**
 * 执行状态
 */
sealed class ExecutionStatus {
    object Idle : ExecutionStatus()
    object Running : ExecutionStatus()
    object Completed : ExecutionStatus()
    object Cancelled : ExecutionStatus()
    data class Error(val message: String) : ExecutionStatus()
}

/**
 * 日志类型
 */
enum class LogType {
    INFO, ACTION, WARNING, ERROR
}

/**
 * 日志条目
 */
data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val message: String
)
