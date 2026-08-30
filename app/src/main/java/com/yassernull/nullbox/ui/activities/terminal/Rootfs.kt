package com.yassernull.nullbox.ui.activities.terminal

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.preferences.terminal.isDistributionPermissionChosen
import com.yassernull.nullbox.utils.PrivilegedFileOps
import com.yassernull.nullbox.utils.appLocalDir
import com.yassernull.nullbox.utils.child
import com.yassernull.nullbox.utils.isDistroShizukuRoot
import com.yassernull.nullbox.utils.localBinDir
import com.yassernull.nullbox.utils.localDir
import com.yassernull.nullbox.utils.localLibDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Rootfs {
    private const val TAG = "Rootfs"

    /** Active distribution local dir (app-private, or /data/local/tmp/null-box when shizuku/root). */
    val Terminal: File
        get() = localDir()

    var isDownloaded = mutableStateOf(isFilesDownloaded())
    var needsDistributionInit = mutableStateOf(false)

    fun recheck() {
        isDownloaded.value = isFilesDownloaded()
    }

    /**
     * Whether the setup is fully complete: files are downloaded AND the user has chosen
     * a distribution permission. Returns false when the downloader should be shown.
     */
    fun isSetupComplete(context: android.content.Context): Boolean {
        if (!isFilesDownloaded()) return false
        return AppPreferences(context).isDistributionPermissionChosen()
    }

    suspend fun ensureBinaries(context: android.content.Context) {
        withContext(Dispatchers.IO) {
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            // For shizuku/root mode, localBinDir() already resolves to /data/local/tmp/null-box/bin/
            // For default mode, localBinDir() resolves to <app-private>/local/bin/
            val binDir = localBinDir()

            val rishSource = nativeLibDir.child("librish.so")
            val initHostSource = nativeLibDir.child("libinit-host.so")

            val rishTarget = binDir.child("rish")
            val initHostTarget = binDir.child("init-host")

            val privileged = isDistroShizukuRoot()

            fun needsCopy(source: File, target: File): Boolean {
                return source.exists() && (!target.exists() || target.length() != source.length())
            }

            if (needsCopy(rishSource, rishTarget)) {
                if (privileged) {
                    PrivilegedFileOps.writeFileToPrivileged(context, rishSource, rishTarget.path)
                } else {
                    rishSource.inputStream().use { input ->
                        rishTarget.outputStream().use { output -> input.copyTo(output) }
                    }
                    rishTarget.setExecutable(true, false)
                }
            }

            if (needsCopy(initHostSource, initHostTarget)) {
                if (privileged) {
                    PrivilegedFileOps.writeFileToPrivileged(context, initHostSource, initHostTarget.path)
                } else {
                    initHostSource.inputStream().use { input ->
                        initHostTarget.outputStream().use { output -> input.copyTo(output) }
                    }
                    initHostTarget.setExecutable(true, false)
                }
            }
        }
    }

    /**
     * Copies the essential distribution files to /data/local/tmp/null-box:
     *  - bin/proot, bin/busybox
     *  - lib/libtalloc.so.2, lib/libproot-loader.so, lib/libproot-loader32.so
     *  - distribution.* (the alpine archive)
     *
     * For root: direct cp via libsu.
     * For shizuku: copy to /sdcard first, then shizuku shell copies from /sdcard.
     * Decompression happens later in the terminal, not during the download screen.
     */
    suspend fun ensureDistributionAtPrivilegedPath(context: android.content.Context): Boolean {
        Log.i(TAG, "ensureDistributionAtPrivilegedPath: starting copy")
        val source = appLocalDir()
        Log.i(TAG, "ensureDistributionAtPrivilegedPath: source=${source.path}, exists=${source.exists()}")

        // Step 1: Copy essential files from app-private to /data/local/tmp/null-box
        val copyResult = PrivilegedFileOps.copyDistributionToPrivileged(context)
        Log.i(TAG, "ensureDistributionAtPrivilegedPath: copyDistributionToPrivileged result=$copyResult")
        if (!copyResult) return false

        // Step 2: Copy proot-loaders from native lib dir to target lib/
        val target = File(PrivilegedFileOps.PRIVILEGED_BASE)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        for (name in listOf("libproot-loader.so", "libproot-loader32.so")) {
            val src = nativeDir.child(name)
            Log.i(TAG, "ensureDistributionAtPrivilegedPath: checking $name, exists=${src.exists()}")
            if (src.exists()) {
                val writeResult = PrivilegedFileOps.writeFileToPrivileged(
                    context, src, File(target, "lib/$name").path
                )
                Log.i(TAG, "ensureDistributionAtPrivilegedPath: writeFileToPrivileged $name result=$writeResult")
                if (!writeResult) {
                    Log.e(TAG, "ensureDistributionAtPrivilegedPath: failed to write $name")
                    return false
                }
            }
        }

        Log.i(TAG, "ensureDistributionAtPrivilegedPath: copy completed successfully")
        return true
    }

    fun isFilesDownloaded(): Boolean {
        val binDir = localBinDir()
        val libDir = localLibDir()

        // rish and init-host are copied later by ensureBinaries() after the terminal opens,
        // so don't check for them here.
        val essentialBinariesExist = binDir.child("proot").exists() &&
            binDir.child("busybox").exists() &&
            libDir.child("libtalloc.so.2").exists()

        if (!essentialBinariesExist) return false

        // Check for any file that starts with "distribution." in the local directory
        val distributionFileExists = Terminal.listFiles()?.any {
            it.name.startsWith("distribution.") && !it.name.endsWith(".part")
        } ?: false

        return distributionFileExists
    }
}
