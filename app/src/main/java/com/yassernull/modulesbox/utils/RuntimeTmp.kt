package com.yassernull.modulesbox.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host side of the guest directory `/opt/modules-box/.tmp`.
 *
 * The guest path lives inside the proot rootfs, so on the host it is
 * `<distribution>/opt/modules-box/.tmp` — the same place module payloads are staged
 * into. Two files live there:
 *
 *  - [USED_PORTS_FILE]: every port currently held by a running module, one per line.
 *    Custom start scripts read it to pick a free port themselves, so it MUST be
 *    republished whenever a server starts or stops.
 *  - [PORT_FILE]: the port a custom start script chose. The script writes it to file
 *    descriptor 4, which the runner redirects here with
 *    `sh <script> 4>/opt/modules-box/.tmp/port.txt`. Keeping the hand-off on a
 *    dedicated fd rather than stdout means server logs stay clean and parseable.
 *
 * In shizuku/root distribution mode the directory belongs to the shell user, so every
 * write/delete is elevated instead of going through plain file IO.
 */
object RuntimeTmp {
    private const val TAG = "RuntimeTmp"

    /** Guest-visible directory, exactly as referenced by the running shell. */
    const val GUEST_DIR = "/opt/modules-box/.tmp"

    const val USED_PORTS_FILE = "used_ports.txt"
    const val PORT_FILE = "port.txt"

    /** Redirect handed to a custom start script: `sh <script> 4>$GUEST_PORT_REDIRECT`. */
    const val GUEST_PORT_REDIRECT = "$GUEST_DIR/$PORT_FILE"

    private const val GUEST_DIR_RELATIVE = "opt/modules-box/.tmp"
    private val VALID_PORTS = 1..65535
    private val PORT_PATTERN = Regex("\\d+")

    /** Guarded by [prepare] so an activity recreation does not wipe a live port list. */
    private val prepared = AtomicBoolean(false)

    fun hostDir(): File = File(distributionDir(), GUEST_DIR_RELATIVE)

    fun usedPortsFile(): File = File(hostDir(), USED_PORTS_FILE)

    fun portFile(): File = File(hostDir(), PORT_FILE)

    /** Whether [port] can be a TCP port at all, used to reject nonsense readings. */
    fun isValidPort(port: Int): Boolean = port in VALID_PORTS

    /**
     * Creates `/opt/modules-box/.tmp` and drops whatever the previous session left
     * behind, then forgets the tracked ports: no server survives an app restart, so a
     * stale entry would make the next custom start script skip a port for nothing.
     * Runs once per process, on app entry.
     */
    suspend fun prepare(context: Context) {
        withContext(Dispatchers.IO) {
            if (!prepared.compareAndSet(false, true)) return@withContext
            if (ensureDir(context) == null) {
                Log.w(TAG, "prepare: could not create $GUEST_DIR")
                return@withContext
            }
            PortManager.clearUsedPorts(context)
            deleteUsedPorts(context)
            clearAssignedPort(context)
            Log.i(TAG, "prepare: $GUEST_DIR ready, tracked ports reset")
        }
    }

    /**
     * Rewrites [USED_PORTS_FILE] from the ports currently tracked by [PortManager].
     * The file is created even when the list is empty, because custom start scripts
     * bail out with `exit 1` when it is missing.
     */
    suspend fun publishUsedPorts(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            if (ensureDir(context) == null) {
                Log.w(TAG, "publishUsedPorts: $GUEST_DIR is missing")
                return@withContext false
            }
            val ports = PortManager.getUsedPorts(context).sorted()
            val content = if (ports.isEmpty()) "" else ports.joinToString(separator = "\n", postfix = "\n")
            val target = usedPortsFile()
            try {
                if (isDistroShizukuRoot()) {
                    // The shell user owns the directory: hand the content over elevated
                    // through the /sdcard staging copy writeFileToPrivileged performs.
                    val staging = File(context.cacheDir, USED_PORTS_FILE)
                    staging.writeText(content)
                    val ok = PrivilegedFileOps.writeFileToPrivileged(context, staging, target.absolutePath)
                    staging.delete()
                    if (!ok) Log.w(TAG, "publishUsedPorts: elevated write failed for $target")
                    ok
                } else {
                    target.writeText(content)
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "publishUsedPorts failed for $target", e)
                false
            }
        }
    }

    /**
     * The port a custom start script reported on fd 4, or null while it has not written
     * one yet. The script is expected to write the bare number, but a decorated line
     * (`port=6881`) is still accepted; anything outside the TCP range is rejected so a
     * partial or unrelated write is polled again instead of trusted.
     */
    fun readAssignedPort(): Int? {
        val file = portFile()
        if (!file.exists()) return null
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        val port = PORT_PATTERN.find(content)?.value?.toIntOrNull()
        if (port == null) return null
        if (!isValidPort(port)) {
            Log.w(TAG, "readAssignedPort: ignoring out of range port $port")
            return null
        }
        return port
    }

    /** Removes the previous answer so a restarted script cannot be read as stale. */
    suspend fun clearAssignedPort(context: Context): Boolean = withContext(Dispatchers.IO) {
        deleteFile(context, portFile())
    }

    /** Drops both runtime files, keeping the directory itself. */
    suspend fun clearAll(context: Context) {
        withContext(Dispatchers.IO) {
            deleteUsedPorts(context)
            clearAssignedPort(context)
            Log.i(TAG, "clearAll: runtime files removed from $GUEST_DIR")
        }
    }

    private suspend fun deleteUsedPorts(context: Context) {
        deleteFile(context, usedPortsFile())
    }

    private suspend fun deleteFile(context: Context, file: File): Boolean {
        if (!file.exists()) return true
        return try {
            if (isDistroShizukuRoot()) {
                PrivilegedFileOps.deletePrivileged(context, file)
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "delete failed for $file", e)
            false
        }
    }

    private suspend fun ensureDir(context: Context): File? {
        val dir = hostDir()
        return try {
            if (isDistroShizukuRoot()) {
                if (PrivilegedFileOps.runPrivileged(context, "mkdir -p ${dir.absolutePath}")) dir else null
            } else {
                if (dir.isDirectory || dir.mkdirs()) dir else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureDir failed for $dir", e)
            null
        }
    }
}
