package com.autoglm.app.core

/**
 * 动作密封类 - 表示 AI 返回的各种操作指令
 */
sealed class Action {
    /**
     * 点击操作
     */
    data class Tap(val x: Int, val y: Int) : Action() {
        override fun toString() = "点击 ($x, $y)"
    }
    
    /**
     * 滑动操作
     */
    data class Swipe(
        val x1: Int, 
        val y1: Int, 
        val x2: Int, 
        val y2: Int, 
        val duration: Int = 300
    ) : Action() {
        override fun toString() = "滑动 ($x1,$y1) → ($x2,$y2)"
    }
    
    /**
     * 输入文字操作
     */
    data class Input(val text: String) : Action() {
        override fun toString() = "输入: $text"
    }
    
    /**
     * 任务完成
     */
    data class Done(val message: String = "任务完成") : Action() {
        override fun toString() = "任务完成: $message"
    }
    
    /**
     * 等待操作
     */
    data class Wait(val milliseconds: Long) : Action() {
        override fun toString() = "等待 ${milliseconds}ms"
    }
    
    /**
     * 返回操作
     */
    object Back : Action() {
        override fun toString() = "返回"
    }
    
    /**
     * 回到主屏幕
     */
    object Home : Action() {
        override fun toString() = "回到主屏幕"
    }
    
    /**
     * 请求用户介入
     * 用于敏感操作(支付/删除/确认)或 AI 无法继续时
     */
    data class AskUser(
        val reason: String,
        val suggestion: String = ""
    ) : Action() {
        override fun toString() = "请求用户介入: $reason"
    }
    
    /**
     * 启动应用操作
     * 直接通过系统启动应用，不需要模拟点击图标
     */
    data class Launch(val appName: String) : Action() {
        override fun toString() = "启动应用: $appName"
    }
    
    /**
     * 确认/回车操作
     * 模拟按下输入法的确认键或回车键
     */
    object Enter : Action() {
        override fun toString() = "确认/回车"
    }
    
    /**
     * 长按操作
     */
    data class LongPress(val x: Int, val y: Int, val duration: Int = 1000) : Action() {
        override fun toString() = "长按 ($x, $y)"
    }
}

/**
 * AI 响应数据类
 * 支持多个连续操作和悬浮窗状态消息
 */
data class AIResponse(
    val action: Action,                    // 主要操作（兼容单操作）
    val actions: List<Action> = listOf(),  // 多操作列表
    val status: String = "",               // 悬浮窗显示的状态消息
    val thinking: String? = null,          // AI 思考过程
    val rawResponse: String? = null,       // AI 原始响应（调试用）
    val confidence: Float = 1.0f
) {
    // 获取所有要执行的操作
    fun getAllActions(): List<Action> {
        return if (actions.isNotEmpty()) actions else listOf(action)
    }
}

/**
 * 任务执行结果
 */
sealed class TaskResult {
    data class Success(val message: String) : TaskResult()
    data class Failed(val error: String) : TaskResult()
    object Cancelled : TaskResult()
}
