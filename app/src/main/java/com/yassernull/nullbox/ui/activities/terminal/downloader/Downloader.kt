package com.yassernull.nullbox.ui.activities.terminal.downloader

import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.yassernull.nullbox.R
import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.preferences.terminal.setTerminalDistributionPermission
import com.yassernull.nullbox.ui.activities.TerminalActivity
import com.yassernull.nullbox.ui.activities.terminal.DistroPermission
import com.yassernull.nullbox.ui.activities.terminal.Rootfs
import com.yassernull.nullbox.ui.components.SettingsCard
import com.yassernull.nullbox.utils.appLocalBinDir
import com.yassernull.nullbox.utils.appLocalDir
import com.yassernull.nullbox.utils.appLocalLibDir
import com.yassernull.nullbox.utils.child
import com.yassernull.nullbox.utils.extractFileNameFromUrl
import com.yassernull.nullbox.utils.getDistributionOutputName
import com.yassernull.nullbox.utils.PrivilegedAccessManager
import com.yassernull.nullbox.utils.toast
import kotlinx.coroutines.launch
import java.net.UnknownHostException

/**
 * First-launch Alpine Linux downloader. Alpine is the only supported distribution,
 * so the setup auto-selects it and downloads directly — there is no distribution
 * selection screen. After the download finishes the user picks a distribution
 * permission (default/root vs shizuku/root); when the shizuku/root permission is
 * chosen the distribution is copied to /data/local/tmp/null-box with elevated access.
 */
