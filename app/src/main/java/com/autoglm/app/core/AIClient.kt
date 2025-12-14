package com.autoglm.app.core

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.autoglm.app.util.FileLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 重试配置
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val multiplier: Double = 2.0
)

/**
 * AI API 客户端
 * 支持多 API 轮询、随机选择、故障转移
 */
class AIClient(
    private val apiConfigs: List<ApiConfig>,
    private val retryConfig: RetryConfig = RetryConfig()
) {
    companion object {
        private const val TAG = "AIClient"
    }
    
    private val enabledConfigs: List<ApiConfig>
        get() = apiConfigs.filter { it.enabled }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // 记录失败的 API，避免短时间内重复使用
    private val failedApis = mutableMapOf<String, Long>()
    private val failureCooldownMs = 60_000L  // 失败后冷却 60 秒
    
    /**
     * 分析屏幕截图并获取下一步动作
     * 使用多 API 轮询策略
     */
    suspend fun analyzeScreenAndPlan(
        screenshot: Bitmap,
        task: String,
        previousActions: List<String> = emptyList()
    ): AIResponse = withContext(Dispatchers.IO) {
        val availableConfigs = getAvailableConfigs()
        
        if (availableConfigs.isEmpty()) {
            throw Exception("没有可用的 API 配置，请在设置中添加")
        }
        
        // 随机打乱顺序（考虑优先级）
        val shuffledConfigs = availableConfigs
            .sortedByDescending { it.priority }
            .groupBy { it.priority }
            .flatMap { (_, configs) -> configs.shuffled() }
        
        var lastException: Exception? = null
        
        for (config in shuffledConfigs) {
            try {
                Log.d(TAG, "尝试使用 API: ${config.name} (${config.provider.displayName})")
                val result = makeApiCallWithRetry(config, screenshot, task, previousActions)
                // 成功，清除失败记录
                failedApis.remove(config.id)
                return@withContext result
            } catch (e: Exception) {
                Log.w(TAG, "API ${config.name} 调用失败: ${e.message}")
                lastException = e
                // 记录失败
                failedApis[config.id] = System.currentTimeMillis()
            }
        }
        
        throw lastException ?: Exception("所有 API 调用均失败")
    }
    
    /**
     * 获取当前可用的 API 配置（排除冷却中的）
     */
    private fun getAvailableConfigs(): List<ApiConfig> {
        val now = System.currentTimeMillis()
        return enabledConfigs.filter { config ->
            val failTime = failedApis[config.id]
            failTime == null || (now - failTime) > failureCooldownMs
        }
    }
    
    /**
     * 带重试的 API 调用
     */
    private suspend fun makeApiCallWithRetry(
        config: ApiConfig,
        screenshot: Bitmap,
        task: String,
        previousActions: List<String>
    ): AIResponse {
        var lastException: Exception? = null
        var delayMs = retryConfig.initialDelayMs
        
        repeat(retryConfig.maxRetries) { attempt ->
            try {
                Log.d(TAG, "API ${config.name} 尝试 ${attempt + 1}/${retryConfig.maxRetries}")
                return makeApiCall(config, screenshot, task, previousActions)
            } catch (e: Exception) {
                lastException = e
                
                if (!isRetryableError(e)) {
                    throw e
                }
                
                if (attempt < retryConfig.maxRetries - 1) {
                    Log.d(TAG, "等待 ${delayMs}ms 后重试...")
                    delay(delayMs)
                    delayMs = (delayMs * retryConfig.multiplier).toLong()
                        .coerceAtMost(retryConfig.maxDelayMs)
                }
            }
        }
        
        throw lastException ?: Exception("API 调用失败")
    }
    
    /**
     * 判断错误是否可重试
     */
    private fun isRetryableError(e: Exception): Boolean {
        // 致命错误不可重试
        if (isFatalError(e)) return false
        
        val message = e.message?.lowercase() ?: return false
        return when {
            message.contains("timeout") -> true
            message.contains("connection") -> true
            message.contains("socket") -> true
            message.contains("500") -> true
            message.contains("502") -> true
            message.contains("503") -> true
            message.contains("504") -> true
            message.contains("429") -> true
            message.contains("rate limit") -> true
            else -> false
        }
    }
    
    /**
     * 判断是否为致命错误（账户级别问题，不应无限重试）
     * 包括：余额不足、额度耗尽、认证失败、账户被禁用等
     */
    fun isFatalError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: return false
        return when {
            // 余额/额度相关
            message.contains("insufficient") -> true
            message.contains("quota") -> true
            message.contains("balance") -> true
            message.contains("余额") -> true
            message.contains("额度") -> true
            message.contains("credit") -> true
            message.contains("billing") -> true
            // 认证相关
            message.contains("401") -> true
            message.contains("403") -> true
            message.contains("unauthorized") -> true
            message.contains("forbidden") -> true
            message.contains("invalid api key") -> true
            message.contains("invalid_api_key") -> true
            message.contains("authentication") -> true
            // 账户状态
            message.contains("account") && (message.contains("disabled") || message.contains("suspended") || message.contains("banned")) -> true
            message.contains("账户") -> true
            message.contains("账号") -> true
            else -> false
        }
    }
    
    /**
     * 执行 API 调用
     */
    private suspend fun makeApiCall(
        config: ApiConfig,
        screenshot: Bitmap,
        task: String,
        previousActions: List<String>
    ): AIResponse {
        val base64Image = bitmapToBase64(screenshot)
        
        // 根据不同提供商构建请求
        val (request, parseResponse) = when (config.provider) {
            AIProvider.CLAUDE -> buildClaudeRequest(config, base64Image, task, previousActions)
            AIProvider.GEMINI -> buildGeminiRequest(config, base64Image, task, previousActions)
            else -> buildOpenAICompatibleRequest(config, base64Image, task, previousActions)
        }
        
        FileLogger.logApiRequest(TAG, config.provider.displayName, config.model, config.endpoint)
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("空响应")
        
        FileLogger.logApiResponse(TAG, response.code, responseBody)
        
        if (!response.isSuccessful) {
            val errorMsg = parseErrorMessage(responseBody, response.code)
            throw Exception(errorMsg)
        }
        
        return parseResponse(responseBody)
    }
    
    /**
     * 构建 OpenAI 兼容格式请求（智谱、OpenAI、通义千问等）
     */
    private fun buildOpenAICompatibleRequest(
        config: ApiConfig,
        base64Image: String,
        task: String,
        previousActions: List<String>
    ): Pair<Request, (String) -> AIResponse> {
        val systemPrompt = getSystemPrompt()
        val userMessage = getUserMessage(task, previousActions)
        
        val requestJson = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("stream", false)  // 禁用流式传输
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to userMessage),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Image")
                        )
                    )
                )
            )))
            addProperty("max_tokens", 512)
            addProperty("temperature", 0.1)
        }
        
        val request = Request.Builder()
            .url(config.endpoint)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return Pair(request, ::parseOpenAIResponse)
    }
    
    /**
     * 构建 Claude 请求
     */
    private fun buildClaudeRequest(
        config: ApiConfig,
        base64Image: String,
        task: String,
        previousActions: List<String>
    ): Pair<Request, (String) -> AIResponse> {
        val userMessage = getUserMessage(task, previousActions)
        
        val requestJson = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("max_tokens", 512)
            addProperty("stream", false)  // 禁用流式传输
            addProperty("system", getSystemPrompt())
            add("messages", gson.toJsonTree(listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "image",
                            "source" to mapOf(
                                "type" to "base64",
                                "media_type" to "image/jpeg",
                                "data" to base64Image
                            )
                        ),
                        mapOf("type" to "text", "text" to userMessage)
                    )
                )
            )))
        }
        
        val request = Request.Builder()
            .url(config.endpoint)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return Pair(request, ::parseClaudeResponse)
    }
    
    /**
     * 构建 Gemini 请求
     */
    private fun buildGeminiRequest(
        config: ApiConfig,
        base64Image: String,
        task: String,
        previousActions: List<String>
    ): Pair<Request, (String) -> AIResponse> {
        val userMessage = "${getSystemPrompt()}\n\n${getUserMessage(task, previousActions)}"
        
        val requestJson = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to userMessage),
                        mapOf(
                            "inline_data" to mapOf(
                                "mime_type" to "image/jpeg",
                                "data" to base64Image
                            )
                        )
                    )
                )
            )))
        }
        
        val url = "${config.endpoint}?key=${config.apiKey}"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return Pair(request, ::parseGeminiResponse)
    }
    
    // 屏幕尺寸，用于坐标转换
    private var screenWidth = 1080
    private var screenHeight = 2400
    
    /**
     * 设置屏幕尺寸（从截图获取）
     */
    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }
    
    /**
     * 将归一化坐标 (0-999) 转换为像素坐标
     */
    private fun normalizedToPixel(normalizedX: Int, normalizedY: Int): Pair<Int, Int> {
        val pixelX = (normalizedX * screenWidth / 1000).coerceIn(0, screenWidth - 1)
        val pixelY = (normalizedY * screenHeight / 1000).coerceIn(0, screenHeight - 1)
        return Pair(pixelX, pixelY)
    }
    
    private fun getSystemPrompt(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日 EEEE", java.util.Locale.CHINESE)
        val today = dateFormat.format(java.util.Date())
        
        return """
今天的日期是: $today
你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。

【重要】你必须严格按照以下格式输出：
<think>你的思考过程，说明为什么选择这个操作</think>
<answer>do(action="操作类型", ...)</answer>

操作指令：
- do(action="Launch", app="应用名") - 【推荐】直接启动应用，支持中英文名称
- do(action="Tap", element=[x,y]) - 点击坐标(0-999归一化坐标)
- do(action="Tap", element=[x,y], message="说明") - 敏感点击，暂停等待确认
- do(action="Type", text="xxx") - 输入文本
- do(action="Enter") - 【推荐】按下输入法确认键（搜索/发送/下一步），比点击按钮更精准
- do(action="Swipe", start=[x1,y1], end=[x2,y2]) - 滑动
- do(action="Long Press", element=[x,y]) - 长按
- do(action="Back") - 返回
- do(action="Home") - 回桌面
- do(action="Wait", duration="x seconds") - 等待x秒
- ask_user(reason="原因") - 请求用户介入
- finish(message="完成信息") - 任务完成

规则：
1. 【最重要】需要打开应用时，优先使用 Launch 操作，而不是点击图标, 软件的名称未必和用户输入的yi一模一样,可能有出入也可能是英文,比如谷歌浏览器其实是Chrome
2. 先检查当前界面是否符合预期
3. 无关页面执行 Back
4. 找不到目标可尝试 Swipe 滑动查找
5. 涉及密码/验证码/支付 -> 使用 ask_user
6. 当处于 AutoGLM 软件内时，使用 Home 操作
7. 如果 Launch 失败（历史中有失败记录），改用 Tap 点击图标

【示例1 - 打开应用】
<think>任务需要打开浏览器，使用 Launch 直接启动</think>
<answer>do(action="Launch", app="浏览器")</answer>

【示例2 - 点击操作】
<think>当前在浏览器中，需要点击搜索框，搜索框在屏幕上方中央</think>
<answer>do(action="Tap", element=[500,150])</answer>
        """.trimIndent()
    }
    
    private fun getUserMessage(task: String, previousActions: List<String>): String = buildString {
        append("任务: $task\n")
        if (previousActions.isNotEmpty()) {
            append("已执行: ${previousActions.takeLast(5).joinToString(" -> ")}\n")
            val homeCount = previousActions.takeLast(3).count { it.contains("Home") }
            if (homeCount >= 2) {
                append("【禁止再按Home！】\n")
            }
            // 检查 Launch 失败
            if (previousActions.any { it.contains("Launch失败") }) {
                append("【Launch失败过，请改用 Tap 点击图标】\n")
            }
        }
        append("\n请分析屏幕并输出操作:")
    }
    
    private fun parseOpenAIResponse(responseBody: String): AIResponse {
        val json = JsonParser.parseString(responseBody).asJsonObject
        val content = json.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
        return parseActionFromContent(content)
    }
    
    private fun parseClaudeResponse(responseBody: String): AIResponse {
        val json = JsonParser.parseString(responseBody).asJsonObject
        val content = json.getAsJsonArray("content")[0].asJsonObject
            .get("text").asString
        return parseActionFromContent(content)
    }
    
    private fun parseGeminiResponse(responseBody: String): AIResponse {
        val json = JsonParser.parseString(responseBody).asJsonObject
        val content = json.getAsJsonArray("candidates")[0].asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")[0].asJsonObject
            .get("text").asString
        return parseActionFromContent(content)
    }
    
    private fun parseActionFromContent(content: String): AIResponse {
        Log.i(TAG, "========== AI 返回内容 ==========")
        Log.i(TAG, content)
        Log.i(TAG, "==================================")
        FileLogger.d(TAG, "AI 原始响应: $content")
        
        // 0. 提取 think 标签内容（AI 思考过程）
        val thinkPattern = Regex("""<think>\s*(.*?)\s*</think>""", RegexOption.DOT_MATCHES_ALL)
        val thinkMatch = thinkPattern.find(content)
        val thinking = thinkMatch?.groupValues?.get(1)?.trim()
        if (thinking != null) {
            Log.d(TAG, "AI 思考: $thinking")
        }
        
        // 1. 提取 status（如果有）
        val statusPattern = Regex("""status:\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
        val statusMatch = statusPattern.find(content)
        val status = statusMatch?.groupValues?.get(1)?.trim() ?: ""
        
        // 2. 提取所有 actions 行
        val actionsPattern = Regex("""(?:actions:[\s\S]*?)?-\s*((?:do|finish|ask_user)\s*\([^)]+\))""", RegexOption.IGNORE_CASE)
        val actionMatches = actionsPattern.findAll(content).toList()
        
        val actions = mutableListOf<Action>()
        
        if (actionMatches.isNotEmpty()) {
            Log.d(TAG, "找到 ${actionMatches.size} 个操作")
            for (match in actionMatches) {
                val actionStr = match.groupValues[1]
                val parsedAction = tryParseFromText(actionStr)
                if (parsedAction != null) {
                    actions.add(parsedAction)
                    Log.d(TAG, "解析操作: $parsedAction")
                }
            }
        }
        
        // 3. 如果没有找到 actions 格式，尝试备用解析
        if (actions.isEmpty()) {
            val fallbackAction = tryParseFromText(content)
            if (fallbackAction != null) {
                return AIResponse(action = fallbackAction, status = status, thinking = thinking)
            }
        }
        
        // 4. 返回结果
        if (actions.isNotEmpty()) {
            FileLogger.d(TAG, "解析成功: ${actions.size} 个操作, status=$status, thinking=${thinking?.take(50)}")
            return AIResponse(
                action = actions.first(),
                actions = actions,
                status = status,
                thinking = thinking,
                rawResponse = content.take(500)  // 保存原始响应（截取前500字符）
            )
        }
        
        // 5. 首先尝试提取 JSON（兼容旧格式）
        val jsonContent = extractJson(content)
        
        // 如果找到了 JSON，尝试解析
        if (jsonContent.startsWith("{") && jsonContent.endsWith("}")) {
            try {
                Log.d(TAG, "提取的 JSON: $jsonContent")
                FileLogger.d(TAG, "提取的 JSON: $jsonContent")
                
                val reader = com.google.gson.stream.JsonReader(java.io.StringReader(jsonContent))
                reader.isLenient = true
                val actionJson = JsonParser.parseReader(reader).asJsonObject
                
                val actionType = actionJson.get("action")?.asString 
                    ?: throw Exception("响应中缺少 action 字段")
                
                val action = when (actionType.lowercase()) {
                    "tap", "click" -> {
                        val x = actionJson.get("x")?.asInt ?: 0
                        val y = actionJson.get("y")?.asInt ?: 0
                        Action.Tap(x, y)
                    }
                    "swipe", "scroll" -> Action.Swipe(
                        actionJson.get("x1")?.asInt ?: actionJson.get("startX")?.asInt ?: 0,
                        actionJson.get("y1")?.asInt ?: actionJson.get("startY")?.asInt ?: 0,
                        actionJson.get("x2")?.asInt ?: actionJson.get("endX")?.asInt ?: 0,
                        actionJson.get("y2")?.asInt ?: actionJson.get("endY")?.asInt ?: 0,
                        actionJson.get("duration")?.asInt ?: 300
                    )
                    "input", "type", "text" -> {
                        val text = actionJson.get("text")?.asString 
                            ?: actionJson.get("content")?.asString 
                            ?: ""
                        Action.Input(text)
                    }
                    "back" -> Action.Back
                    "home" -> Action.Home
                    "wait", "sleep", "delay" -> {
                        val ms = actionJson.get("milliseconds")?.asLong 
                            ?: actionJson.get("ms")?.asLong 
                            ?: actionJson.get("duration")?.asLong 
                            ?: 1000
                        Action.Wait(ms)
                    }
                    "done", "complete", "finish", "completed" -> Action.Done()
                    else -> throw Exception("未知动作类型: $actionType")
                }
                
                FileLogger.d(TAG, "解析成功: $action")
                return AIResponse(action = action)
            } catch (jsonError: Exception) {
                Log.w(TAG, "JSON 解析失败，尝试备用解析: ${jsonError.message}")
                FileLogger.w(TAG, "JSON 解析失败: ${jsonError.message}")
            }
        }
        
        // 备用解析：从纯文本中提取坐标
        Log.d(TAG, "尝试从文本中提取坐标...")
        FileLogger.d(TAG, "尝试备用解析，从文本提取坐标")
        
        val action = tryParseFromText(content)
        if (action != null) {
            FileLogger.d(TAG, "备用解析成功: $action")
            return AIResponse(action = action)
        }
        
        // 都失败了
        FileLogger.e(TAG, "无法解析 AI 响应")
        throw Exception("无法解析 AI 响应，请检查 AI 模型是否支持此任务\n原始响应: ${content.take(200)}")
    }
    
    /**
     * 备用解析：从纯文本中提取动作
     */
    private fun tryParseFromText(content: String): Action? {
        val lowerContent = content.lowercase()
        
        // 1. 首先尝试从 <answer> 标签中提取内容
        val answerPattern = Regex("""<answer>\s*(.*?)\s*</answer>""", RegexOption.DOT_MATCHES_ALL)
        val answerContent = answerPattern.find(content)?.groupValues?.get(1) ?: content
        
        // 2. 检查 finish 操作
        val finishPattern = Regex("""finish\s*\(\s*message\s*=\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
        val finishMatch = finishPattern.find(answerContent)
        if (finishMatch != null) {
            val message = finishMatch.groupValues[1]
            Log.d(TAG, "解析到 finish 操作: $message")
            return Action.Done(message)
        }
        
        // 2.5 检查 ask_user 操作
        val askUserPattern = Regex("""ask_user\s*\(\s*reason\s*=\s*"([^"]*)"(?:\s*,\s*suggestion\s*=\s*"([^"]*)")?\s*\)""", RegexOption.IGNORE_CASE)
        val askUserMatch = askUserPattern.find(answerContent)
        if (askUserMatch != null) {
            val reason = askUserMatch.groupValues[1]
            val suggestion = askUserMatch.groupValues.getOrNull(2) ?: ""
            Log.d(TAG, "解析到 ask_user 操作: reason=$reason, suggestion=$suggestion")
            return Action.AskUser(reason, suggestion)
        }
        
        // 3. 解析带 message 的敏感 Tap 操作 do(action="Tap", element=[x,y], message="...")
        val sensitiveTagPattern = Regex("""do\s*\(\s*action\s*=\s*"Tap"\s*,\s*element\s*=\s*\[\s*(\d+)\s*,\s*(\d+)\s*\]\s*,\s*message\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        val sensitiveMatch = sensitiveTagPattern.find(answerContent)
        if (sensitiveMatch != null) {
            val message = sensitiveMatch.groupValues[3]
            Log.d(TAG, "解析到敏感操作 Tap with message: $message")
            return Action.AskUser(reason = "敏感操作: $message", suggestion = "请确认后点击继续")
        }
        
        // 4. 解析 do(action="xxx", element=[x,y]) 格式 (AutoGLM 原生格式)
        val doElementPattern = Regex("""do\s*\(\s*action\s*=\s*"([\w\s]+)"\s*,\s*element\s*=\s*\[\s*(\d+)\s*,\s*(\d+)\s*\]""", RegexOption.IGNORE_CASE)
        val doElementMatch = doElementPattern.find(answerContent)
        if (doElementMatch != null) {
            val action = doElementMatch.groupValues[1].lowercase().trim()
            val normX = doElementMatch.groupValues[2].toIntOrNull() ?: 500
            val normY = doElementMatch.groupValues[3].toIntOrNull() ?: 500
            
            Log.d(TAG, "解析 do() element 格式: action=$action, norm=($normX,$normY)")
            
            // 转换归一化坐标到像素坐标
            val (pixelX, pixelY) = normalizedToPixel(normX, normY)
            Log.d(TAG, "转换为像素坐标: ($pixelX, $pixelY)")
            
            return when (action) {
                "tap", "click" -> Action.Tap(pixelX, pixelY)
                "long press", "longpress" -> Action.LongPress(pixelX, pixelY)
                "double tap", "doubletap" -> Action.Tap(pixelX, pixelY)  // 双击暂时用普通点击
                else -> null
            }
        }
        
        // 4. 解析 do(action="Swipe", start=[x1,y1], end=[x2,y2]) 格式
        val swipePattern = Regex("""do\s*\(\s*action\s*=\s*"Swipe"\s*,\s*start\s*=\s*\[\s*(\d+)\s*,\s*(\d+)\s*\]\s*,\s*end\s*=\s*\[\s*(\d+)\s*,\s*(\d+)\s*\]""", RegexOption.IGNORE_CASE)
        val swipeMatch = swipePattern.find(answerContent)
        if (swipeMatch != null) {
            val normX1 = swipeMatch.groupValues[1].toIntOrNull() ?: 500
            val normY1 = swipeMatch.groupValues[2].toIntOrNull() ?: 750
            val normX2 = swipeMatch.groupValues[3].toIntOrNull() ?: 500
            val normY2 = swipeMatch.groupValues[4].toIntOrNull() ?: 250
            
            val (x1, y1) = normalizedToPixel(normX1, normY1)
            val (x2, y2) = normalizedToPixel(normX2, normY2)
            
            Log.d(TAG, "解析 Swipe: ($x1,$y1) -> ($x2,$y2)")
            return Action.Swipe(x1, y1, x2, y2, 300)
        }
        
        // 5. 解析 do(action="Type", text="xxx") 格式
        val typePattern = Regex("""do\s*\(\s*action\s*=\s*"Type(?:_Name)?"\s*,\s*text\s*=\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
        val typeMatch = typePattern.find(answerContent)
        if (typeMatch != null) {
            val text = typeMatch.groupValues[1]
            Log.d(TAG, "解析 Type: $text")
            return Action.Input(text)
        }
        
        // 5.1 解析 do(action="Launch", app="xxx") 格式
        val launchPattern = Regex("""do\s*\(\s*action\s*=\s*"Launch"\s*,\s*app\s*=\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
        val launchMatch = launchPattern.find(answerContent)
        if (launchMatch != null) {
            val appName = launchMatch.groupValues[1]
            Log.d(TAG, "解析 Launch: app=$appName")
            return Action.Launch(appName)
        }
        
        // 6. 解析简单操作
        val simpleActionPattern = Regex("""do\s*\(\s*action\s*=\s*"(\w+)"\s*(?:,\s*duration\s*=\s*"?(\d+)[^"]*"?)?\s*\)""", RegexOption.IGNORE_CASE)
        val simpleMatch = simpleActionPattern.find(answerContent)
        if (simpleMatch != null) {
            val action = simpleMatch.groupValues[1].lowercase()
            Log.d(TAG, "解析简单操作: $action")
            
            return when (action) {
                "back" -> Action.Back
                "home" -> Action.Home
                "enter" -> Action.Enter
                "wait" -> {
                    val seconds = simpleMatch.groupValues[2].toIntOrNull() ?: 2
                    Action.Wait(seconds * 1000L)
                }
                else -> null
            }
        }
        
        // 7. 检查关键词
        if (lowerContent.contains("任务完成") || lowerContent.contains("已完成") || 
            lowerContent.contains("task completed") || lowerContent.contains("finish")) {
            return Action.Done()
        }
        
        if (lowerContent.contains("返回") && !lowerContent.contains("返回主")) {
            return Action.Back
        }
        
        if (lowerContent.contains("主屏幕") || lowerContent.contains("回到主") || 
            lowerContent.contains("home")) {
            return Action.Home
        }
        
        // 8. 尝试从坐标格式提取（兼容旧格式）
        // [x, y] 格式（归一化坐标）
        val bracketPattern = Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*\]""")
        val bracketMatch = bracketPattern.findAll(answerContent).lastOrNull()
        if (bracketMatch != null) {
            val x = bracketMatch.groupValues[1].toIntOrNull() ?: return null
            val y = bracketMatch.groupValues[2].toIntOrNull() ?: return null
            
            // 判断是归一化坐标还是像素坐标
            if (x <= 999 && y <= 999) {
                val (pixelX, pixelY) = normalizedToPixel(x, y)
                Log.d(TAG, "从 [x,y] 提取归一化坐标: [$x,$y] -> ($pixelX,$pixelY)")
                return Action.Tap(pixelX, pixelY)
            } else {
                Log.d(TAG, "从 [x,y] 提取像素坐标: ($x, $y)")
                return Action.Tap(x, y)
            }
        }
        
        // (x, y) 格式（像素坐标）
        val parenPattern = Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
        val parenMatch = parenPattern.findAll(content).lastOrNull()
        if (parenMatch != null) {
            val x = parenMatch.groupValues[1].toIntOrNull() ?: return null
            val y = parenMatch.groupValues[2].toIntOrNull() ?: return null
            if (x in 0..3000 && y in 0..5000) {
                Log.d(TAG, "从 (x,y) 提取像素坐标: ($x, $y)")
                return Action.Tap(x, y)
            }
        }
        
        return null
    }
    
    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        return try {
            val reader = com.google.gson.stream.JsonReader(java.io.StringReader(responseBody))
            reader.isLenient = true
            val json = JsonParser.parseReader(reader).asJsonObject
            val error = json.getAsJsonObject("error")
            val message = error?.get("message")?.asString ?: responseBody
            "API 错误 ($statusCode): $message"
        } catch (e: Exception) {
            "API 错误 ($statusCode): $responseBody"
        }
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
    
    private fun extractJson(content: String): String {
        // 尝试多种方式提取 JSON
        
        // 1. 查找 ```json 代码块
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val codeBlockMatch = codeBlockPattern.find(content)
        if (codeBlockMatch != null) {
            val extracted = codeBlockMatch.groupValues[1].trim()
            if (extracted.startsWith("{")) {
                return extracted
            }
        }
        
        // 2. 查找第一个完整的 JSON 对象
        var braceCount = 0
        var jsonStart = -1
        var jsonEnd = -1
        
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (jsonStart == -1) jsonStart = i
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && jsonStart != -1) {
                        jsonEnd = i
                        break
                    }
                }
            }
        }
        
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd + 1)
        }
        
        // 3. 简单的花括号匹配
        val simpleStart = content.indexOf('{')
        val simpleEnd = content.lastIndexOf('}')
        if (simpleStart != -1 && simpleEnd > simpleStart) {
            return content.substring(simpleStart, simpleEnd + 1)
        }
        
        // 4. 返回原内容，让后续解析处理错误
        return content.trim()
    }
}

