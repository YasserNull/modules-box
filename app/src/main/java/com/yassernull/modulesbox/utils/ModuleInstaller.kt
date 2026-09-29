package com.yassernull.modulesbox.utils

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * How long a custom start script may take to report its port on fd 4.
     */
    private const val PORT_REPORT_TIMEOUT_MS = 20_000L

    /** Poll cadence while waiting for the reported port / the port to accept connections. */
    private const val PORT_POLL_INTERVAL_MS = 150L

    /** How long the reported port gets to accept connections before the start fails. */
    private const val PORT_LISTEN_TIMEOUT_MS = 15_000L

    /** Upper bound on numbers probed per tick, so log noise cannot slow the loop down. */
    private const val MAX_PORT_CANDIDATES = 5

    private val OUTPUT_PORT = Regex("(?m)^[ \\t]*(\\d{2,5})[ \\t]*\\r?$")

    private val runningHandles = mutableMapOf<String, ShizukuShellHandle>()
    private val runningPorts = mutableMapOf<String, Int>()
    private val commandOutputs = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val drainJobs = mutableMapOf<String, Job>()
    private val drainStreams = mutableMapOf<String, FileInputStream>()
    private val installerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serialises server starts. A custom start script reads used_ports.txt and picks a
     * free port itself, so two of them reading the same snapshot concurrently would
     * both claim the same port.
     */
    private val startMutex = Mutex()

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

    fun getServerCommand(module: Module, port: Int): String =
        buildModuleCommand(module, getStartScript(module), port)

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

    /**
     * Starts the module's web server and returns the port it ended up listening on, or
     * null when the start failed (the captured logs stay available for the Log dialog).
     *
     * Modules that declare a `start` script in module.prop own their own port: the app
     * only publishes used_ports.txt, runs `sh <script> 4>/opt/modules-box/.tmp/port.txt`
     * and reads the answer back. Every other module gets an app allocated port and the
     * built-in `php -S` / `node server.js` command.
     */
    suspend fun startModuleServer(context: Context, module: Module): Int? = startMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val startScript = getStartScript(module)
                val port = if (startScript != null) {
                    startCustomServer(context, module, startScript)
                } else {
                    startDefaultServer(context, module)
                } ?: return@withContext null

                runningPorts[module.id] = port
                // Republish so the next custom start script skips the port just taken.
                // The shell user may not see app writes, so this must go through the
                // same elevated path as the first publish.
                if (!RuntimeTmp.publishUsedPorts(context)) {
                    Log.w(TAG, "startModuleServer: could not republish user ports after $port")
                }
                Log.i(TAG, "startModuleServer: ${module.id} listening on $port")
                port
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start module server for: ${module.id}", e)
                commandOutputs[module.id] = "Error: ${e.message}"
                null
            }
        }
    }

    /** php -S / node server.js on a port the app allocates up front. */
    private suspend fun startDefaultServer(context: Context, module: Module): Int? {
        val port = PortManager.generateAvailablePort(context)
        if (port == null || !PortManager.acquirePort(context, port)) {
            Log.w(TAG, "No available port for module: ${module.id}")
            commandOutputs[module.id] = "Error: no available port"
            return null
        }
        val started = startServerProcess(context, module, null, port)
        if (started == null) PortManager.releasePort(context, port)
        return started
    }

    /**
     * Runs the module's own start script. The script picks a free port by reading
     * used_ports.txt and reports the one it used back through file descriptor 4, which
     * the command redirects into /opt/modules-box/.tmp/port.txt. Nothing is guessed
     * here: the port is whatever the script answered.
     */
    private suspend fun startCustomServer(context: Context, module: Module, startScript: String): Int? {
        if (!RuntimeTmp.publishUsedPorts(context)) {
            commandOutputs[module.id] = "Error: cannot write ${RuntimeTmp.USED_PORTS_FILE}"
            return null
        }
        // Drop the previous answer first, otherwise a script that dies before writing
        // would still look like it reported a port.
        RuntimeTmp.clearAssignedPort(context)

        val port = startServerProcess(context, module, startScript, null) ?: return null
        if (!PortManager.acquirePort(context, port)) {
            // The script may legitimately land on a port the app already tracks; the
            // server owns it either way, so keep running and just note the overlap.
            Log.w(TAG, "startCustomServer: ${module.id} reported already tracked port $port")
        }
        return port
    }

    suspend fun stopModuleServer(context: Context, module: Module) {
        tearDownServerProcess(module.id)
        val port = runningPorts.remove(module.id) ?: return
        PortManager.releasePort(context, port)
        // Free the port for the next custom start script immediately.
        RuntimeTmp.publishUsedPorts(context)
        Log.i(TAG, "stopModuleServer: ${module.id} released port $port")
    }

    /**
     * Drops the guest runtime files when the app is closed for good, so nothing stale
     * survives into the next session. Runs on the installer scope because the activity
     * that triggered it is already tearing down.
     */
    fun onAppClosed(context: Context) {
        val appContext = context.applicationContext
        installerScope.launch { RuntimeTmp.clearAll(appContext) }
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

    private fun getProotPath(module: Module): String {
        return "/opt/modules-box/${module.id}"
    }

    /**
     * Guest path of the module's custom start script, or null when the module declares
     * none. A declared-but-missing file falls back to the built-in command so a module
     * is still startable instead of failing outright.
     */
    fun getStartScript(module: Module): String? {
        val start = module.start?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Checked against the host copy: a declared-but-absent script would otherwise
        // reach the guest as a "not found" exit and look like a crash.
        if (!File(module.path, start).exists()) {
            Log.w(TAG, "getStartScript: ${module.id} declares start=$start but the file is missing")
            return null
        }
        return "${getProotPath(module)}/${start.trimStart('/')}"
    }

    /**
     * @param startScript the module's own start script, or null to use the built-in one.
     * @param port the port the app allocated, or null when [startScript] chooses it and
     * reports it back on fd 4.
     */
    private fun buildModuleCommand(module: Module, startScript: String?, port: Int?): String {
        val prootPath = getProotPath(module)
        if (startScript != null) {
            // No quotes: init-host wraps the whole command in '...' before handing it to
            // the guest, so any quote here would break the argument it builds.
            return "cd $prootPath && sh $startScript 4>${RuntimeTmp.GUEST_PORT_REDIRECT}"
        }
        val php = !module.html.isNullOrBlank()
        return if (php) {
            "cd $prootPath && php -S localhost:$port"
        } else {
            "cd $prootPath && export NODE_PATH=/usr/local/lib/node_modules && export PORT=$port && node server.js"
        }
    }

    /**
     * Starts a long-running server (node server.js / php -S / a custom start script)
     * WITHOUT waiting for it to exit. Server commands never exit on success, so no exit
     * code is ever awaited: success is declared as soon as the process survives a short
     * startup window and the port is known, and the Stop button + port appear
     * immediately. The pty keeps draining in the background (no output-buffer deadlock,
     * Log dialog stays live). Only an early non-zero exit (crash during startup) is
     * reported as failure with the captured logs.
     *
     * @return the resolved port, or null when the start failed.
     */
    private suspend fun startServerProcess(
        context: Context,
        module: Module,
        startScript: String?,
        port: Int?
    ): Int? = withContext(Dispatchers.IO) {
        val moduleId = module.id
        val command = buildModuleCommand(module, startScript, port)
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

            if (port == null) {
                return@withContext awaitReportedPort(deferred, output, { status -> snapshot(status) }, moduleId)
            }

            // No startup window. A server command never exits on success, so any wait here
            // is pure added latency on the UI, and the start should feel exactly as quick
            // as running the same command in a terminal. The single case still worth
            // handling is a process that has already died — that check is free.
            if (deferred.isCompleted) {
                val exitCode = runCatching { deferred.await() }.getOrDefault(-1)
                withTimeoutOrNull(2_000) { drainJobs[moduleId]?.join() }
                val ok = exitCode == 0 && isPortOpen(port)
                if (!ok) tearDownServerProcess(moduleId)
                commandOutputs[moduleId] = snapshot(if (ok) "exited 0 (listening)" else "exited $exitCode")
                return@withContext if (ok) port else null
            }

            // Still alive: report success immediately so Stop + port appear at once.
            // Logs keep streaming in the background for the Log button.
            commandOutputs[moduleId] = snapshot("running")
            return@withContext port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server process for: $moduleId", e)
            tearDownServerProcess(moduleId)
            commandOutputs[moduleId] = "Error: ${e.message}"
            return@withContext null
        }
    }

    /**
     * Waits for a custom start script to report the port it used, then confirms it.
     *
     * Two channels are accepted because scripts report in two ways: through fd 4 (which
     * the command redirects into port.txt) and through a bare `echo "$PORT"` line in the
     * pty output, which is how the reference script speaks. Every candidate is verified
     * by connecting to it, so a number picked up from log noise can never be mistaken
     * for the server. A script that dies without reporting, or reports a port nothing
     * ever binds, fails the start instead of leaving a dead entry in the list.
     */
    private suspend fun awaitReportedPort(
        deferred: CompletableDeferred<Int>,
        output: StringBuilder,
        snapshot: (String) -> String,
        moduleId: String
    ): Int? {
        val deadline = SystemClock.elapsedRealtime() + PORT_REPORT_TIMEOUT_MS + PORT_LISTEN_TIMEOUT_MS
        val candidates = LinkedHashSet<Int>()

        fun collect(): Boolean {
            RuntimeTmp.readAssignedPort()?.let { candidates.add(it) }
            // Keep the tail only: a script prints the port at startup, so later numbers
            // are log noise, and probing them all on every tick would be wasteful.
            reportedPortsFrom(output).takeLast(MAX_PORT_CANDIDATES).forEach { candidates.add(it) }
            return candidates.isNotEmpty()
        }

        while (SystemClock.elapsedRealtime() < deadline) {
            val reported = collect()
            candidates.firstOrNull { isPortOpen(it) }?.let { listening ->
                commandOutputs[moduleId] = snapshot("running on $listening")
                Log.i(TAG, "awaitReportedPort: $moduleId listening on $listening")
                return listening
            }
            if (!reported && deferred.isCompleted) {
                tearDownServerProcess(moduleId)
                commandOutputs[moduleId] = snapshot("exited before reporting a port")
                return null
            }
            delay(PORT_POLL_INTERVAL_MS)
        }

        tearDownServerProcess(moduleId)
        val seen = candidates.takeIf { it.isNotEmpty() }?.joinToString() ?: "none"
        commandOutputs[moduleId] = snapshot("no reported port started listening (seen: $seen)")
        Log.w(TAG, "awaitReportedPort: $moduleId never opened a reported port (seen: $seen)")
        return null
    }

    /** Bare-number lines in the pty output, i.e. what `echo "$PORT"` prints there. */
    private fun reportedPortsFrom(output: StringBuilder): List<Int> {
        val text = synchronized(output) { output.toString() }
        return OUTPUT_PORT.findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { RuntimeTmp.isValidPort(it) }
            .toList()
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
