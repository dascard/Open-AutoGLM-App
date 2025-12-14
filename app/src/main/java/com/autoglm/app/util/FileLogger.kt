package com.autoglm.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志管理器
 * 将日志保存到本地文件，方便调试
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_DIR = "autoglm_logs"
    private const val MAX_LOG_FILES = 10  // 最多保留10个日志文件
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024  // 5MB
    
    private var logFile: File? = null
    private var printWriter: PrintWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    /**
     * 初始化日志系统
     */
    fun init(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            // 清理旧日志
            cleanOldLogs(logDir)
            
            // 创建新日志文件
            val fileName = "log_${fileDateFormat.format(Date())}.txt"
            logFile = File(logDir, fileName)
            printWriter = PrintWriter(FileWriter(logFile, true), true)
            
            i(TAG, "日志系统初始化完成: ${logFile?.absolutePath}")
            i(TAG, "设备信息: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            i(TAG, "Android 版本: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            Log.e(TAG, "初始化日志系统失败", e)
        }
    }
    
    private fun cleanOldLogs(logDir: File) {
        try {
            val logFiles = logDir.listFiles { file -> file.name.startsWith("log_") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            
            if (logFiles.size > MAX_LOG_FILES) {
                logFiles.drop(MAX_LOG_FILES).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧日志失败", e)
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    /**
     * Info 日志
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("I", tag, message)
    }
    
    /**
     * Debug 日志
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("D", tag, message)
    }
    
    /**
     * Warning 日志
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("W", tag, message)
    }
    
    /**
     * Error 日志
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeToFile("E", tag, message)
        throwable?.let {
            writeToFile("E", tag, "Exception: ${it.javaClass.simpleName}: ${it.message}")
            it.stackTrace.take(10).forEach { element ->
                writeToFile("E", tag, "    at $element")
            }
        }
    }
    
    /**
     * 记录 API 请求
     */
    fun logApiRequest(tag: String, provider: String, model: String, endpoint: String) {
        val message = """
            |=== API 请求 ===
            |提供商: $provider
            |模型: $model
            |端点: $endpoint
        """.trimMargin()
        i(tag, message)
    }
    
    /**
     * 记录 API 响应
     */
    fun logApiResponse(tag: String, statusCode: Int, responseBody: String) {
        val truncated = if (responseBody.length > 2000) {
            responseBody.take(2000) + "... (截断，共 ${responseBody.length} 字符)"
        } else {
            responseBody
        }
        val message = """
            |=== API 响应 ===
            |状态码: $statusCode
            |响应体:
            |$truncated
        """.trimMargin()
        i(tag, message)
    }
    
    /**
     * 记录 AI 解析结果
     */
    fun logAiAction(tag: String, originalContent: String, extractedJson: String, action: String) {
        val message = """
            |=== AI 动作解析 ===
            |原始内容: $originalContent
            |提取的 JSON: $extractedJson
            |解析的动作: $action
        """.trimMargin()
        i(tag, message)
    }
    
    private fun writeToFile(level: String, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp [$level/$tag] $message"
            printWriter?.println(logLine)
            
            // 检查文件大小，超过限制则轮转
            logFile?.let { file ->
                if (file.length() > MAX_FILE_SIZE) {
                    rotateLogFile()
                }
            }
        } catch (e: Exception) {
            // 忽略写入错误
        }
    }
    
    private fun rotateLogFile() {
        try {
            printWriter?.close()
            logFile?.let { oldFile ->
                val newName = oldFile.name.replace(".txt", "_old.txt")
                oldFile.renameTo(File(oldFile.parent, newName))
            }
            logFile?.let { file ->
                printWriter = PrintWriter(FileWriter(file, false), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮转日志文件失败", e)
        }
    }
    
    /**
     * 关闭日志系统
     */
    fun close() {
        try {
            printWriter?.close()
        } catch (e: Exception) {
            // 忽略
        }
    }
}
