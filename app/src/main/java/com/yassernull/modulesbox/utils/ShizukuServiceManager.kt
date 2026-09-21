package com.yassernull.modulesbox.utils

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import com.yassernull.modulesbox.ipc.IShizukuShellService
import com.yassernull.modulesbox.ipc.ShizukuShellService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

object ShizukuServiceManager {
    private const val TAG = "ShizukuServiceManager"

    private val shellMutex = Mutex()
    private val initDone = java.util.concurrent.atomic.AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null
    @Volatile private var shellService: IShizukuShellService? = null
    @Volatile private var binding: CompletableDeferred<IShizukuShellService>? = null
    @Volatile private var shellArgs: Shizuku.UserServiceArgs? = null
    @Volatile private var shellConnection: ServiceConnection? = null

    // Sticky: fires immediately when the binder is already alive, and again every
    // time it returns — so the shell service is (re-)warmed proactively instead of
    // on first user tap.
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received, warming shell service")
        warmAsync()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died, dropping cached shell service")
        binding?.let { deferred ->
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(RemoteException("Shizuku binder died"))
            }
        }
        binding = null
        shellService = null
    }

    /** Registers binder listeners and eagerly warms the shell service. Call once. */
    fun init(context: Context) {
        if (!initDone.compareAndSet(false, true)) return
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        warmAsync()
    }

    private fun warmAsync() {
        val ctx = appContext ?: return
        if (shellService != null) return
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
        serviceScope.launch {
            runCatching { ensureShellService(ctx) }
        }
    }

    suspend fun prewarm(context: Context) {
        init(context)
        withContext(Dispatchers.IO) {
            runCatching { ensureShellService(context.applicationContext) }
        }
    }

    suspend fun ensureShellService(context: Context): IShizukuShellService {
        shellService?.let { return it }
        // Single in-flight bind shared by concurrent callers; wakes instantly on
        // onServiceConnected instead of polling.
        val deferred = shellMutex.withLock {
            shellService?.let { return it }
            binding?.let { return@withLock it }
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Shell binder not available")
                throw RemoteException("Shizuku binder not available")
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Shell permission not granted")
                throw RemoteException("Shizuku permission not granted")
            }
            val fresh = CompletableDeferred<IShizukuShellService>()
            binding = fresh
            doBind(context.applicationContext, fresh)
            fresh
        }
        return withTimeoutOrNull(15_000) { deferred.await() }
            ?: throw RemoteException("Shizuku shell service connect timeout")
    }

    private fun doBind(context: Context, fresh: CompletableDeferred<IShizukuShellService>) {
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuShellService::class.java))
            .daemon(false)
            .processNameSuffix("shizuku-shell")
            .tag("shell")
            .version(1)
        shellArgs = args
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val api = IShizukuShellService.Stub.asInterface(service)
                    shellService = api
                    // Let the shell service watch the app process so it can kill all
                    // shells when the app is force-stopped.
                    runCatching { api.setAppPid(Process.myPid()) }
                    try {
                        service?.linkToDeath({
                            shellService = null
                        }, 0)
                    } catch (_: Throwable) {
                    }
                    if (!fresh.isCompleted) fresh.complete(api)
                } catch (e: Throwable) {
                    if (!fresh.isCompleted) fresh.completeExceptionally(e)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shellService = null
            }
        }
        shellConnection = connection
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Throwable) {
            if (!fresh.isCompleted) fresh.completeExceptionally(e)
        }
    }

    fun unbindShellService() {
        // Tell the service to kill its own process; the binding then drops
        // automatically with the process death.
        shellService?.let { api ->
            runCatching { api.shutdown() }
        }
        shellArgs = null
        shellConnection = null
        shellService = null
        binding = null
    }
}
