package com.autoglm.app.ui

import android.webkit.JavascriptInterface

interface WebAppListener {
    fun onStartTask(task: String)
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
}

class WebAppInterface(private val listener: WebAppListener) {
    
    @JavascriptInterface
    fun startTask(task: String) {
        listener.onStartTask(task)
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
}
