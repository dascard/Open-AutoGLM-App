package com.autoglm.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.autoglm.app.IUserService
import com.autoglm.app.util.FileLogger
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

/** Shizuku 辅助类 负责管理 Shizuku 状态、权限和 UserService */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"

    // 应用配置常量（避免依赖 BuildConfig 生成类）
    private const val APPLICATION_ID = "com.autoglm.app"
    private const val VERSION_CODE = 1
    private const val IS_DEBUG = false

    @Volatile private var userService: IUserService? = null

    @Volatile private var isBound = false

    // 权限请求回调
    private var permissionCallback: ((Boolean) -> Unit)? = null

    // Binder 状态回调
    private var binderStateCallback: ((Boolean) -> Unit)? = null

    // 服务绑定回调
    private var serviceBindingCallback: ((Boolean) -> Unit)? = null

    // UserService 连接
    private val userServiceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    FileLogger.i(TAG, "UserService connected: $name")
                    if (service != null && service.pingBinder()) {
                        userService = IUserService.Stub.asInterface(service)
                        isBound = true
                        FileLogger.i(TAG, "UserService bound successfully")
                        serviceBindingCallback?.invoke(true)
                    } else {
                        FileLogger.e(
                                TAG,
                                "UserService binding failed - service is null or binder dead"
                        )
                        serviceBindingCallback?.invoke(false)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    FileLogger.w(TAG, "UserService disconnected: $name")
                    userService = null
                    isBound = false
                    serviceBindingCallback?.invoke(false)
                }
            }

    // Binder 接收监听器
    private val binderReceivedListener =
            Shizuku.OnBinderReceivedListener {
                FileLogger.i(TAG, "Shizuku binder received")
                binderStateCallback?.invoke(true)
            }

    // Binder 断开监听器
    private val binderDeadListener =
            Shizuku.OnBinderDeadListener {
                FileLogger.w(TAG, "Shizuku binder dead")
                userService = null
                isBound = false
                binderStateCallback?.invoke(false)
            }

    // 权限结果监听器
    private val permissionResultListener =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                FileLogger.i(
                        TAG,
                        "Permission result: requestCode=$requestCode, grantResult=$grantResult"
                )
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                permissionCallback?.invoke(granted)
            }

    /** 初始化 Shizuku 监听器 */
    fun init(
            onBinderStateChanged: ((Boolean) -> Unit)? = null,
            onPermissionResult: ((Boolean) -> Unit)? = null,
            onServiceBinding: ((Boolean) -> Unit)? = null
    ) {
        binderStateCallback = onBinderStateChanged
        permissionCallback = onPermissionResult
        serviceBindingCallback = onServiceBinding

        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        FileLogger.i(TAG, "Shizuku listeners initialized")
    }

    /** 清理 Shizuku 监听器 */
    fun cleanup() {
        unbindUserService()

        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)

        binderStateCallback = null
        permissionCallback = null

        FileLogger.i(TAG, "Shizuku listeners cleaned up")
    }

    /** 检查 Shizuku 是否可用（已安装且 Binder 存活） */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error checking Shizuku availability", e)
            false
        }
    }

    /** 检查是否有 Shizuku 权限 */
    fun hasPermission(): Boolean {
        return try {
            if (!isAvailable()) {
                false
            } else if (Shizuku.isPreV11()) {
                // Pre-v11 不支持
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error checking permission", e)
            false
        }
    }

    /** 请求 Shizuku 权限 */
    fun requestPermission(requestCode: Int = 1) {
        try {
            if (!isAvailable()) {
                FileLogger.w(TAG, "Shizuku not available, cannot request permission")
                permissionCallback?.invoke(false)
                return
            }

            if (Shizuku.isPreV11()) {
                FileLogger.w(TAG, "Shizuku pre-v11 is not supported")
                permissionCallback?.invoke(false)
                return
            }

            if (hasPermission()) {
                FileLogger.i(TAG, "Already has permission")
                permissionCallback?.invoke(true)
                return
            }

            if (Shizuku.shouldShowRequestPermissionRationale()) {
                FileLogger.w(TAG, "User denied permission and chose 'don't ask again'")
                permissionCallback?.invoke(false)
                return
            }

            FileLogger.i(TAG, "Requesting Shizuku permission...")
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error requesting permission", e)
            permissionCallback?.invoke(false)
        }
    }

    /** 绑定 UserService */
    fun bindUserService() {
        if (!hasPermission()) {
            FileLogger.w(TAG, "No permission, cannot bind UserService")
            return
        }

        if (isBound && userService != null) {
            FileLogger.i(TAG, "UserService already bound")
            return
        }

        try {
            val isBinderAlive = Shizuku.pingBinder()
            FileLogger.i(
                    TAG,
                    "Pre-bind check: pingBinder=$isBinderAlive, version=${Shizuku.getVersion()}"
            )

            val userServiceArgs =
                    UserServiceArgs(
                                    ComponentName(
                                            APPLICATION_ID,
                                            ShizukuUserService::class.java.name
                                    )
                            )
                            .daemon(false)
                            .processNameSuffix("user_service")
                            .debuggable(IS_DEBUG)
                            .version(VERSION_CODE)

            // Setup timeout handler
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!isBound) {
                    FileLogger.e(TAG, "Binding UserService timed out after 5000ms")
                    serviceBindingCallback?.invoke(false)
                }
            }
            handler.postDelayed(timeoutRunnable, 5000)

            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            FileLogger.i(TAG, "Binding UserService requested...")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error binding UserService", e)
            serviceBindingCallback?.invoke(false)
        }
    }

    /** 解绑 UserService */
    fun unbindUserService() {
        if (!isBound) {
            return
        }

        try {
            Shizuku.unbindUserService(
                    UserServiceArgs(
                            ComponentName(APPLICATION_ID, ShizukuUserService::class.java.name)
                    ),
                    userServiceConnection,
                    true
            )
            userService = null
            isBound = false
            FileLogger.i(TAG, "UserService unbound")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error unbinding UserService", e)
        }
    }

    /**
     * 执行 ADB 命令
     * @param command 要执行的命令
     * @return 命令输出结果，失败返回 null
     */
    fun executeCommand(command: String): String? {
        val service = userService
        if (service == null) {
            FileLogger.w(TAG, "UserService not available")
            return null
        }

        return try {
            FileLogger.d(TAG, "Executing command: $command")
            val result = service.executeCommand(command)
            FileLogger.d(TAG, "Command result: ${result?.take(200)}...")
            result
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error executing command", e)
            "Error: ${e.message}"
        }
    }

    /** 获取 Shizuku 权限级别（ADB=2000, ROOT=0） */
    fun getUid(): Int {
        return try {
            if (isAvailable()) {
                Shizuku.getUid()
            } else {
                -1
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Error getting UID", e)
            -1
        }
    }

    /** 是否是 ROOT 权限 */
    fun isRoot(): Boolean = getUid() == 0

    /** 是否是 ADB 权限 */
    fun isAdb(): Boolean = getUid() == 2000

    /** UserService 是否已绑定 */
    fun isServiceBound(): Boolean = isBound && userService != null
}
