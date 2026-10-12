package com.yassernull.modulesbox.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.repository.ModuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Resolves an installed module and makes sure its server is running, for launches that
 * come from outside the main screen (home-screen widget).
 */
object ModuleLauncher {
    private const val TAG = "ModuleLauncher"

    /**
     * Upper bound for a cold start: how long the freshly started server may take to accept
     * connections. The wait ends as soon as the port opens, so this only caps a failed
     * start; a healthy proot + php/node boot finishes well before it.
     */
    private const val PORT_READY_TIMEOUT_MS = 15_000L
    private const val PORT_WAIT_INTERVAL_MS = 50L

    suspend fun findModule(context: Context, moduleId: String): Module? {
        val module = ModuleRepository(context).getModules().find { it.id == moduleId }
        if (module == null) Log.w(TAG, "findModule: $moduleId is not installed")
        return module
    }

    /**
     * Returns the module's public URL, starting its server first when needed, or null when
     * the start failed.
     *
     * A cold start only polls until the port accepts connections. The fixed extra 300 ms
     * that used to follow the poll was removed: an open port already means the server is
     * accepting requests, so the wait only added latency on every widget tap.
     */
    suspend fun ensureRunning(context: Context, module: Module): String? {
        val startedAt = SystemClock.elapsedRealtime()
        RuntimeTmp.prepare(context)

        var port = ModuleInstaller.getModulePort(module.id)
        Log.i(TAG, "ensureRunning: ${module.id} cachedPort=$port")

        if (port == null) {
            port = ModuleInstaller.startModuleServer(context, module) ?: run {
                Log.e(TAG, "ensureRunning: failed to start ${module.id}")
                return null
            }
            Log.i(
                TAG,
                "ensureRunning: ${module.id} started on $port after ${SystemClock.elapsedRealtime() - startedAt}ms, waiting for it to accept connections"
            )

            // A cold `proot + php/node` boot takes a couple of seconds. Polling until the
            // port actually accepts (instead of a fixed number of tries) is what keeps the
            // WebView from being handed a URL the server cannot serve yet — that gap used
            // to be the extra second users saw after the loader.
            //
            // The port probe must run off the main thread: ensureRunning is a LaunchedEffect
            // job, and the plain-Socket check in isPortOpen throws NetworkOnMainThreadException
            // on the main thread. That exception was swallowed by isPortOpen's catch, so it
            // silently reported "closed" forever and the loader waited for the full timeout.
            val portReady = withContext(Dispatchers.IO) {
                val deadline = SystemClock.elapsedRealtime() + PORT_READY_TIMEOUT_MS
                var open = ModuleInstaller.isPortOpen(port)
                while (!open && SystemClock.elapsedRealtime() < deadline) {
                    delay(PORT_WAIT_INTERVAL_MS)
                    open = ModuleInstaller.isPortOpen(port)
                }
                open
            }

            if (portReady) {
                Log.i(
                    TAG,
                    "ensureRunning: ${module.id} port $port accepting connections after ${SystemClock.elapsedRealtime() - startedAt}ms"
                )
            } else {
                Log.w(
                    TAG,
                    "ensureRunning: ${module.id} port $port still closed after ${SystemClock.elapsedRealtime() - startedAt}ms, opening anyway"
                )
            }
        }

        val page = module.html?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "/"
        val url = "http://localhost:$port$page"
        Log.i(TAG, "ensureRunning: ${module.id} -> $url (total ${SystemClock.elapsedRealtime() - startedAt}ms)")
        return url
    }
}
