package com.yassernull.modulesbox.utils

import android.content.Context
import android.util.Log
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.repository.ModuleRepository
import com.yassernull.modulesbox.ui.activities.terminal.MkSession
import com.yassernull.modulesbox.ui.activities.terminal.WorkingMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

object ModuleInstaller {
    private const val TAG = "ModuleInstaller"

    /**
     * Temporary debug switch: when true, Play opens the server in TerminalActivity so
     * all output is visible live. Set to false to use the background (detached) runner.
     */
    const val DEBUG_RUN_IN_TERMINAL = false

    private val runningHandles = mutableMapOf<String, ShizukuShellHandle>()
    private val runningPorts = mutableMapOf<String, Int>()
    private val commandOutputs = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val drainJobs = mutableMapOf<String, Job>()
    private val drainStreams = mutableMapOf<String, FileInputStream>()
    private val installerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun hasInstallScript(module: Module): Boolean {
        return !module.install.isNullOrBlank()
    }

    fun getInstallScriptFile(module: Module): File? {
        val installPath = module.install ?: return null
        val scriptFile = File(module.path, installPath)
        return if (scriptFile.exists()) scriptFile else null
    }

    fun getCommandOutput(moduleId: String): String {
        return commandOutputs[moduleId] ?: ""
    }

    fun clearCommandOutput(moduleId: String) {
        commandOutputs.remove(moduleId)
    }

