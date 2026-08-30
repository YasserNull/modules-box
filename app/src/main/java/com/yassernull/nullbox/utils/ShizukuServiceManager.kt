package com.yassernull.nullbox.utils

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.yassernull.nullbox.ipc.IShizukuShellService
import com.yassernull.nullbox.ipc.ShizukuShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuServiceManager {
    private val shellMutex = Mutex()
    private val binderListenerAdded = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile private var shellService: IShizukuShellService? = null
    @Volatile private var shellArgs: Shizuku.UserServiceArgs? = null
    @Volatile private var shellConnection: ServiceConnection? = null

    suspend fun prewarm(context: Context) {
        if (!Shizuku.pingBinder()) {
            if (binderListenerAdded.compareAndSet(false, true)) {
                Shizuku.addBinderReceivedListener {
                    GlobalScope.launch(Dispatchers.IO) {
                        runCatching { ensureShellService(context) }
                    }
                }
            }
            return
        }
        withContext(Dispatchers.IO) {
            runCatching { ensureShellService(context) }
        }
    }

    suspend fun ensureShellService(context: Context): IShizukuShellService {
        return shellMutex.withLock {
            shellService?.let { return it }
            if (!Shizuku.pingBinder()) {
                android.util.Log.e("ShizukuServiceManager", "Shell binder not available")
                throw RemoteException("Shizuku binder not available")
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("ShizukuServiceManager", "Shell permission not granted")
                throw RemoteException("Shizuku permission not granted")
            }
            val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuShellService::class.java))
                .daemon(false)
                .processNameSuffix("shizuku-shell")
                .tag("shell")
                .version(1)
            shellArgs = args
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val api = IShizukuShellService.Stub.asInterface(service)
                    shellService = api
                    try {
                        service?.linkToDeath({
                            shellService = null
                        }, 0)
                    } catch (_: Throwable) {
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    shellService = null
                }
            }
            shellConnection = connection
            Shizuku.bindUserService(args, connection)
            var waitCount = 0
            while (shellService == null && waitCount < 200) {
                kotlinx.coroutines.delay(50)
                waitCount++
            }
            if (shellService == null) {
                android.util.Log.e("ShizukuServiceManager", "Shell service connect timeout")
            }
            return shellService
                ?: throw RemoteException("Shizuku shell service not connected")
        }
    }
}
