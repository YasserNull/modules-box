package com.yassernull.nullbox.ui.activities.terminal.downloader

import android.content.Context
import android.os.Build
import com.yassernull.nullbox.R
import com.yassernull.nullbox.utils.formatProgress
import com.yassernull.nullbox.utils.runOnUiThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

val abiMap = mapOf(
    "x86_64" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/libtalloc.so.2",
        busybox = "https://github.com/YasserNull/ndk-box-kitchen/releases/download/build%231/libbusybox-x86_64.so",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/proot"
    ),
    "arm64-v8a" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/libtalloc.so.2",
        busybox = "https://github.com/YasserNull/ndk-box-kitchen/releases/download/build%231/libbusybox-arm64-v8a.so",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/proot"
    ),
    "armeabi-v7a" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/libtalloc.so.2",
        busybox = "https://github.com/YasserNull/ndk-box-kitchen/releases/download/build%231/libbusybox-armeabi-v7a.so",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/proot"
    )
)

/**
 * Modelbox ships a single hardcoded Alpine Linux distribution (proot only).
 * Minirootfs 3.24.1 for aarch64/armv7/x86_64/x86.
 */
fun loadAlpineConfig(): DistributionConfig {
    return DistributionConfig(
        ui = DistributionUi(
            key = "alpine",
            name = "Alpine Linux",
            description = null,
            icon = "alpine.png"
        ),
        urlTemplate = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/\${architecture}/alpine-minirootfs-3.24.1-\${architecture}.tar.gz",
        architectures = listOf("aarch64", "armv7", "x86_64", "x86"),
        releases = emptyList()
    )
}

fun buildDistributionUrl(
    config: DistributionConfig,
    context: Context,
    release: ReleaseOption? = null
): String? {
    if (config.urlTemplate.isBlank()) return null
    val abi = Build.SUPPORTED_ABIS.firstOrNull()
    val archToken = resolveArchitectureToken(config.architectures, abi) ?: return null
    var url = config.urlTemplate
    if (release != null) {
        url = url.replace("\${release}", release.key)
        url = url.replace("\${version}", release.version)
    }
    url = url.replace("\${architecture}", archToken)
    url = url.replace("\${arch}", archToken)
    return url
}

private fun resolveArchitectureToken(architectures: List<String>, abi: String?): String? {
    if (architectures.isEmpty()) return null
    val normalizedAbi = abi ?: return null
    val candidates = when (normalizedAbi) {
        "arm64-v8a" -> listOf("arm64", "aarch64")
        "armeabi-v7a" -> listOf("armv7", "armhf", "arm")
        "x86_64" -> listOf("x86_64", "amd64")
        "x86" -> listOf("x86", "i686")
        else -> emptyList()
    }
    return candidates.firstOrNull { architectures.contains(it) }
}

suspend fun resolveWildcardUrl(url: String): String? {
    val marker = "\${.*}"
    if (!url.contains(marker)) return url

    val baseUrl = url.substringBeforeLast("/") + "/"
    val filePattern = url.substringAfterLast("/")

    val parts = filePattern.split(marker)
    val regexString = parts.joinToString("""([^"'<>\s]+)""") { Regex.escape(it) }
    val regex = Regex(regexString)

    return withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null

                val files = regex.findAll(body)
                    .map { it.value }
                    .distinct()
                    .toList()

                if (files.isEmpty()) return@withContext null

                val latest = files.maxWithOrNull { a, b ->
                    compareMixedVersionTokens(a, b)
                } ?: return@withContext null

                baseUrl + latest
            }
        } catch (_: Exception) {
            null
        }
    }
}

suspend fun setupEnvironment(
    context: Context,
    filesToDownload: List<DownloadFile>,
    onProgress: (fileName: String, fileBytesProgress: String, filePercentage: Float, overallProgressText: String) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit,
    isCancelled: () -> Boolean
) {
    withContext(Dispatchers.IO) {
        try {
            var completedFiles = 0
            val totalFiles = filesToDownload.size

            for (file in filesToDownload) {
                if (isCancelled()) throw CancelledDownloadException()
                completedFiles++
                val overallText = context.getString(
                    R.string.downloader_file_progress_overall, completedFiles, totalFiles
                )
                val outputFile = file.outputFile.apply { parentFile?.mkdirs() }

                if (!outputFile.exists()) {
                    if (isCancelled()) throw CancelledDownloadException()
                    runOnUiThread { onProgress(file.url, "", 0f, overallText) }
                    downloadFile(file.url, outputFile, isCancelled) { downloaded, total ->
                        val percentage = if (total > 0) downloaded.toFloat() / total else 0f
                        val bytesText = formatProgress(downloaded, total)
                        runOnUiThread { onProgress(file.url, bytesText, percentage, overallText) }
                    }
                }
                if (isCancelled()) throw CancelledDownloadException()
                val bytesText = if (outputFile.exists()) {
                    formatProgress(outputFile.length(), outputFile.length())
                } else {
                    context.getString(R.string.downloader_file_completed)
                }
                runOnUiThread { onProgress(file.url, bytesText, 1f, overallText) }

                val name = outputFile.name.lowercase()
                val isArchive = name.endsWith(".tar.xz") ||
                    name.endsWith(".tar.gz") ||
                    name.endsWith(".tgz") ||
                    name.endsWith(".txz") ||
                    name.endsWith(".tar.zst") ||
                    name.endsWith(".zip")

                if (!isArchive && !name.endsWith(".so") && !name.contains(".so.")) {
                    outputFile.setExecutable(true, false)
                }
            }
            runOnUiThread { onComplete() }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e) }
        }
    }
}

suspend fun downloadFile(
    url: String,
    outputFile: File,
    isCancelled: () -> Boolean,
    onProgress: (Long, Long) -> Unit
) {
    withContext(Dispatchers.IO) {
        val partFile = File(outputFile.parentFile, outputFile.name + ".part")
        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            .apply {
                if (downloadedBytes > 0) {
                    addHeader("Range", "bytes=$downloadedBytes-")
                }
            }
            .build()

        try {
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw Exception("Failed to download file: ${response.code}")
                }

                val isResuming = response.code == 206
                if (response.code == 200) {
                    downloadedBytes = 0L
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = if (isResuming) {
                    downloadedBytes + body.contentLength()
                } else {
                    body.contentLength()
                }

                java.io.FileOutputStream(partFile, isResuming).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled()) throw CancelledDownloadException()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                        }
                    }
                }

                if (totalBytes > 0 && downloadedBytes < totalBytes) {
                    return@withContext
                }

                partFile.renameTo(outputFile)
            }
        } catch (e: Exception) {
            throw e
        }
    }
}

private fun compareMixedVersionTokens(a: String, b: String): Int {
    val tokenA = Regex("""(\d+)""").findAll(a).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()
    val tokenB = Regex("""(\d+)""").findAll(b).map { it.groupValues[1].toIntOrNull() ?: 0 }.toList()

    val maxSize = maxOf(tokenA.size, tokenB.size)
    for (i in 0 until maxSize) {
        val aVal = tokenA.getOrElse(i) { 0 }
        val bVal = tokenB.getOrElse(i) { 0 }
        if (aVal != bVal) return aVal.compareTo(bVal)
    }
    return 0
}
