package com.yassernull.modulesbox.ipc

import android.content.Context
import android.util.Log
import com.yassernull.modulesbox.utils.PrivilegedAccessManager
import com.yassernull.modulesbox.utils.startShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object RishDaemon {
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val baseDir = File(context.filesDir.parentFile, "tmp")
        if (!baseDir.exists()) baseDir.mkdirs()
        File(baseDir, "rish.ready").writeText("ready")

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val reqs = baseDir.listFiles { file -> file.name.endsWith(".req") } ?: emptyArray()
                    for (req in reqs) {
                        runCatching { File(baseDir, "rish.log").appendText("got ${req.name}\n") }
                        handleReq(context, req)
                    }
                } catch (t: Throwable) {
                    Log.e("RishDaemon", "loop error", t)
                }
                delay(200)
            }
        }
    }

    private suspend fun handleReq(context: Context, req: File) {
        val lines = runCatching { req.readLines() }.getOrNull()
        if (lines == null || lines.size < 4) {
            req.delete()
            return
        }

        val cmdPath = lines[0]
        val outPath = lines[1]
        val errPath = lines[2]
        val codePath = lines[3]

        val cmdFile = File(cmdPath)
        val cmd = if (cmdFile.exists()) cmdFile.readText() else ""

        req.delete()

        if (cmd.isBlank()) {
            File(errPath).writeText("rish: empty command\n")
            File(codePath).writeText("2")
            return
        }

        if (!PrivilegedAccessManager.hasShizukuPermission()) {
            File(errPath).writeText("Shizuku permission not granted\n")
            File(codePath).writeText("126")
            return
        }

        try {
            var exitCode = -1
            val pathExport = "export PATH=/system/bin:/system/xbin:/vendor/bin:/sbin:/product/bin:/system_ext/bin:\$PATH"
            val script = "$pathExport; $cmd"
            val (ptyInfo, _) = startShizukuShell(
                context,
                "/system/bin/sh",
                "/sdcard",
                arrayOf("sh", "-c", script),
                emptyArray(),
                24,
                80,
                0,
                0
            ) { code ->
                exitCode = code
            }

            withContext(Dispatchers.IO) {
                FileOutputStream(outPath).use { outStream ->
                    java.io.FileInputStream(ptyInfo.ptyFd.fileDescriptor).use { inStream ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val readBytes = try {
                                inStream.read(buffer)
                            } catch (_: IOException) {
                                -1
                            }
                            if (readBytes <= 0) break
                            outStream.write(buffer, 0, readBytes)
                        }
                        outStream.flush()
                    }
                }
            }

            if (exitCode < 0) {
                exitCode = 0
            }
            File(errPath).writeText("")
            File(codePath).writeText(exitCode.toString())
        } catch (t: Throwable) {
            File(errPath).writeText("rish error: ${t.message}\n")
            File(codePath).writeText("125")
        }
    }
}
