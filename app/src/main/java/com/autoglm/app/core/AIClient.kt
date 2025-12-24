package com.autoglm.app.core

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.autoglm.app.util.FileLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 重试配置 */
data class RetryConfig(
        val maxRetries: Int = 3,
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 10000,
        val multiplier: Double = 2.0
)

/** AI API 客户端 支持多 API 轮询、随机选择、故障转移 */
class AIClient(
        private val apiConfigs: List<ApiConfig>,
        private val retryConfig: RetryConfig = RetryConfig()
) {
        companion object {
                private const val TAG = "AIClient"
        }

        private val enabledConfigs: List<ApiConfig>
                get() = apiConfigs.filter { it.enabled }

        private val client =
                OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()

        private val gson = Gson()

        // 记录失败的 API，避免短时间内重复使用
        private val failedApis = mutableMapOf<String, Long>()
        private val failureCooldownMs = 60_000L // 失败后冷却 60 秒

        /** 分析屏幕截图并获取下一步动作 使用多 API 轮询策略 */
        suspend fun analyzeScreenAndPlan(
                screenshot: Bitmap,
                task: String,
                previousActions: List<String> = emptyList()
        ): AIResponse =
                withContext(Dispatchers.IO) {
                        val availableConfigs = getAvailableConfigs()

                        if (availableConfigs.isEmpty()) {
                                throw Exception("没有可用的 API 配置，请在设置中添加")
                        }

                        // 随机打乱顺序（考虑优先级）
                        val shuffledConfigs =
                                availableConfigs
                                        .sortedByDescending { it.priority }
                                        .groupBy { it.priority }
                                        .flatMap { (_, configs) -> configs.shuffled() }

                        var lastException: Exception? = null

                        for (config in shuffledConfigs) {
                                try {
                                        Log.d(
                                                TAG,
                                                "尝试使用 API: ${config.name} (${config.provider.displayName})"
                                        )
                                        val result =
                                                makeApiCallWithRetry(
                                                        config,
                                                        screenshot,
                                                        task,
                                                        previousActions
                                                )
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

        /** 获取当前可用的 API 配置（排除冷却中的） */
        private fun getAvailableConfigs(): List<ApiConfig> {
                val now = System.currentTimeMillis()
                return enabledConfigs.filter { config ->
                        val failTime = failedApis[config.id]
                        failTime == null || (now - failTime) > failureCooldownMs
                }
        }

        /** 带重试的 API 调用 */
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
                                Log.d(
                                        TAG,
                                        "API ${config.name} 尝试 ${attempt + 1}/${retryConfig.maxRetries}"
                                )
                                return makeApiCall(config, screenshot, task, previousActions)
                        } catch (e: Exception) {
                                lastException = e

                                if (!isRetryableError(e)) {
                                        throw e
                                }

                                if (attempt < retryConfig.maxRetries - 1) {
                                        Log.d(TAG, "等待 ${delayMs}ms 后重试...")
                                        delay(delayMs)
                                        delayMs =
                                                (delayMs * retryConfig.multiplier)
                                                        .toLong()
                                                        .coerceAtMost(retryConfig.maxDelayMs)
                                }
                        }
                }

                throw lastException ?: Exception("API 调用失败")
        }

        /** 判断错误是否可重试 */
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
                        // 解析错误也可以重试（可能是模型输出格式不对或被截断）
                        message.contains("无法解析") -> true
                        message.contains("parse error") -> true
                        message.contains("json") -> true
                        else -> false
                }
        }

        /** 判断是否为致命错误（账户级别问题，不应无限重试） 包括：余额不足、额度耗尽、认证失败、账户被禁用等 */
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
                        message.contains("account") &&
                                (message.contains("disabled") ||
                                        message.contains("suspended") ||
                                        message.contains("banned")) -> true
                        message.contains("账户") -> true
                        message.contains("账号") -> true
                        else -> false
                }
        }

        /** 执行 API 调用 */
        private suspend fun makeApiCall(
                config: ApiConfig,
                screenshot: Bitmap,
                task: String,
                previousActions: List<String>
        ): AIResponse {
                val base64Image = bitmapToBase64(screenshot)

                // 根据不同提供商构建请求
                val (request, parseResponse) =
                        when (config.provider) {
                                AIProvider.CLAUDE ->
                                        buildClaudeRequest(
                                                config,
                                                base64Image,
                                                task,
                                                previousActions
                                        )
                                AIProvider.GEMINI ->
                                        buildGeminiRequest(
                                                config,
                                                base64Image,
                                                task,
                                                previousActions
                                        )
                                else ->
                                        buildOpenAICompatibleRequest(
                                                config,
                                                base64Image,
                                                task,
                                                previousActions
                                        )
                        }

                FileLogger.logApiRequest(
                        TAG,
                        config.provider.displayName,
                        config.model,
                        config.endpoint
                )

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("空响应")

                FileLogger.logApiResponse(TAG, response.code, responseBody)

                if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(responseBody, response.code)
                        throw Exception(errorMsg)
                }

                return parseResponse(responseBody)
        }

        /** 构建 OpenAI 兼容格式请求（智谱、OpenAI、通义千问等） */
        private fun buildOpenAICompatibleRequest(
                config: ApiConfig,
                base64Image: String,
                task: String,
                previousActions: List<String>
        ): Pair<Request, (String) -> AIResponse> {
                val systemPrompt = getSystemPrompt()
                val userMessage = getUserMessage(task, previousActions)

                val requestJson =
                        JsonObject().apply {
                                addProperty("model", config.model)
                                addProperty("stream", false) // 禁用流式传输
                                add(
                                        "messages",
                                        gson.toJsonTree(
                                                listOf(
                                                        mapOf(
                                                                "role" to "system",
                                                                "content" to systemPrompt
                                                        ),
                                                        mapOf(
                                                                "role" to "user",
                                                                "content" to
                                                                        listOf(
                                                                                mapOf(
                                                                                        "type" to
                                                                                                "text",
                                                                                        "text" to
                                                                                                userMessage
                                                                                ),
                                                                                mapOf(
                                                                                        "type" to
                                                                                                "image_url",
                                                                                        "image_url" to
                                                                                                mapOf(
                                                                                                        "url" to
                                                                                                                "data:image/jpeg;base64,$base64Image"
                                                                                                )
                                                                                )
                                                                        )
                                                        )
                                                )
                                        )
                                )
                                addProperty("max_tokens", 1024)
                                addProperty("temperature", 0.1)
                        }

                val request =
                        Request.Builder()
                                .url(config.endpoint)
                                .addHeader("Authorization", "Bearer ${config.apiKey}")
                                .addHeader("Content-Type", "application/json")
                                .post(
                                        requestJson
                                                .toString()
                                                .toRequestBody("application/json".toMediaType())
                                )
                                .build()

                return Pair(request, ::parseOpenAIResponse)
        }

        /** 构建 Claude 请求 */
        private fun buildClaudeRequest(
                config: ApiConfig,
                base64Image: String,
                task: String,
                previousActions: List<String>
        ): Pair<Request, (String) -> AIResponse> {
                val userMessage = getUserMessage(task, previousActions)

                val requestJson =
                        JsonObject().apply {
                                addProperty("model", config.model)
                                addProperty("max_tokens", 1024)
                                addProperty("stream", false) // 禁用流式传输
                                addProperty("system", getSystemPrompt())
                                add(
                                        "messages",
                                        gson.toJsonTree(
                                                listOf(
                                                        mapOf(
                                                                "role" to "user",
                                                                "content" to
                                                                        listOf(
                                                                                mapOf(
                                                                                        "type" to
                                                                                                "image",
                                                                                        "source" to
                                                                                                mapOf(
                                                                                                        "type" to
                                                                                                                "base64",
                                                                                                        "media_type" to
                                                                                                                "image/jpeg",
                                                                                                        "data" to
                                                                                                                base64Image
                                                                                                )
                                                                                ),
                                                                                mapOf(
                                                                                        "type" to
                                                                                                "text",
                                                                                        "text" to
                                                                                                userMessage
                                                                                )
                                                                        )
                                                        )
                                                )
                                        )
                                )
                        }

                val request =
                        Request.Builder()
                                .url(config.endpoint)
                                .addHeader("x-api-key", config.apiKey)
                                .addHeader("anthropic-version", "2023-06-01")
                                .addHeader("Content-Type", "application/json")
                                .post(
                                        requestJson
                                                .toString()
                                                .toRequestBody("application/json".toMediaType())
                                )
                                .build()

                return Pair(request, ::parseClaudeResponse)
        }

        /** 构建 Gemini 请求 */
        private fun buildGeminiRequest(
                config: ApiConfig,
                base64Image: String,
                task: String,
                previousActions: List<String>
        ): Pair<Request, (String) -> AIResponse> {
                val userMessage = "${getSystemPrompt()}\n\n${getUserMessage(task, previousActions)}"

                val requestJson =
                        JsonObject().apply {
                                add(
                                        "contents",
                                        gson.toJsonTree(
                                                listOf(
                                                        mapOf(
                                                                "parts" to
                                                                        listOf(
                                                                                mapOf(
                                                                                        "text" to
                                                                                                userMessage
                                                                                ),
                                                                                mapOf(
                                                                                        "inline_data" to
                                                                                                mapOf(
                                                                                                        "mime_type" to
                                                                                                                "image/jpeg",
                                                                                                        "data" to
                                                                                                                base64Image
                                                                                                )
                                                                                )
                                                                        )
                                                        )
                                                )
                                        )
                                )
                        }

                val url = "${config.endpoint}?key=${config.apiKey}"

                val request =
                        Request.Builder()
                                .url(url)
                                .addHeader("Content-Type", "application/json")
                                .post(
                                        requestJson
                                                .toString()
                                                .toRequestBody("application/json".toMediaType())
                                )
                                .build()

                return Pair(request, ::parseGeminiResponse)
        }

        // 屏幕尺寸，用于坐标转换
        private var screenWidth = 1080
        private var screenHeight = 2400

        /** 设置屏幕尺寸（从截图获取） */
        fun setScreenSize(width: Int, height: Int) {
                screenWidth = width
                screenHeight = height
        }

        /** 将归一化坐标 (0-999) 转换为像素坐标 */
        private fun normalizedToPixel(normalizedX: Int, normalizedY: Int): Pair<Int, Int> {
                val pixelX = (normalizedX * screenWidth / 1000).coerceIn(0, screenWidth - 1)
                val pixelY = (normalizedY * screenHeight / 1000).coerceIn(0, screenHeight - 1)
                return Pair(pixelX, pixelY)
        }

        /** 统一系统提示词生成 - 自动判断是否使用 Mark */
        private fun getUnifiedSystemPrompt(): String {
                val dateFormat =
                        java.text.SimpleDateFormat("yyyy年MM月dd日 EEEE", java.util.Locale.CHINESE)
                val today = dateFormat.format(java.util.Date())

                return """
# AutoGLM Android 助手
今天是 $today

## 核心规则
1. **优先使用标记 (Mark)**：
    -   **必须优先**检查截图中是否包含**粉色数字标记**。
    -   **如果存在标记**：你**必须**使用 `mark=编号` 进行操作（如 `do(action="Tap", mark=5)`）。此时**严禁**使用坐标。
    -   **如果不存在标记**：使用**归一化坐标** [0-1000]（如 `do(action="Tap", element=[500,500])`）。
2. **坐标仅限滑动**：`Swipe` 操作始终使用 `[x,y]` 坐标。
3. **思考后行动**：先在 `<think>` 中简述分析，再在 `<act>` 中输出单行或多行代码。
4. 打开应用APP必须优先使用Launch，失败后才使用Tap。
5. **回复格式**: 你的回复中必须包含 `<think>` 和 `<act>` 两部分，且 `<act>` 中必须包含至少一个动作。
6. **输出长度**: 你的回复不长度不应该超过512 tokens

## 动作指令表
| 意图 | 指令格式 | 说明 |
| :--- | :--- | :--- |
| **点击(标记)** | `do(action="Tap", mark=编号)` | **首选**。仅当界面有标记时使用。 |
| **点击(坐标)** | `do(action="Tap", element=[x,y])` | **备选**。仅当界面无标记时使用（范围0-1000）。 |
| **滑动** | `do(action="Swipe", start=[x1,y1], end=[x2,y2])` | 必须使用坐标。 |
| **输入** | `do(action="Type", text="内容")` | |
| **按键** | `do(action="Enter/Back/Home")` | |
| **打开** | `do(action="Launch", app="应用名")` | |
| **询问** | `ask_user(reason="原因")` | |
| **结束** | `finish(message="完成说明")` | |

## 规则
- Launch 失败过 → 改用 Tap 点击图标
- 无关页面 → 执行 Back
- 找不到目标 → Swipe 滑动查找
- 涉及密码/验证码/支付 → ask_user

## 输出示例
**场景1：界面有标记（优先）**
<think>界面上有粉色标记，我想点击第5个选项(标记5)。</think>
<act>do(action="Tap", mark=5)</act>

**场景2：界面无标记（使用坐标）**
<think>界面无标记，需要点击中间的按钮。</think>
<act>do(action="Tap", element=[500,500])</act>

**场景3：多步操作**
<think>先点击搜索框(标记3)，输入内容，再点搜索。</think>
<act>
do(action="Tap", mark=3)
do(action="Type", text="天气")
do(action="Enter")
</act>

---
请根据当前截图状态执行下一步。
        """.trimIndent()
        }

        /** 统一用户消息生成 */
        private fun getUnifiedUserMessage(task: String, previousActions: List<String>): String =
                buildString {
                        append("任务: $task\n")
                        if (previousActions.isNotEmpty()) {
                                append("已执行: ${previousActions.takeLast(3).joinToString(" → ")}\n")
                                // 检查 Home 循环
                                val homeCount =
                                        previousActions.takeLast(3).count { it.contains("Home") }
                                if (homeCount >= 2) {
                                        append("【禁止再按Home！】\n")
                                }
                                // 检查 Launch 失败
                                if (previousActions.any { it.contains("Launch失败") }) {
                                        append("【Launch失败过，请改用 Tap 点击图标】\n")
                                }
                        }
                        append("请分析截图并执行下一步。如果看到粉色数字标记，请优先使用 mark=编号。")
                }

        // 兼容旧调用 - 无障碍模式
        private fun getSystemPrompt(): String = getUnifiedSystemPrompt()
        private fun getUserMessage(task: String, previousActions: List<String>): String =
                getUnifiedUserMessage(task, previousActions)

        private fun parseOpenAIResponse(responseBody: String): AIResponse {
                val json = JsonParser.parseString(responseBody).asJsonObject
                val content =
                        json.getAsJsonArray("choices")[0]
                                .asJsonObject
                                .getAsJsonObject("message")
                                .get("content")
                                .asString
                return parseUnifiedResponse(content)
        }

        private fun parseClaudeResponse(responseBody: String): AIResponse {
                val json = JsonParser.parseString(responseBody).asJsonObject
                val content = json.getAsJsonArray("content")[0].asJsonObject.get("text").asString
                return parseUnifiedResponse(content)
        }

        private fun parseGeminiResponse(responseBody: String): AIResponse {
                val json = JsonParser.parseString(responseBody).asJsonObject
                val content =
                        json.getAsJsonArray("candidates")[0]
                                        .asJsonObject
                                        .getAsJsonObject("content")
                                        .getAsJsonArray("parts")[0]
                                .asJsonObject.get("text")
                                .asString
                return parseUnifiedResponse(content)
        }

        private fun parseUnifiedResponse(content: String): AIResponse {
                Log.i(TAG, "========== AI 返回内容 (Unified) ==========")
                Log.i(TAG, content)
                Log.i(TAG, "===========================================")
                FileLogger.d(TAG, "AI 原始响应: ${content.take(500)}")

                // 1. 提取思考过程 (think 标签)
                val thinkPattern =
                        Regex("""<think>\s*(.*?)\s*</think>""", RegexOption.DOT_MATCHES_ALL)
                var thinking = thinkPattern.find(content)?.groupValues?.get(1)?.trim()

                // 如果没有标签，尝试从开头提取 (兼容 deepseek 等模型)
                if (thinking == null) {
                        val actIndex = content.indexOf("<act>")
                        val doIndex = content.indexOf("do(", ignoreCase = true)
                        val answerIndex = content.indexOf("<answer>")

                        // 找到最早出现的动作指示
                        val indices = listOf(actIndex, doIndex, answerIndex).filter { it != -1 }
                        val endIndex = if (indices.isNotEmpty()) indices.minOrNull() ?: -1 else -1

                        if (endIndex > 0) {
                                thinking = content.substring(0, endIndex).trim()
                                Log.d(TAG, "从开头提取到思考内容: ${thinking?.take(50)}...")
                        }
                }

                // 2. 提取 status (如果有)
                val statusPattern = Regex("""status:\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
                val status = statusPattern.find(content)?.groupValues?.get(1)?.trim() ?: ""

                // 3. 提取动作内容 (<act> 或 <answer> 或 全文)
                val actPattern = Regex("""<act>\s*(.*?)\s*</act>""", RegexOption.DOT_MATCHES_ALL)
                val answerPattern =
                        Regex("""<answer>\s*(.*?)\s*</answer>""", RegexOption.DOT_MATCHES_ALL)

                val actionContent =
                        actPattern.find(content)?.groupValues?.get(1)
                                ?: answerPattern.find(content)?.groupValues?.get(1) ?: content

                val actions = mutableListOf<Action>()

                // 4. 解析动作列表
                // 4.1 TapMark: do(action="Tap", mark=N)
                val tapMarkPattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"Tap"\s*,\s*mark\s*=\s*(\d+)\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                tapMarkPattern.findAll(actionContent).forEach { match ->
                        val markId = match.groupValues[1].toIntOrNull() ?: 0
                        actions.add(Action.TapMark(markId))
                        Log.d(TAG, "解析到 TapMark: $markId")
                }

                // 4.2 Tap: do(action="Tap", element=[x,y]) 或 Tap(x,y)
                // 正则匹配 element=[x,y] 或 element=(x,y)
                val tapCoordPattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"Tap"\s*,\s*element\s*=\s*[\[\(]\s*(\d+)\s*,\s*(\d+)\s*[\]\)]\s*(?:,\s*message\s*=\s*"[^"]*")?\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                tapCoordPattern.findAll(actionContent).forEach { match ->
                        val x = match.groupValues[1].toIntOrNull() ?: 0
                        val y = match.groupValues[2].toIntOrNull() ?: 0

                        // 强制归一化处理：如果坐标 <= 1000 (且不为0)，视为归一化坐标
                        val (pixelX, pixelY) =
                                if (x <= 1000 && y <= 1000 && (x != 0 || y != 0)) {
                                        normalizedToPixel(x, y)
                                } else {
                                        Pair(x, y)
                                }

                        actions.add(Action.Tap(pixelX, pixelY))
                        Log.d(TAG, "解析到 Tap: raw=($x,$y) -> pixel=($pixelX,$pixelY)")
                }

                // 4.2.5 Malformed Tap mark=[x,y] (Gemini 常见错误)
                val tapMarkCoordPattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"Tap"\s*,\s*mark\s*=\s*[\[\(](\d+)\s*,\s*(\d+)[\]\)]\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                tapMarkCoordPattern.findAll(actionContent).forEach { match ->
                        val x = match.groupValues[1].toIntOrNull() ?: 0
                        val y = match.groupValues[2].toIntOrNull() ?: 0
                        val (pixelX, pixelY) =
                                if (x <= 1000 && y <= 1000 && (x != 0 || y != 0)) {
                                        normalizedToPixel(x, y)
                                } else {
                                        Pair(x, y)
                                }
                        actions.add(Action.Tap(pixelX, pixelY))
                        Log.d(TAG, "解析到 Malformed Tap mark=[$x,$y] -> Tap($pixelX,$pixelY)")
                }

                // 4.3 Swipe: do(action="Swipe", start=[x1,y1], end=[x2,y2])
                val swipePattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"Swipe"\s*,\s*start\s*=\s*[\[\(](\d+)\s*,\s*(\d+)[\]\)]\s*,\s*end\s*=\s*[\[\(](\d+)\s*,\s*(\d+)[\]\)]""",
                                RegexOption.IGNORE_CASE
                        )
                swipePattern.findAll(actionContent).forEach { match ->
                        val x1 = match.groupValues[1].toIntOrNull() ?: 0
                        val y1 = match.groupValues[2].toIntOrNull() ?: 0
                        val x2 = match.groupValues[3].toIntOrNull() ?: 0
                        val y2 = match.groupValues[4].toIntOrNull() ?: 0

                        // 关键修复：如果任意一个坐标 > 1000，说明 AI 使用的是像素坐标，全部不归一化
                        val isPixelCoords = x1 > 1000 || y1 > 1000 || x2 > 1000 || y2 > 1000

                        val (px1, py1, px2, py2) =
                                if (isPixelCoords) {
                                        // 像素坐标，直接使用
                                        listOf(x1, y1, x2, y2)
                                } else {
                                        // 归一化坐标 (0-1000)，需要转换
                                        val (p1, p2) = normalizedToPixel(x1, y1)
                                        val (p3, p4) = normalizedToPixel(x2, y2)
                                        listOf(p1, p2, p3, p4)
                                }

                        actions.add(Action.Swipe(px1, py1, px2, py2))
                        Log.d(
                                TAG,
                                "解析到 Swipe: raw=($x1,$y1)->($x2,$y2) -> pixel=($px1,$py1)->($px2,$py2) [isPixel=$isPixelCoords]"
                        )
                }

                // 4.4 Input: do(action="Type", text="xxx")
                val typePattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"Type(?:_Name)?"\s*,\s*text\s*=\s*"([^"]*)"\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                typePattern.findAll(actionContent).forEach { match ->
                        actions.add(Action.Input(match.groupValues[1]))
                        Log.d(TAG, "解析到 Input: ${match.groupValues[1]}")
                }

                // 4.5 Launch: do(action="Launch", app="xxx")
                val launchPattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"Launch"\s*,\s*app\s*=\s*"([^"]*)"\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                launchPattern.findAll(actionContent).forEach { match ->
                        actions.add(Action.Launch(match.groupValues[1]))
                        Log.d(TAG, "解析到 Launch: ${match.groupValues[1]}")
                }

                // 4.6 Simple Actions (Back, Home, Enter, Wait, Done, AskUser)
                // Finish
                val finishPattern =
                        Regex(
                                """finish\s*\(\s*message\s*=\s*"([^"]*)"\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                finishPattern.findAll(actionContent).forEach { match ->
                        actions.add(Action.Done(match.groupValues[1]))
                        Log.d(TAG, "解析到 Finish: ${match.groupValues[1]}")
                }

                // Ask User
                val askUserPattern =
                        Regex(
                                """ask_user\s*\(\s*reason\s*=\s*"([^"]*)"(?:\s*,\s*suggestion\s*=\s*"([^"]*)")?\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                askUserPattern.findAll(actionContent).forEach { match ->
                        actions.add(
                                Action.AskUser(
                                        match.groupValues[1],
                                        match.groupValues.getOrNull(2) ?: ""
                                )
                        )
                        Log.d(TAG, "解析到 AskUser: ${match.groupValues[1]}")
                }

                // Back/Home/Enter/Wait
                val simplePattern =
                        Regex(
                                """do\s*\(\s*action\s*=\s*"(Back|Home|Enter|Wait)"(?:\s*,\s*(?:duration|milliseconds)\s*=\s*"?(\d+)"?)?\s*\)""",
                                RegexOption.IGNORE_CASE
                        )
                simplePattern.findAll(actionContent).forEach { match ->
                        val type = match.groupValues[1].lowercase()
                        val param = match.groupValues.getOrNull(2)
                        when (type) {
                                "back" -> actions.add(Action.Back)
                                "home" -> actions.add(Action.Home)
                                "enter" -> actions.add(Action.Enter)
                                "wait" -> actions.add(Action.Wait((param?.toLongOrNull() ?: 1000L)))
                        }
                        Log.d(TAG, "解析到 SimpleAction: $type")
                }

                // 5. 备用解析 (如果没解析出 do() 指令)
                if (actions.isEmpty()) {
                        Log.w(TAG, "未解析到标准格式动作，尝试备用解析...")
                        // 简单文本匹配
                        if (actionContent.contains("任务完成") || actionContent.contains("finish")) {
                                actions.add(Action.Done())
                        } else if (actionContent.contains("返回")) {
                                actions.add(Action.Back)
                        } else if (actionContent.contains("主屏幕") || actionContent.contains("home")
                        ) {
                                actions.add(Action.Home)
                        }

                        // 尝试提取纯坐标 [x,y] 或 (x,y)
                        val bracketPattern = Regex("""[\[\(]\s*(\d+)\s*,\s*(\d+)\s*[\]\)]""")
                        val match = bracketPattern.findAll(actionContent).lastOrNull()
                        if (match != null && actions.isEmpty()) {
                                val x = match.groupValues[1].toInt()
                                val y = match.groupValues[2].toInt()
                                val (px, py) =
                                        if (x <= 1000 && y <= 1000) normalizedToPixel(x, y)
                                        else Pair(x, y)
                                actions.add(Action.Tap(px, py))
                                Log.d(TAG, "备用解析提取坐标: ($px, $py)")
                        }
                }

                if (actions.isEmpty()) {
                        // 最后一搏：尝试 JSON 解析 (兼容旧模型)
                        val json = extractJson(content)
                        if (json.startsWith("{")) {
                                try {
                                        val jsonAction =
                                                parseJsonAction(json) // 需要把之前的 JSON 解析逻辑提取出来
                                        actions.add(jsonAction)
                                } catch (e: Exception) {
                                        Log.w(TAG, "JSON 备用解析失败: ${e.message}")
                                }
                        }
                }

                if (actions.isEmpty()) {
                        throw Exception("无法解析 AI 响应，未找到有效动作。")
                }

                return AIResponse(
                        action = actions.first(),
                        actions = actions,
                        status = status,
                        thinking = thinking,
                        rawResponse = content.take(1000)
                )
        }

        /** 辅助方法：解析单个 JSON 动作 */
        private fun parseJsonAction(jsonContent: String): Action {
                val reader = com.google.gson.stream.JsonReader(java.io.StringReader(jsonContent))
                reader.isLenient = true
                val actionJson = JsonParser.parseReader(reader).asJsonObject
                val actionType =
                        actionJson.get("action")?.asString ?: throw Exception("Missing action type")

                return when (actionType.lowercase()) {
                        "tap", "click" -> {
                                val x = actionJson.get("x")?.asInt ?: 0
                                val y = actionJson.get("y")?.asInt ?: 0
                                val (px, py) =
                                        if (x <= 1000 && y <= 1000) normalizedToPixel(x, y)
                                        else Pair(x, y)
                                Action.Tap(px, py)
                        }
                        "input", "type" -> Action.Input(actionJson.get("text")?.asString ?: "")
                        "back" -> Action.Back
                        "home" -> Action.Home
                        "wait" -> Action.Wait(actionJson.get("duration")?.asLong ?: 1000L)
                        "done", "finish" -> Action.Done()
                        else -> throw Exception("Unknown action: $actionType")
                }
        }

        private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
                return try {
                        val reader =
                                com.google.gson.stream.JsonReader(
                                        java.io.StringReader(responseBody)
                                )
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

        // ==================== Shizuku 模式专用方法 ====================

        /** Shizuku 模式分析屏幕并获取下一步动作 使用包名启动应用的专用提示词 */
        suspend fun analyzeScreenAndPlanForShizuku(
                screenshot: Bitmap,
                task: String,
                previousActions: List<String> = emptyList(),
                uiElements: List<UIElement> = emptyList()
        ): AIResponse =
                withContext(Dispatchers.IO) {
                        val availableConfigs = getAvailableConfigs()

                        if (availableConfigs.isEmpty()) {
                                throw Exception("没有可用的 API 配置，请在设置中添加")
                        }

                        val shuffledConfigs =
                                availableConfigs
                                        .sortedByDescending { it.priority }
                                        .groupBy { it.priority }
                                        .flatMap { (_, configs) -> configs.shuffled() }

                        var lastException: Exception? = null

                        for (config in shuffledConfigs) {
                                try {
                                        Log.d(TAG, "Shizuku模式: 尝试使用 API: ${config.name}")
                                        val result =
                                                makeShizukuApiCallWithRetry(
                                                        config,
                                                        screenshot,
                                                        task,
                                                        previousActions,
                                                        uiElements
                                                )
                                        failedApis.remove(config.id)
                                        return@withContext result
                                } catch (e: Exception) {
                                        Log.w(TAG, "API ${config.name} 调用失败: ${e.message}")
                                        lastException = e
                                        failedApis[config.id] = System.currentTimeMillis()
                                }
                        }

                        throw lastException ?: Exception("所有 API 调用均失败")
                }

        private suspend fun makeShizukuApiCallWithRetry(
                config: ApiConfig,
                screenshot: Bitmap,
                task: String,
                previousActions: List<String>,
                uiElements: List<UIElement>
        ): AIResponse {
                var lastException: Exception? = null
                var delayMs = retryConfig.initialDelayMs

                repeat(retryConfig.maxRetries) { attempt ->
                        try {
                                return makeShizukuApiCall(
                                        config,
                                        screenshot,
                                        task,
                                        previousActions,
                                        uiElements
                                )
                        } catch (e: Exception) {
                                lastException = e
                                if (!isRetryableError(e)) throw e
                                if (attempt < retryConfig.maxRetries - 1) {
                                        delay(delayMs)
                                        delayMs =
                                                (delayMs * retryConfig.multiplier)
                                                        .toLong()
                                                        .coerceAtMost(retryConfig.maxDelayMs)
                                }
                        }
                }
                throw lastException ?: Exception("API 调用失败")
        }

        private suspend fun makeShizukuApiCall(
                config: ApiConfig,
                screenshot: Bitmap,
                task: String,
                previousActions: List<String>,
                uiElements: List<UIElement>
        ): AIResponse {
                val base64Image = bitmapToBase64(screenshot)
                val systemPrompt = getShizukuSystemPrompt()
                val userMessage = getShizukuUserMessage(task, previousActions, uiElements)

                // 打印发送给 AI 的提示词
                Log.i(TAG, "===== Shizuku System Prompt =====")
                Log.i(TAG, systemPrompt)
                Log.i(TAG, "===== Shizuku User Message =====")
                Log.i(TAG, userMessage)
                Log.i(TAG, "=================================")

                // 调试日志：输出请求详情（API Key 掩码处理）
                val maskedKey =
                        if (config.apiKey.length > 8) {
                                config.apiKey.take(4) + "****" + config.apiKey.takeLast(4)
                        } else {
                                "****"
                        }

                // 判断是否是 Gemini API
                val isGemini =
                        config.endpoint.contains("googleapis.com") ||
                                config.endpoint.contains("gemini") ||
                                config.endpoint.contains("generativelanguage")

                Log.i(
                        TAG,
                        "Shizuku API 请求: endpoint=${config.endpoint}, model=${config.model}, key=$maskedKey, isGemini=$isGemini"
                )

                val request =
                        if (isGemini) {
                                // Gemini 格式请求
                                val combinedMessage = "$systemPrompt\n\n$userMessage"
                                val requestJson =
                                        JsonObject().apply {
                                                add(
                                                        "contents",
                                                        gson.toJsonTree(
                                                                listOf(
                                                                        mapOf(
                                                                                "parts" to
                                                                                        listOf(
                                                                                                mapOf(
                                                                                                        "text" to
                                                                                                                combinedMessage
                                                                                                ),
                                                                                                mapOf(
                                                                                                        "inline_data" to
                                                                                                                mapOf(
                                                                                                                        "mime_type" to
                                                                                                                                "image/jpeg",
                                                                                                                        "data" to
                                                                                                                                base64Image
                                                                                                                )
                                                                                                )
                                                                                        )
                                                                        )
                                                                )
                                                        )
                                                )
                                        }
                                // Gemini 使用 URL 参数传递 API Key
                                val url = "${config.endpoint}?key=${config.apiKey}"
                                Request.Builder()
                                        .url(url)
                                        .addHeader("Content-Type", "application/json")
                                        .post(
                                                requestJson
                                                        .toString()
                                                        .toRequestBody(
                                                                "application/json".toMediaType()
                                                        )
                                        )
                                        .build()
                        } else {
                                // OpenAI 兼容格式请求
                                val requestJson =
                                        JsonObject().apply {
                                                addProperty("model", config.model)
                                                addProperty("stream", false)
                                                add(
                                                        "messages",
                                                        gson.toJsonTree(
                                                                listOf(
                                                                        mapOf(
                                                                                "role" to "system",
                                                                                "content" to
                                                                                        systemPrompt
                                                                        ),
                                                                        mapOf(
                                                                                "role" to "user",
                                                                                "content" to
                                                                                        listOf(
                                                                                                mapOf(
                                                                                                        "type" to
                                                                                                                "text",
                                                                                                        "text" to
                                                                                                                userMessage
                                                                                                ),
                                                                                                mapOf(
                                                                                                        "type" to
                                                                                                                "image_url",
                                                                                                        "image_url" to
                                                                                                                mapOf(
                                                                                                                        "url" to
                                                                                                                                "data:image/jpeg;base64,$base64Image"
                                                                                                                )
                                                                                                )
                                                                                        )
                                                                        )
                                                                )
                                                        )
                                                )
                                                addProperty("max_tokens", 1024)
                                                addProperty("temperature", 0.1)
                                        }
                                // OpenAI 兼容格式使用 Bearer token
                                Request.Builder()
                                        .url(config.endpoint)
                                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                                        .addHeader("Content-Type", "application/json")
                                        .post(
                                                requestJson
                                                        .toString()
                                                        .toRequestBody(
                                                                "application/json".toMediaType()
                                                        )
                                        )
                                        .build()
                        }

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("空响应")

                if (!response.isSuccessful) {
                        throw Exception(parseErrorMessage(responseBody, response.code))
                }

                // 根据 API 类型选择不同的解析方法
                return if (isGemini) {
                        parseGeminiResponse(responseBody)
                } else {
                        parseOpenAIResponse(responseBody)
                }
        }
        // Shizuku 模式 - 使用统一函数
        private fun getShizukuSystemPrompt(): String = getUnifiedSystemPrompt()
        private fun getShizukuUserMessage(
                task: String,
                previousActions: List<String>,
                uiElements: List<UIElement>
        ): String = getUnifiedUserMessage(task, previousActions)
}
