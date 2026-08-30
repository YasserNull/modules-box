package com.yassernull.nullbox.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yassernull.nullbox.utils.PrivilegedAccessManager
import com.yassernull.nullbox.utils.startShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class RishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.yassernull.nullbox.RISH") return
        val cmdPath = intent.getStringExtra("cmd_path") ?: return
        val outPath = intent.getStringExtra("out") ?: return
        val errPath = intent.getStringExtra("err") ?: return
        val codePath = intent.getStringExtra("code") ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cmd = File(cmdPath).takeIf { it.exists() }?.readText() ?: run {
                    File(errPath).writeText("rish: missing command file\n")
                    File(codePath).writeText("2")
                    return@launch
                }
                if (!PrivilegedAccessManager.hasShizukuPermission()) {
                    File(errPath).writeText("Shizuku permission not granted\n")
                    File(codePath).writeText("126")
                    return@launch
                }

                val outFile = File(outPath)
                val errFile = File(errPath)
                outFile.parentFile?.mkdirs()

                val safeOut = outFile.absolutePath.replace("'", "'\\''")
                val safeErr = errFile.absolutePath.replace("'", "'\\''")
                val safeCode = File(codePath).absolutePath.replace("'", "'\\''")

                val script = "($cmd) 1>'$safeOut' 2>'$safeErr'; echo $? >'$safeCode'"
                startShizukuShell(
                    context,
                    "/system/bin/sh",
                    "/sdcard",
                    arrayOf("/system/bin/sh", "-c", script),
                    emptyArray(),
                    24,
                    80,
                    0,
                    0
                ) { _ -> }
            } catch (t: Throwable) {
                try {
                    File(errPath).writeText("rish error: ${t.message}\n")
                    File(codePath).writeText("125")
                } catch (_: Throwable) {
                }
                Log.e("RishReceiver", "rish failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
