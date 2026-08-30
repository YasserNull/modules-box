package com.yassernull.nullbox.ipc

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.PtyProcess

class ShizukuShellService : IShizukuShellService.Stub() {
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
        Thread({
            val exitCode = PtyProcess.waitFor(shellPid)
            try {
                callback?.onShellExit(shellPid, exitCode)
            } catch (_: RemoteException) {
            }
        }, "ShizukuShellWaiter[pid=$shellPid]").start()
        return PtyInfo(pfd, shellPid)
    }

    override fun stopShell(pid: Int) {
        runCatching { Os.kill(pid, OsConstants.SIGKILL) }
    }
}
