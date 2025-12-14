package com.autoglm.app.core

/**
 * AI 服务商
 * 每个服务商有固定的 API 端点和多个可选模型
 */
enum class AIProvider(
    val displayName: String,
    val endpoint: String,
    val defaultModels: List<ModelInfo>,  // 预设模型列表
    val authHeader: String = "Authorization",
    val authPrefix: String = "Bearer ",
    val supportsCustomModel: Boolean = true  // 是否支持自定义模型
) {
    ZHIPU(
        displayName = "智谱 AI",
        endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        defaultModels = listOf(
            ModelInfo("glm-4v", "GLM-4V (推荐)", true),
            ModelInfo("glm-4v-plus", "GLM-4V-Plus", true)
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        endpoint = "https://api.openai.com/v1/chat/completions",
        defaultModels = listOf(
            ModelInfo("gpt-4o", "GPT-4o (推荐)", true),
            ModelInfo("gpt-4o-mini", "GPT-4o Mini", true),
            ModelInfo("gpt-4-turbo", "GPT-4 Turbo", true)
        )
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        endpoint = "https://api.anthropic.com/v1/messages",
        defaultModels = listOf(
            ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet (推荐)", true),
            ModelInfo("claude-3-opus-20240229", "Claude 3 Opus", true),
            ModelInfo("claude-3-haiku-20240307", "Claude 3 Haiku", true)
        ),
        authHeader = "x-api-key",
        authPrefix = ""
    ),
    GEMINI(
        displayName = "Google Gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        defaultModels = listOf(
            ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash (推荐)", true),
            ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", true),
            ModelInfo("gemini-2.0-flash-exp", "Gemini 2.0 Flash", true)
        )
    ),
    QWEN(
        displayName = "通义千问",
        endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        defaultModels = listOf(
            ModelInfo("qwen-vl-max", "Qwen-VL-Max (推荐)", true),
            ModelInfo("qwen-vl-plus", "Qwen-VL-Plus", true)
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        endpoint = "https://api.deepseek.com/v1/chat/completions",
        defaultModels = listOf(
            ModelInfo("deepseek-chat", "DeepSeek Chat", false)  // DeepSeek 暂不支持视觉
        )
    ),
    MOONSHOT(
        displayName = "Moonshot (Kimi)",
        endpoint = "https://api.moonshot.cn/v1/chat/completions",
        defaultModels = listOf(
            ModelInfo("moonshot-v1-128k", "Moonshot V1 128K", false)  // Kimi 暂不支持视觉
        )
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI 兼容接口",
        endpoint = "",  // 用户自定义
        defaultModels = emptyList(),
        supportsCustomModel = true
    );
    
    companion object {
        fun fromName(name: String): AIProvider {
            return entries.find { it.name == name } ?: ZHIPU
        }
        
        /**
         * 获取支持视觉的提供商
         */
        fun getVisionProviders(): List<AIProvider> {
            return entries.filter { provider ->
                provider.defaultModels.any { it.supportsVision } || provider == OPENAI_COMPATIBLE
            }
        }
    }
}

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,           // 模型 ID，如 "gpt-4o"
    val displayName: String,  // 显示名称，如 "GPT-4o (推荐)"
    val supportsVision: Boolean = true  // 是否支持视觉
)

/**
 * API 配置项
 */
data class ApiConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,           // 用户自定义名称
    val provider: AIProvider,
    val model: String,          // 模型 ID
    val apiKey: String,
    val customEndpoint: String? = null,  // 仅 OPENAI_COMPATIBLE 使用
    val enabled: Boolean = true,
    val priority: Int = 0
) {
    val endpoint: String
        get() = when (provider) {
            AIProvider.OPENAI_COMPATIBLE -> customEndpoint ?: ""
            AIProvider.GEMINI -> provider.endpoint.replace("{model}", model)
            else -> provider.endpoint
        }
    
    val displayInfo: String
        get() = "${provider.displayName} / $model"
}
