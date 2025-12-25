package com.autoglm.app.ui

import android.webkit.JavascriptInterface

interface WebAppListener {
    fun onStartTask(task: String)
    fun onStartTaskWithMode(task: String, mode: String)
    fun onStopTask()
    fun onTogglePause()
    fun isTaskPaused(): Boolean
    fun checkOverlayPermission(): Boolean
    fun onRequestOverlayPermission()
    fun onOpenAccessibilitySettings()
    fun onGetApiConfigs(): String
    fun onSaveApiConfig(configJson: String)
    fun onDeleteApiConfig(id: String)
    fun onGetCommandHistory(): String
    fun onClearCommandHistory()

    // Shizuku 相关
    fun onCheckShizukuAvailable(): Boolean
    fun onCheckShizukuPermission(): Boolean
    fun onRequestShizukuPermission()
    fun onBindShizukuService()
    fun onExecuteAdbCommand(command: String): String
    fun onGetShizukuStatus(): String

    // 设置相关
    fun onGetLogLevel(): Int
    fun onSetLogLevel(level: Int)
    fun onGetDevMode(): Boolean
    fun onSetDevMode(enabled: Boolean)
    fun onGetChatHistory(): String
    fun onSaveChatHistory(json: String)

    // 最大步数设置
    fun onGetMaxSteps(): Int
    fun onSetMaxSteps(steps: Int)

    // SoM 预览
    fun onGetSomPreview(): String

    // 执行模式
    fun onGetExecutionMode(): String
    fun onSetExecutionMode(mode: String)

    // 文件日志
    fun onGetFileLogContent(): String
}

class WebAppInterface(private val listener: WebAppListener) {

    @JavascriptInterface
    fun startTask(task: String) {
        listener.onStartTask(task)
    }

    @JavascriptInterface
    fun startTaskWithMode(task: String, mode: String) {
        listener.onStartTaskWithMode(task, mode)
    }

    @JavascriptInterface
    fun stopTask() {
        listener.onStopTask()
    }

    @JavascriptInterface
    fun togglePause() {
        listener.onTogglePause()
    }

    @JavascriptInterface
    fun isPaused(): Boolean {
        return listener.isTaskPaused()
    }

    @JavascriptInterface
    fun checkOverlayPermission(): Boolean {
        return listener.checkOverlayPermission()
    }

    @JavascriptInterface
    fun requestOverlayPermission() {
        listener.onRequestOverlayPermission()
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        listener.onOpenAccessibilitySettings()
    }

    @JavascriptInterface
    fun getApiConfigs(): String {
        return listener.onGetApiConfigs()
    }

    @JavascriptInterface
    fun saveApiConfig(configJson: String) {
        listener.onSaveApiConfig(configJson)
    }

    @JavascriptInterface
    fun deleteApiConfig(id: String) {
        listener.onDeleteApiConfig(id)
    }

    @JavascriptInterface
    fun getCommandHistory(): String {
        return listener.onGetCommandHistory()
    }

    @JavascriptInterface
    fun clearCommandHistory() {
        listener.onClearCommandHistory()
    }

    // ===== Shizuku 相关接口 =====

    @JavascriptInterface
    fun checkShizukuAvailable(): Boolean {
        return listener.onCheckShizukuAvailable()
    }

    @JavascriptInterface
    fun checkShizukuPermission(): Boolean {
        return listener.onCheckShizukuPermission()
    }

    @JavascriptInterface
    fun requestShizukuPermission() {
        listener.onRequestShizukuPermission()
    }

    @JavascriptInterface
    fun bindShizukuService() {
        listener.onBindShizukuService()
    }

    @JavascriptInterface
    fun executeAdbCommand(command: String): String {
        return listener.onExecuteAdbCommand(command)
    }

    @JavascriptInterface
    fun getShizukuStatus(): String {
        return listener.onGetShizukuStatus()
    }

    // ===== 设置相关接口 =====

    @JavascriptInterface
    fun getLogLevel(): Int {
        return listener.onGetLogLevel()
    }

    @JavascriptInterface
    fun setLogLevel(level: Int) {
        listener.onSetLogLevel(level)
    }

    @JavascriptInterface
    fun getDevMode(): Boolean {
        return listener.onGetDevMode()
    }

    @JavascriptInterface
    fun setDevMode(enabled: Boolean) {
        listener.onSetDevMode(enabled)
    }

    @JavascriptInterface
    fun getChatHistory(): String {
        return listener.onGetChatHistory()
    }

    @JavascriptInterface
    fun saveChatHistory(json: String) {
        listener.onSaveChatHistory(json)
    }

    @JavascriptInterface
    fun getMaxSteps(): Int {
        return listener.onGetMaxSteps()
    }

    @JavascriptInterface
    fun setMaxSteps(steps: Int) {
        listener.onSetMaxSteps(steps)
    }

    @JavascriptInterface
    fun getSomPreview(): String {
        return listener.onGetSomPreview()
    }

    @JavascriptInterface
    fun getExecutionMode(): String {
        return listener.onGetExecutionMode()
    }

    @JavascriptInterface
    fun setExecutionMode(mode: String) {
        listener.onSetExecutionMode(mode)
    }

    @JavascriptInterface
    fun getFileLogContent(): String {
        return listener.onGetFileLogContent()
    }
}
