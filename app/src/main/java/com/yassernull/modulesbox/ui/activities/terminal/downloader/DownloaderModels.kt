package com.yassernull.modulesbox.ui.activities.terminal.downloader

import java.io.File

data class DistributionUi(
    val key: String,
    val name: String,
    val description: String?,
    val icon: String
)

data class ReleaseOption(
    val key: String,
    val name: String,
    val version: String
)

data class DistributionConfig(
    val ui: DistributionUi,
    val urlTemplate: String,
    val architectures: List<String>,
    val releases: List<ReleaseOption>
)

data class DownloadFile(val url: String, val outputFile: File)

data class AbiUrls(
    val talloc: String,
    val busybox: String,
    val proot: String
)

class CancelledDownloadException : Exception()
