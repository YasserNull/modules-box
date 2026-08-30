package com.yassernull.nullbox.utils

import com.yassernull.nullbox.BuildConfig
import java.io.File

// All distribution files live under <dataDir>/local by default. When the user picks
// the shizuku/root distribution permission, the distribution lives under
// /data/local/tmp/null-box instead (the app user cannot write there, so directories
// are created by init-host / the elevated copy, never by plain mkdirs).
private fun getFilesDir(): File {
    return application?.filesDir ?: File("/data/data/com.yassernull.nullbox/files")
}

/** App data dir — the "prefix" of the default (app-private) distribution layout. */
private fun appBase(): File {
    return getFilesDir().parentFile
}

/** Whether the user chose the shizuku/root distribution permission. */
fun isDistroShizukuRoot(): Boolean {
    val context = application ?: return false
    return context.getSharedPreferences("terminal", android.content.Context.MODE_PRIVATE)
        .getInt("distribution_permission", 0) == 1
}

/** True for paths under /data/local/tmp — the app user cannot mkdir there. */
private fun canMkdir(path: File): Boolean {
    return !path.absolutePath.startsWith("/data/local/tmp")
}

/** Base directory of the active distribution (prefix that contains "local"). */
fun distributionBase(): File {
    return if (isDistroShizukuRoot()) File(PrivilegedFileOps.PRIVILEGED_BASE) else appBase()
}

/** Active local dir (contains bin/, lib/, distribution/). */
fun localDir(): File {
    return if (isDistroShizukuRoot()) {
        // For shizuku/root mode, files are directly under /data/local/tmp/null-box/
        distributionBase()
    } else {
        File(distributionBase(), "local").also {
            if (canMkdir(it) && !it.exists()) it.mkdirs()
        }
    }
}

fun distributionDir(): File {
    return localDir().child("distribution").also {
        if (canMkdir(it) && !it.exists()) it.mkdirs()
    }
}

fun distributionHomeDir(): File {
    return distributionDir().child("root").also {
        if (canMkdir(it) && !it.exists()) it.mkdirs()
    }
}

fun localBinDir(): File {
    return localDir().child("bin").also {
        if (canMkdir(it) && !it.exists()) it.mkdirs()
    }
}

fun localLibDir(): File {
    return localDir().child("lib").also {
        if (canMkdir(it) && !it.exists()) it.mkdirs()
    }
}

// The first-launch downloader always writes to the app-private copy first; if the
// shizuku/root permission is later chosen, those files are elevated-copied to the
// privileged path. These helpers address the app-private copy regardless of the
// active permission.

fun appLocalDir(): File {
    return File(appBase(), "local").also {
        if (!it.exists()) it.mkdirs()
    }
}

fun appLocalBinDir(): File {
    return appLocalDir().child("bin").also {
        if (!it.exists()) it.mkdirs()
    }
}

fun appLocalLibDir(): File {
    return appLocalDir().child("lib").also {
        if (!it.exists()) it.mkdirs()
    }
}

fun File.child(fileName: String): File {
    return File(this, fileName)
}

fun File.createFileIfNot(): File {
    if (!exists()) createNewFile()
    return this
}

fun extractFileNameFromUrl(url: String): String {
    return url.substringAfterLast('/').substringBefore('?').ifBlank { "distribution" }
}

fun getDistributionOutputName(url: String): String {
    val fileName = url.substringAfterLast('/').substringBefore('?').lowercase()
    val ext = if (fileName.contains(".tar.")) {
        "tar." + fileName.substringAfterLast(".tar.")
    } else {
        fileName.substringAfterLast('.')
    }
    return "distribution.$ext"
}
