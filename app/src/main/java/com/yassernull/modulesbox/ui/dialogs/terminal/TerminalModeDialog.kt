package com.yassernull.modulesbox.ui.dialogs.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.core.preferences.terminal.isDistroShizukuRoot
import com.yassernull.modulesbox.ui.activities.terminal.WorkingMode
import com.yassernull.modulesbox.ui.components.SettingsCard
import com.yassernull.modulesbox.utils.PrivilegedAccessManager

/**
 * Dialog to pick a terminal session mode. Ported from null-code-ide with chroot and
 * distribution-shizuku removed — Alpine Linux is proot only. The Alpine sub-dialog
 * offers [Default, Root] with the default/root distribution permission and
 * [Shizuku, Root] with the shizuku/root permission.
 */
@Composable
fun TerminalModeDialog(
    onSelectMode: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var showDistributionAccessDialog by remember { mutableStateOf(false) }
    var showAndroidAccessDialog by remember { mutableStateOf(false) }
    val hasShizukuPermission = PrivilegedAccessManager.hasShizukuPermission()
    val hasRootPermission = PrivilegedAccessManager.hasRootPermission()
    val context = LocalContext.current
    val isDistroShizukuRoot = remember(context) { AppPreferences(context).isDistroShizukuRoot() }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 6.dp
        ) {
            Column {
                SettingsCard(
                    title = { Text(stringResource(R.string.distribution)) },
                    description = { Text(stringResource(R.string.distribution_linux)) },
                    startWidget = {
                        AsyncImage(
                            model = "file:///android_asset/icons/alpine.png",
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(24.dp)
                        )
                    },
                    onClick = {
                        showDistributionAccessDialog = true
                    }
                )
                SettingsCard(
                    title = { Text(stringResource(R.string.android)) },
                    description = { Text(stringResource(R.string.android_shell)) },
                    startWidget = {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = Color(0xFF00FF00),
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                        )
                    },
                    onClick = {
                        showAndroidAccessDialog = true
                    }
                )
            }
        }
    }

    if (showDistributionAccessDialog) {
        BasicAlertDialog(onDismissRequest = { showDistributionAccessDialog = false }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp
            ) {
                Column {
                    if (isDistroShizukuRoot) {
                        // shizuku/root permission → only Shizuku and Root access.
                        SettingsCard(
                            title = { Text(stringResource(R.string.distribution_shizuku_access)) },
                            description = { Text(stringResource(R.string.distribution_shizuku_access_desc)) },
                            isEnabled = hasShizukuPermission,
                            onClick = {
                                onSelectMode(WorkingMode.DISTRIBUTION_SHIZUKU)
                                showDistributionAccessDialog = false
                                onDismiss()
                            }
                        )
                    } else {
                        // default/root permission → only Default and Root access.
                        SettingsCard(
                            title = { Text(stringResource(R.string.distribution_default_access)) },
                            description = { Text(stringResource(R.string.distribution_default_access_desc)) },
                            onClick = {
                                onSelectMode(WorkingMode.DISTRIBUTION)
                                showDistributionAccessDialog = false
                                onDismiss()
                            }
                        )
                    }
                    SettingsCard(
                        title = { Text(stringResource(R.string.distribution_root_access)) },
                        description = { Text(stringResource(R.string.distribution_root_access_desc)) },
                        isEnabled = hasRootPermission,
                        onClick = {
                            onSelectMode(WorkingMode.DISTRIBUTION_ROOT)
                            showDistributionAccessDialog = false
                            onDismiss()
                        }
                    )
                }
            }
        }
    }

    if (showAndroidAccessDialog) {
        BasicAlertDialog(onDismissRequest = { showAndroidAccessDialog = false }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp
            ) {
                Column {
                    SettingsCard(
                        title = { Text(stringResource(R.string.android_default_access)) },
                        description = { Text(stringResource(R.string.android_default_access_desc)) },
                        onClick = {
                            onSelectMode(WorkingMode.ANDROID)
                            showAndroidAccessDialog = false
                            onDismiss()
                        }
                    )
                    SettingsCard(
                        title = { Text(stringResource(R.string.android_shizuku_access)) },
                        description = { Text(stringResource(R.string.android_shizuku_access_desc)) },
                        isEnabled = hasShizukuPermission,
                        onClick = {
                            onSelectMode(WorkingMode.SHIZUKU)
                            showAndroidAccessDialog = false
                            onDismiss()
                        }
                    )
                    SettingsCard(
                        title = { Text(stringResource(R.string.android_root_access)) },
                        description = { Text(stringResource(R.string.android_root_access_desc)) },
                        isEnabled = hasRootPermission,
                        onClick = {
                            onSelectMode(WorkingMode.ROOT)
                            showAndroidAccessDialog = false
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
