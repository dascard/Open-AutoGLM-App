package com.autoglm.app

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/** Shizuku UserService 接口定义 手写接口以避免 AIDL 编译问题 */
interface IUserService : IInterface {

    /** 执行 shell 命令并返回结果 */
    fun executeCommand(command: String): String?

    /** 销毁服务 */
    fun destroy()

    companion object {
        const val DESCRIPTOR = "com.autoglm.app.IUserService"

        // Transaction codes
        const val TRANSACTION_executeCommand = IBinder.FIRST_CALL_TRANSACTION + 0
        const val TRANSACTION_destroy = 16777114
    }

    /** Stub 抽象类 - UserService 实现类需要继承此类 */
    abstract class Stub : Binder(), IUserService {

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val descriptor = DESCRIPTOR
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(descriptor)
                    return true
                }
                TRANSACTION_executeCommand -> {
                    data.enforceInterface(descriptor)
                    val command = data.readString() ?: ""
                    val result = executeCommand(command)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                TRANSACTION_destroy -> {
                    data.enforceInterface(descriptor)
                    destroy()
                    reply?.writeNoException()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        companion object {
            fun asInterface(obj: IBinder?): IUserService? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is IUserService) {
                    return iin
                }
                return Proxy(obj)
            }
        }
    }

    /** Proxy 类 - 用于远程调用 */
    private class Proxy(private val remote: IBinder) : IUserService {

        override fun asBinder(): IBinder = remote

        override fun executeCommand(command: String): String? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(command)
                remote.transact(TRANSACTION_executeCommand, data, reply, 0)
                reply.readException()
                reply.readString()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun destroy() {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_destroy, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }
}
