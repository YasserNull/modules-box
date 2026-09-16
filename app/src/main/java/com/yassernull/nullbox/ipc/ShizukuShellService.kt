package com.yassernull.nullbox.ipc

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.PtyProcess
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuShellService : IShizukuShellService.Stub() {
    private val activePids = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var appPid = 0
    private val watchdogStarted = AtomicBoolean(false)

    override fun setAppPid(pid: Int) {
        appPid = pid
        ensureWatchdog()
    }

    override fun startShell(
        shellPath: String,
        cwd: String,
        args: Array<String>,
        env: Array<String>,
        rows: Int,
        cols: Int,
        cellWidth: Int,
        cellHeight: Int,
        callback: IShellExitCallback?
    ): PtyInfo {
        val pid = IntArray(1)
        val fd = PtyProcess.createSubprocess(
            shellPath,
            cwd,
            args,
            env,
            pid,
            rows,
            cols,
            cellWidth,
            cellHeight
        )
        val pfd = ParcelFileDescriptor.fromFd(fd)
        val shellPid = pid[0]
        activePids.add(shellPid)
        Thread({
            val exitCode = PtyProcess.waitFor(shellPid)
            activePids.remove(shellPid)
            try {
                callback?.onShellExit(shellPid, exitCode)
            } catch (_: RemoteException) {
            }
        }, "ShizukuShellWaiter[pid=$shellPid]").start()
        return PtyInfo(pfd, shellPid)
    }

    override fun stopShell(pid: Int) {
        activePids.remove(pid)
        // The child is a session leader (setsid in createSubprocess), so killing its
        // process group also kills init-host/proot/the guest below it.
        runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
        runCatching { Os.kill(pid, OsConstants.SIGKILL) }
    }

    override fun shutdown() {
        // Called by the app when no shizuku session remains. Kill any leftover
        // shells and this process so no uid-2000 process survives the app.
        activePids.forEach { killTree(it) }
        runCatching { Os.kill(Os.getpid(), OsConstants.SIGKILL) }
    }

    private fun ensureWatchdog() {
        if (!watchdogStarted.compareAndSet(false, true)) return
        Thread({
            while (true) {
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                }
                if (appPid > 0 && !isAppAlive(appPid)) {
                    // The app process died (force-stop). Kill every shell and this process.
                    activePids.forEach { killTree(it) }
                    runCatching { Os.kill(Os.getpid(), OsConstants.SIGKILL) }
                    break
                }
            }
        }, "ShizukuShellWatchdog").start()
    }

    private fun isAppAlive(pid: Int): Boolean {
        val cmdline = runCatching { File("/proc/$pid/cmdline").readText() }.getOrNull()
        return cmdline?.startsWith(APP_PACKAGE) == true
    }

    private fun killTree(pid: Int) {
        runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
        runCatching { Os.kill(pid, OsConstants.SIGKILL) }
    }

    private companion object {
        const val APP_PACKAGE = "com.yassernull.nullbox"
    }
}
