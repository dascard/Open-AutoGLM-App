package com.autoglm.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.autoglm.app.core.ApiConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 配置管理器 支持多 API 配置的存储和管理 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "autoglm_prefs"
        private const val KEY_API_CONFIGS = "api_configs"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_COMMAND_HISTORY = "command_history"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_DEV_MODE = "dev_mode"
        private const val KEY_CHAT_HISTORY = "chat_history"
        private const val MAX_HISTORY_SIZE = 20
        private const val MAX_CHAT_SESSIONS = 50
        private const val KEY_MAX_STEPS = "max_steps"
        private const val KEY_EXECUTION_MODE = "execution_mode"
        private const val KEY_CUSTOM_TASK_LISTS = "custom_task_lists"
        private const val KEY_VISUAL_STRATEGY = "visual_strategy"
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** API 配置列表 */
    var apiConfigs: List<ApiConfig>
        get() {
            val json = prefs.getString(KEY_API_CONFIGS, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<ApiConfig>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit { putString(KEY_API_CONFIGS, json) }
        }

    /** 最大重试次数 */
    var maxRetries: Int
        get() = prefs.getInt(KEY_MAX_RETRIES, 3)
        set(value) = prefs.edit { putInt(KEY_MAX_RETRIES, value) }

    /** 添加 API 配置 */
    fun addApiConfig(config: ApiConfig) {
        apiConfigs = apiConfigs + config
    }

    /** 更新 API 配置 */
    fun updateApiConfig(config: ApiConfig) {
        apiConfigs = apiConfigs.map { if (it.id == config.id) config else it }
    }

    /** 删除 API 配置 */
    fun removeApiConfig(id: String) {
        apiConfigs = apiConfigs.filter { it.id != id }
    }

    /** 获取启用的 API 配置数量 */
    fun getEnabledConfigCount(): Int {
        return apiConfigs.count { it.enabled }
    }

    /** 检查是否有可用的 API 配置 */
    fun hasApiConfigs(): Boolean {
        return apiConfigs.any { it.enabled }
    }

    /** 命令历史列表 */
    var commandHistory: List<String>
        get() {
            val json = prefs.getString(KEY_COMMAND_HISTORY, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
        private set(value) {
            val json = gson.toJson(value)
            prefs.edit { putString(KEY_COMMAND_HISTORY, json) }
        }

    /** 添加命令到历史 */
    fun addCommandToHistory(command: String) {
        if (command.isBlank()) return

        // 移除已存在的相同命令（避免重复）
        val history = commandHistory.toMutableList()
        history.remove(command)

        // 添加到列表开头
        history.add(0, command)

        // 保持列表大小限制
        if (history.size > MAX_HISTORY_SIZE) {
            commandHistory = history.take(MAX_HISTORY_SIZE)
        } else {
            commandHistory = history
        }
    }

    /** 清除命令历史 */
    fun clearCommandHistory() {
        prefs.edit { remove(KEY_COMMAND_HISTORY) }
    }

    /** 日志级别 0=OFF, 1=ERROR, 2=WARNING, 3=INFO, 4=DEBUG */
    var logLevel: Int
        get() = prefs.getInt(KEY_LOG_LEVEL, 3) // 默认 INFO
        set(value) = prefs.edit { putInt(KEY_LOG_LEVEL, value) }

    /** 开发者模式 */
    var devMode: Boolean
        get() = prefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_DEV_MODE, value) }

    /** 对话历史 (JSON 字符串) */
    var chatHistory: String
        get() = prefs.getString(KEY_CHAT_HISTORY, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_CHAT_HISTORY, value) }

    /** 最大步数限制 */
    var maxSteps: Int
        get() = prefs.getInt(KEY_MAX_STEPS, 50)
        set(value) = prefs.edit { putInt(KEY_MAX_STEPS, value) }

    /** 执行模式 (accessibility 或 shizuku) */
    var executionMode: String
        get() = prefs.getString(KEY_EXECUTION_MODE, "accessibility") ?: "accessibility"
        set(value) = prefs.edit { putString(KEY_EXECUTION_MODE, value) }

    /** 自定义任务列表 (JSON 字符串) */
    var customTaskLists: String
        get() = prefs.getString(KEY_CUSTOM_TASK_LISTS, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_CUSTOM_TASK_LISTS, value) }

    /** 视觉策略 (auto/som/grid/none) */
    var visualStrategy: String
        get() = prefs.getString(KEY_VISUAL_STRATEGY, "auto") ?: "auto"
        set(value) = prefs.edit { putString(KEY_VISUAL_STRATEGY, value) }

    /** 清除所有配置 */
    fun clear() {
        prefs.edit { clear() }
    }
}
