package com.yassernull.modulesbox.utils

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Elevated file operations for the shizuku/root distribution permission.
 *
 * The app user cannot write to /data/local/tmp, and the shell user cannot read
 * app-private data. So:
 *  - root: run `cp`/`rm` via libsu (root can read both sides).
 *  - shizuku: copy to /sdcard first (world-readable), then shizuku shell copies
 *    from /sdcard to the target.
 */
object PrivilegedFileOps {
    private const val TAG = "PrivilegedFileOps"
    const val PRIVILEGED_BASE = "/data/local/tmp/modules-box"
    private const val STAGING_DIR = "/sdcard/.modulesbox_staging"

    /** Runs a shell command with root, or via the shizuku shell. */
    suspend fun runPrivileged(context: Context, command: String): Boolean {
        return withContext(Dispatchers.IO) {
            when {
                PrivilegedAccessManager.hasRootPermission() -> {
                    Shell.cmd(command).exec().isSuccess
                }

                PrivilegedAccessManager.hasShizukuPermission() -> {
                    runViaShizuku(context, "$command >/dev/null 2>&1")
                }

                else -> false
            }
        }
    }

    /**
     * Copies specific files from the app-private directory to the privileged path.
     * Only copies essential files: proot, busybox, libtalloc, proot-loaders, distribution.
     *
     * For root: direct cp via libsu.
     * For shizuku: copy to /sdcard/ first, then shizuku shell copies from /sdcard/.
     */
    suspend fun copyDistributionToPrivileged(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val target = File(PRIVILEGED_BASE)
            val source = appLocalDir()
            Log.i(TAG, "copyDistributionToPrivileged: source=${source.path}, target=${target.path}")
            Log.i(TAG, "copyDistributionToPrivileged: source exists=${source.exists()}")

            val hasRoot = PrivilegedAccessManager.hasRootPermission()
            val hasShizuku = PrivilegedAccessManager.hasShizukuPermission()
            Log.i(TAG, "copyDistributionToPrivileged: hasRoot=$hasRoot, hasShizuku=$hasShizuku")

            if (!hasRoot && !hasShizuku) {
                Log.e(TAG, "copyDistributionToPrivileged: no root or shizuku permission")
                return@withContext false
            }

            // List of files to copy: (sourceRelativePath, targetRelativePath)
            val filesToCopy = mutableListOf<Pair<String, String>>()

            // Binaries
            val binDir = source.child("bin")
            for (name in listOf("proot", "busybox")) {
                val src = binDir.child(name)
                if (src.exists()) {
                    filesToCopy.add("bin/$name" to "bin/$name")
                }
            }

            // Libraries
            val libDir = source.child("lib")
            for (name in listOf("libtalloc.so.2")) {
                val src = libDir.child(name)
                if (src.exists()) {
                    filesToCopy.add("lib/$name" to "lib/$name")
                }
            }

            // Distribution archive (any file starting with "distribution.")
            source.listFiles()?.forEach { file ->
                if (file.name.startsWith("distribution.") && !file.name.endsWith(".part")) {
                    filesToCopy.add(file.name to file.name)
                }
            }

            Log.i(TAG, "copyDistributionToPrivileged: ${filesToCopy.size} files to copy")

            if (hasRoot) {
                copyFilesViaRoot(source, target, filesToCopy)
            } else {
                copyFilesViaShizuku(context, source, target, filesToCopy)
            }
        }
    }

    /** Copies files directly via root (root can read app-private storage). */
    private suspend fun copyFilesViaRoot(
        source: File,
        target: File,
        files: List<Pair<String, String>>
    ): Boolean {
        // Create target directories
        val mkdirCmd = "mkdir -p '${target.path}/bin' '${target.path}/lib'"
        Log.i(TAG, "copyFilesViaRoot: mkdir: $mkdirCmd")
        val mkdirResult = Shell.cmd(mkdirCmd).exec()
        Log.i(TAG, "copyFilesViaRoot: mkdir result=${mkdirResult.isSuccess}")

        for ((srcRel, dstRel) in files) {
            val src = File(source, srcRel)
            val dst = File(target, dstRel)
            val cmd = "cp -f '${src.path}' '${dst.path}' && chmod a+rX '${dst.path}'"
            Log.i(TAG, "copyFilesViaRoot: copying $srcRel -> $dstRel")
            val result = Shell.cmd(cmd).exec()
            if (!result.isSuccess) {
                Log.e(TAG, "copyFilesViaRoot: failed to copy $srcRel: ${result.err.joinToString("\n")}")
                return false
            }
        }

        Log.i(TAG, "copyFilesViaRoot: all files copied successfully")
        return true
    }

    /**
     * Copies files via shizuku using /sdcard as intermediary.
     * Shizuku shell cannot read app-private storage, but can read /sdcard.
     */
    private suspend fun copyFilesViaShizuku(
        context: Context,
        source: File,
        target: File,
        files: List<Pair<String, String>>
    ): Boolean {
        // Create staging dir on /sdcard
        val stagingDir = File(STAGING_DIR)
        stagingDir.mkdirs()
        Log.i(TAG, "copyFilesViaShizuku: staging dir=${stagingDir.path}")

        // Create target directories via shizuku
        if (!runViaShizuku(context, "mkdir -p '${target.path}/bin' '${target.path}/lib' >/dev/null 2>&1")) {
            Log.e(TAG, "copyFilesViaShizuku: failed to create target dirs")
            return false
        }

        // For each file: copy to /sdcard, then shizuku shell copies from /sdcard to target
        for ((srcRel, dstRel) in files) {
            val srcFile = File(source, srcRel)
            val stagingFile = File(stagingDir, srcRel.replace("/", "_"))

            // Step 1: Copy from app-private to /sdcard (app has write access to /sdcard)
            Log.i(TAG, "copyFilesViaShizuku: staging $srcRel -> ${stagingFile.path}")
            try {
                srcFile.inputStream().use { input ->
                    stagingFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "copyFilesViaShizuku: failed to stage $srcRel", e)
                cleanStaging(stagingDir)
                return false
            }

            // Step 2: Shizuku shell copies from /sdcard to target
            val dstFile = File(target, dstRel)
            // chmod 755 (not a+rX): the /sdcard staging cp strips the exec bit, and `a+rX`
            // only re-adds it when the file is already executable for some user — so the
            // proot loaders would end up non-executable and fail with "execve: Permission denied".
            val cmd = "cp -f '${stagingFile.path}' '${dstFile.path}' && chmod 755 '${dstFile.path}'"
            Log.i(TAG, "copyFilesViaShizuku: shizuku cp $srcRel -> $dstRel")
            if (!runViaShizuku(context, cmd)) {
                Log.e(TAG, "copyFilesViaShizuku: shizuku cp failed for $srcRel")
                cleanStaging(stagingDir)
                return false
            }
        }

        // Clean up staging dir
        cleanStaging(stagingDir)
        Log.i(TAG, "copyFilesViaShizuku: all files copied successfully")
        return true
    }

    /** Removes the staging directory on /sdcard. */
    private fun cleanStaging(stagingDir: File) {
        try {
            stagingDir.deleteRecursively()
            Log.i(TAG, "cleanStaging: removed ${stagingDir.path}")
        } catch (e: Exception) {
            Log.w(TAG, "cleanStaging: failed to remove staging dir", e)
        }
    }

    /** Copies a single app-readable [source] file to [targetPath] with elevated access. */
    suspend fun writeFileToPrivileged(context: Context, source: File, targetPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            when {
                PrivilegedAccessManager.hasRootPermission() -> {
                    val parent = File(targetPath).parentFile?.path ?: PRIVILEGED_BASE
                    val cmd = "mkdir -p '$parent' && cp '${source.path}' '$targetPath' && chmod a+rX '$targetPath'"
                    Shell.cmd(cmd).exec().isSuccess
                }

                PrivilegedAccessManager.hasShizukuPermission() -> {
                    // Copy to /sdcard first, then shizuku shell copies from /sdcard
                    val stagingFile = File(STAGING_DIR, File(targetPath).name)
                    stagingFile.parentFile?.mkdirs()
                    try {
                        source.inputStream().use { input ->
                            stagingFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        val parent = File(targetPath).parentFile?.path ?: PRIVILEGED_BASE
                        val cmd = "mkdir -p '$parent' && cp -f '${stagingFile.path}' '$targetPath' && chmod 755 '$targetPath'"
                        val result = runViaShizuku(context, cmd)
                        stagingFile.delete()
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "writeFileToPrivileged shizuku failed: $targetPath", e)
                        stagingFile.delete()
                        false
                    }
                }

                else -> false
            }
        }
    }

    /** Copies a whole app-readable directory to [targetPath] with elevated access. */
    suspend fun copyDirToPrivileged(context: Context, sourceDir: File, targetPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            when {
                PrivilegedAccessManager.hasRootPermission() -> {
                    // Root can read app-private storage directly.
                    val cmd = "rm -rf '$targetPath' && mkdir -p '$targetPath' && cp -r '${sourceDir.path}/.' '$targetPath/' && chmod -R a+rX '$targetPath'"
                    Shell.cmd(cmd).exec().isSuccess
                }

                PrivilegedAccessManager.hasShizukuPermission() -> {
                    // Shizuku shell cannot read app-private storage: recreate dirs
                    // elevated, then copy each file via writeFileToPrivileged (/sdcard staging).
                    if (!runViaShizuku(context, "rm -rf '$targetPath' && mkdir -p '$targetPath' >/dev/null 2>&1")) {
                        return@withContext false
                    }
                    var ok = true
                    sourceDir.walkTopDown().forEach { file ->
                        if (!ok) return@forEach
                        val rel = file.relativeTo(sourceDir).path
                        if (rel.isEmpty()) return@forEach
                        val dest = "$targetPath/$rel"
                        ok = if (file.isDirectory) {
                            runViaShizuku(context, "mkdir -p '$dest' >/dev/null 2>&1")
                        } else {
                            writeFileToPrivileged(context, file, dest)
                        }
                    }
                    ok
                }

                else -> false
            }
        }
    }

    /** Deletes [target] (recursively) with elevated access. */
    suspend fun deletePrivileged(context: Context, target: File): Boolean {
        return withContext(Dispatchers.IO) {
            when {
                PrivilegedAccessManager.hasRootPermission() -> {
                    Shell.cmd("rm -rf '${target.path}'").exec().isSuccess
                }

                PrivilegedAccessManager.hasShizukuPermission() -> {
                    runViaShizuku(context, "rm -rf '${target.path}' >/dev/null 2>&1")
                }

                else -> false
            }
        }
    }

    private suspend fun runViaShizuku(context: Context, command: String): Boolean {
        val deferred = CompletableDeferred<Int>()
        return try {
            val (ptyInfo, handle) = startShizukuShell(
                context,
                "/system/bin/sh",
                "/data/local/tmp",
                arrayOf("/system/bin/sh", "-c", command),
                emptyArray(),
                24, 80, 0, 0
            ) { exitCode -> deferred.complete(exitCode) }
            val fd = ptyInfo.ptyFd.detachFd()
            runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            val exit = withTimeoutOrNull(120_000) { deferred.await() } ?: -1
            runCatching { handle.stop() }
            exit == 0
        } catch (e: Throwable) {
            Log.e(TAG, "shizuku command failed: $command", e)
            false
        }
    }
}
