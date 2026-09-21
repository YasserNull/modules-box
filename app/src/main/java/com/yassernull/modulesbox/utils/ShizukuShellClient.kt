package com.yassernull.modulesbox.utils

import android.content.Context
import com.yassernull.modulesbox.ipc.IShizukuShellService
import com.yassernull.modulesbox.ipc.IShellExitCallback
import com.yassernull.modulesbox.ipc.PtyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShizukuShellHandle(
    val service: IShizukuShellService,
    val pid: Int
) {
    fun stop() {
        runCatching { service.stopShell(pid) }
    }
}

suspend fun startShizukuShell(
    context: Context,
    shellPath: String,
    cwd: String,
    args: Array<String>,
    env: Array<String>,
    rows: Int,
    cols: Int,
    cellWidth: Int,
    cellHeight: Int,
    onExit: (Int) -> Unit
): Pair<PtyInfo, ShizukuShellHandle> {
    return withContext(Dispatchers.IO) {
        val api = try {
            ShizukuServiceManager.ensureShellService(context)
        } catch (e: android.os.RemoteException) {
            ShizukuServiceManager.ensureShellService(context)
        }
        val info = api.startShell(
            shellPath,
            cwd,
            args,
            env,
            rows,
            cols,
            cellWidth,
            cellHeight,
            object : IShellExitCallback.Stub() {
                override fun onShellExit(pid: Int, exitCode: Int) {
                    onExit(exitCode)
                }
            }
        )
        info to ShizukuShellHandle(api, info.pid)
    }
}
