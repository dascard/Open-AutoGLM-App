package com.autoglm.app.shizuku

import android.util.Log
import com.autoglm.app.IUserService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/** Shizuku UserService 实现 此服务运行在 Shizuku 进程中，具有 ADB 或 ROOT 权限 */
class ShizukuUserService : IUserService.Stub() {

    companion object {
        private const val TAG = "ShizukuUserService"
    }

    init {
        Log.i(TAG, "ShizukuUserService created")
    }

    /**
     * 执行 shell 命令
     * @param command 要执行的命令
     * @return 命令输出（stdout + stderr）
     */
    override fun executeCommand(command: String): String? {
        Log.d(TAG, "Executing command: $command")

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()

            // 读取标准输出
            var line: String?
            while (stdout.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            // 读取错误输出
            while (stderr.readLine().also { line = it } != null) {
                output.appendLine("[stderr] $line")
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                output.appendLine("[exit code: $exitCode]")
            }

            stdout.close()
            stderr.close()

            val result = output.toString().trimEnd()
            Log.d(
                    TAG,
                    "Command output: ${result.take(200)}${if (result.length > 200) "..." else ""}"
            )
            result
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            "Error: ${e.message}"
        }
    }

    /** 销毁服务 由 Shizuku 调用以清理资源 */
    override fun destroy() {
        Log.i(TAG, "ShizukuUserService destroying...")
        exitProcess(0)
    }
}