@Composable
fun Downloader(
    modifier: Modifier = Modifier,
    terminalActivity: TerminalActivity
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(color = backgroundColor, darkIcons = onBackgroundColor.luminance() > 0.5f)
        systemUiController.setNavigationBarColor(color = backgroundColor, darkIcons = onBackgroundColor.luminance() > 0.5f)
    }

    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(terminalActivity) }

    var progress by remember { mutableFloatStateOf(0f) }
    var overallProgressText by remember { mutableStateOf("") }
    var currentFileName by remember { mutableStateOf("") }
    var fileProgressText by remember { mutableStateOf("") }
    var isSetupComplete by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var isResolving by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableStateOf(0) }
    var selectedDistributionUrl by remember { mutableStateOf<String?>(null) }
    var cancelRequested by remember { mutableStateOf(false) }

    // Distribution permission chooser state.
    var showPermissionChooser by remember { mutableStateOf(false) }
    var showShizukuWarning by remember { mutableStateOf(false) }
    var isCopying by remember { mutableStateOf(false) }
    var setupFinished by remember { mutableStateOf(false) }

    val alpineConfig = remember { loadAlpineConfig() }

    fun finishSetup(permission: Int) {
        preferences.setTerminalDistributionPermission(permission)
        showPermissionChooser = false
        showShizukuWarning = false
        setupFinished = true
        Rootfs.recheck()
    }

    // After setup finishes, the parent (TerminalActivity) will recompose and show
    // the terminal instead of this Downloader. Return early to avoid showing a blank screen.
    if (setupFinished) return

    LaunchedEffect(retryTrigger, selectedDistributionUrl) {
        if (selectedDistributionUrl == null) {
            // Alpine is the only supported distribution → resolve its URL automatically.
            val url = buildDistributionUrl(alpineConfig, terminalActivity)
            if (url == null) {
                toast(terminalActivity.getString(R.string.architecture_not_supported))
                hasError = true
            } else {
                isResolving = true
                val resolvedUrl = resolveWildcardUrl(url)
                isResolving = false
                if (resolvedUrl == null) {
                    toast(terminalActivity.getString(R.string.wildcard_resolution_failed))
                    hasError = true
                } else {
                    selectedDistributionUrl = resolvedUrl
                }
            }
            return@LaunchedEffect
        }

        progress = 0f
        overallProgressText = terminalActivity.getString(R.string.downloader_progress_installing)
        currentFileName = ""
        fileProgressText = ""
        hasError = false
        isSetupComplete = false
        needsDownload = false
        cancelRequested = false
        showPermissionChooser = false
        showShizukuWarning = false
        isCopying = false

        try {
            val abi = Build.SUPPORTED_ABIS.firstOrNull {
                it in abiMap
            } ?: throw RuntimeException(terminalActivity.getString(R.string.downloader_unsupported_cpu))

            val distributionUrl = selectedDistributionUrl ?: throw RuntimeException(
                terminalActivity.getString(R.string.downloader_setup_failed)
            )

            val distributionOutputName = getDistributionOutputName(distributionUrl)

            // Always download into the app-private copy first; if the user picks the
            // shizuku/root permission the files are later elevated-copied to /data/local/tmp/null-box.
            val filesToDownload = listOf(
                "libtalloc.so.2" to abiMap[abi]!!.talloc,
                "busybox" to abiMap[abi]!!.busybox,
                "proot" to abiMap[abi]!!.proot,
                distributionOutputName to distributionUrl
            ).map { (name, url) ->
                val targetFile = when (name) {
                    "libtalloc.so.2" -> appLocalLibDir().child(name)
                    "busybox", "proot" -> appLocalBinDir().child(name)
                    else -> appLocalDir().child(name)
                }
                DownloadFile(url, targetFile)
            }

            needsDownload = filesToDownload.any { !it.outputFile.exists() }

            if (needsDownload) {
                setupEnvironment(
                    terminalActivity,
                    filesToDownload,
                    onProgress = { fileName, fileBytes, filePerc, overallText ->
                        currentFileName = extractFileNameFromUrl(fileName)
                        fileProgressText = fileBytes
                        progress = filePerc
                        overallProgressText = overallText
                    },
                    onComplete = {
                        Rootfs.needsDistributionInit.value = true
                        isSetupComplete = true
                        showPermissionChooser = true
                    },
                    onError = { error ->
                        if (error is CancelledDownloadException) {
                            needsDownload = true
                        } else {
                            toast(
                                if (error is UnknownHostException) terminalActivity.getString(R.string.network_error)
                                else terminalActivity.getString(R.string.downloader_setup_failed)
                            )
                            hasError = true
                        }
                    },
                    isCancelled = { cancelRequested }
                )
            } else {
                Rootfs.needsDistributionInit.value = true
                isSetupComplete = true
                showPermissionChooser = true
            }
        } catch (e: Exception) {
            toast(
                if (e is UnknownHostException) terminalActivity.getString(R.string.network_error)
                else terminalActivity.getString(R.string.downloader_setup_failed)
            )
            hasError = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!isSetupComplete) {
                if (isResolving || selectedDistributionUrl == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.downloader_verifying_files),
                            style = MaterialTheme.typography.bodyLarge,
                            color = onBackgroundColor
                        )
                    }
                } else if (hasError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.downloader_download_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = onBackgroundColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { retryTrigger++ }) {
                            Text(
                                stringResource(R.string.button_retry),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = {
                            selectedDistributionUrl = null
                            needsDownload = false
                            hasError = false
                        }) {
                            Text(
                                stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (needsDownload) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.downloader_one_time_operation_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBackgroundColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(overallProgressText, style = MaterialTheme.typography.bodyLarge, color = onBackgroundColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$currentFileName $fileProgressText", style = MaterialTheme.typography.bodyMedium, color = onBackgroundColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            cancelRequested = true
                            selectedDistributionUrl = null
                            needsDownload = false
                            hasError = false
                        }) {
                            Text(
                                stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.downloader_verifying_files),
                            style = MaterialTheme.typography.bodyLarge,
                            color = onBackgroundColor
                        )
                    }
                }
            }
        }
    }

    // Distribution permission chooser — appears after the download completes.
    if (showPermissionChooser) {
        BasicAlertDialog(onDismissRequest = { }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp
            ) {
                Column {
                    Text(
                        stringResource(R.string.distribution_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SettingsCard(
                        title = { Text(stringResource(R.string.distribution_permission_default_root)) },
                        description = { Text(stringResource(R.string.distribution_permission_default_root_desc)) },
                        onClick = {
                            finishSetup(DistroPermission.DEFAULT_ROOT)
                        }
                    )
                    SettingsCard(
                        title = { Text(stringResource(R.string.distribution_permission_shizuku_root)) },
                        description = { Text(stringResource(R.string.distribution_permission_shizuku_root_desc)) },
                        onClick = {
                            showPermissionChooser = false
                            showShizukuWarning = true
                        }
                    )
                }
            }
        }
    }

    // Warning shown before committing to the shizuku/root permission.
    if (showShizukuWarning) {
        BasicAlertDialog(onDismissRequest = {
            showShizukuWarning = false
            showPermissionChooser = true
        }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp
            ) {
                Column {
                    Text(
                        stringResource(R.string.warning),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        stringResource(R.string.distribution_permission_warning_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showShizukuWarning = false
                            showPermissionChooser = true
                        }) {
                            Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(onClick = {
                            showShizukuWarning = false
                            // Request Shizuku permission before copying
                            PrivilegedAccessManager.ensureShizukuPermission(terminalActivity) { granted ->
                                if (granted) {
                                    isCopying = true
                                    scope.launch {
                                        Log.i("Downloader", "Starting distribution copy to privileged path")
                                        val ok = Rootfs.ensureDistributionAtPrivilegedPath(terminalActivity)
                                        Log.i("Downloader", "Distribution copy result: $ok")
                                        if (ok) {
                                            finishSetup(DistroPermission.SHIZUKU_ROOT)
                                        } else {
                                            Log.e("Downloader", "Distribution copy failed, hasShizuku=${PrivilegedAccessManager.hasShizukuPermission()}, hasRoot=${PrivilegedAccessManager.hasRootPermission()}")
                                            toast(terminalActivity.getString(R.string.distribution_permission_copy_failed))
                                            showPermissionChooser = true
                                        }
                                        isCopying = false
                                    }
                                } else {
                                    Log.w("Downloader", "Shizuku permission denied by user")
                                    toast(terminalActivity.getString(R.string.shizuku_permission_denied))
                                    showPermissionChooser = true
                                }
                            }
                        }) {
                            Text(
                                stringResource(R.string.ok),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Copy progress dialog.
    if (isCopying) {
        BasicAlertDialog(onDismissRequest = { }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.downloader_progress_installing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