    /**
     * Launches TerminalActivity to run the module install script. The terminal closes
     * itself on success and stays open for debugging on failure (see
     * TerminalActivity.onInstallSessionFinished).
     */
    fun launchInstallTerminal(context: Context, module: Module) {
        val scriptFile = getInstallScriptFile(module) ?: run {
            Log.w(TAG, "Install script not found for module: ${module.id}")
            runCatching { ModuleRepository(context).markModuleInstalled(module.id) }
            return
        }
        val intent = android.content.Intent(
            context,
            com.yassernull.modulesbox.ui.activities.TerminalActivity::class.java
        ).apply {
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_MODULE_ID, module.id)
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_SCRIPT_PATH, scriptFile.absolutePath)
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_MODULE_NAME, module.name)
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_WORKING_DIR, module.path)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getServerCommand(module: Module, port: Int): String = buildModuleCommand(module, port)

    /**
     * Opens TerminalActivity and feeds the server command to its interactive Alpine
     * shell (debug mode). The existing install-command machinery handles it:
     * hasPendingInstall() -> interactive session + scheduleInstallCommand().
     */
    fun launchRunTerminal(context: Context, module: Module, command: String) {
        val intent = android.content.Intent(
            context,
            com.yassernull.modulesbox.ui.activities.TerminalActivity::class.java
        ).apply {
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_MODULE_ID, module.id)
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_MODULE_NAME, module.name)
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_COMMAND, command)
            putExtra(com.yassernull.modulesbox.ui.activities.TerminalActivity.EXTRA_INSTALL_WORKING_DIR, "")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    suspend fun startModuleServer(context: Context, module: Module, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildModuleCommand(module, port)
                val started = startServerProcess(context, module.id, command, port)
                if (started) {
                    runningPorts[module.id] = port
                }
                started
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start module server for: ${module.id}", e)
                commandOutputs[module.id] = "Error: ${e.message}"
                false
            }
        }
    }

    fun stopModuleServer(context: Context, module: Module) {
        tearDownServerProcess(module.id)
        val port = runningPorts.remove(module.id) ?: return
        PortManager.releasePort(context, port)
    }

    /** Stops the shell, the pty drain and drops the handle (no port handling). */
    private fun tearDownServerProcess(moduleId: String) {
        drainJobs.remove(moduleId)?.cancel()
        runCatching { drainStreams.remove(moduleId)?.close() }
        runningHandles.remove(moduleId)?.let { runCatching { it.stop() } }
    }

    fun getModulePort(moduleId: String): Int? {
        return runningPorts[moduleId]
    }

    fun startPhpServer(context: Context, module: Module): Int? {
        val port = PortManager.generateAvailablePort(context) ?: run {
            Log.w(TAG, "No available port for module: ${module.id}")
            return null
        }

        if (!PortManager.acquirePort(context, port)) {
            Log.w(TAG, "Failed to acquire port $port for module: ${module.id}")
            return null
        }

        runningPorts[module.id] = port
        return port
    }

    fun stopPhpServer(context: Context, port: Int) {
        PortManager.releasePort(context, port)
    }

    private fun getProotPath(module: Module): String {
        return "/opt/modules-box/${module.id}"
    }

    private fun buildModuleCommand(module: Module, port: Int): String {
        val prootPath = getProotPath(module)
        val html = module.html
        return if (!html.isNullOrBlank()) {
            "cd '$prootPath' && php -S localhost:$port"
        } else {
            "cd '$prootPath' && export NODE_PATH=/usr/local/lib/node_modules && export PORT=$port && node server.js"
        }
    }

    /**
     * Starts a long-running server (node server.js / php -S) WITHOUT waiting for it to exit.
     * Server commands never exit on success, so no exit code is ever awaited: success
     * is declared as soon as the process survives a short startup window, and the Stop
     * button + port appear immediately. The pty keeps draining in the background (no
     * output-buffer deadlock, Log dialog stays live). Only an early non-zero exit
     * (crash during startup) is reported as failure with the captured logs.
     */
    private suspend fun startServerProcess(
        context: Context,
        moduleId: String,
        command: String,
        port: Int
    ): Boolean = withContext(Dispatchers.IO) {
        tearDownServerProcess(moduleId)
        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        val initFile = File(localBinDir(), "init-host").absolutePath
        val home = distributionHomeDir().path

        val prootCmd = "$linker $initFile proot '$command'"
        val shell = "/system/bin/sh"
        val args = arrayOf(shell, "-c", prootCmd)
        // Same env as an interactive DISTRIBUTION_SHIZUKU terminal session
        // (LINKER, PREFIX, PROOT_LOADER, ...). Without it init-host aborts
        // before proot with "LINKER not set" and the guest never starts.
        val env = MkSession.buildAndroidEnv(context, moduleId, WorkingMode.DISTRIBUTION_SHIZUKU)

        val output = StringBuilder()
        fun snapshot(status: String): String = buildString {
            appendLine("Command: $command")
            appendLine("Status: $status")
            appendLine("--- Output ---")
            synchronized(output) { append(output.toString()) }
        }

        try {
            val deferred = CompletableDeferred<Int>()
            val (ptyInfo, handle) = startShizukuShell(
                context,
                shell,
                home,
                args,
                env,
                24, 80, 0, 0
            ) { exitCode ->
                if (!deferred.isCompleted) deferred.complete(exitCode)
            }

            runningHandles[moduleId] = handle
            commandOutputs[moduleId] = snapshot("starting")

            val stream = FileInputStream(ptyInfo.ptyFd.fileDescriptor)
            drainStreams[moduleId] = stream
            drainJobs[moduleId] = installerScope.launch {
                try {
                    stream.use { inStream ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val readBytes = try {
                                inStream.read(buffer)
                            } catch (_: IOException) {
                                -1
                            }
                            if (readBytes <= 0) break
                            synchronized(output) { output.append(String(buffer, 0, readBytes)) }
                            commandOutputs[moduleId] = snapshot("running")
                        }
                    }
                } catch (_: Exception) {
                }
            }

            // Fast-fail window: a server must NOT exit during startup. Anything alive
            // past it counts as running — no exit code is awaited since servers never
            // exit on their own.
            val earlyExit = withTimeoutOrNull(3_000) { deferred.await() }
            if (earlyExit != null) {
                withTimeoutOrNull(2_000) { drainJobs[moduleId]?.join() }
                val ok = earlyExit == 0 && isPortOpen(port)
                if (!ok) tearDownServerProcess(moduleId)
                commandOutputs[moduleId] = snapshot(if (ok) "exited 0 (listening)" else "exited $earlyExit")
                return@withContext ok
            }

            // Still alive: report success immediately so Stop + port appear at once.
            // Logs keep streaming in the background for the Log button.
            commandOutputs[moduleId] = snapshot("running")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server process for: $moduleId", e)
            tearDownServerProcess(moduleId)
            commandOutputs[moduleId] = "Error: ${e.message}"
            return@withContext false
        }
    }

    /** proot shares the host network namespace, so guest localhost == host localhost. */
    private fun isPortOpen(port: Int): Boolean {
        for (host in arrayOf("127.0.0.1", "localhost")) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 600)
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }
}
