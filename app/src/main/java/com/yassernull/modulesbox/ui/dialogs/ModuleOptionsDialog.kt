package com.yassernull.modulesbox.ui.dialogs

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppBrowser
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.receivers.ModuleWidgetProvider
import com.yassernull.modulesbox.receivers.WidgetPinReceiver
import kotlinx.coroutines.runBlocking

@Composable
fun ModuleOptionsDialog(
    module: Module,
    onDismissRequest: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
    onOpenInAppClick: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = AppPreferences(context)
    val defaultBrowser = runBlocking { preferences.getDefaultBrowserSync() }
    var showPinFallback by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.module_options),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(module.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(module.author) },
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ModuleIcon(module = module, modifier = Modifier.size(48.dp))
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    if (defaultBrowser == AppBrowser.MODULES_BOX) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.open_in_app)) },
                            leadingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onOpenInAppClick()
                                onDismissRequest()
                            }
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.open_in_browser)) },
                            leadingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onOpenInBrowserClick()
                                onDismissRequest()
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.open_in_browser)) },
                            leadingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onOpenInBrowserClick()
                                onDismissRequest()
                            }
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.open_in_app)) },
                            leadingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onOpenInAppClick()
                                onDismissRequest()
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.add_to_widget)) },
                        leadingContent = { Icon(Icons.Default.Widgets, contentDescription = null) },
                        modifier = Modifier.clickable {
                            Log.i(TAG, "add_to_widget item clicked")
                            val pinned = requestPinWidget(context, module)
                            onDismissRequest()
                            if (!pinned) {
                                Log.i(TAG, "pin not possible -> showing manual-add fallback dialog")
                                showPinFallback = true
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = stringResource(R.string.module_info),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    InfoRow(stringResource(R.string.id), module.id)
                    InfoRow(stringResource(R.string.name), module.name)
                    InfoRow(stringResource(R.string.author), module.author)
                    InfoRow(stringResource(R.string.version), module.version)
                    InfoRow(stringResource(R.string.path), module.path)
                    InfoRow(stringResource(R.string.html), module.html ?: "-")
                    InfoRow(stringResource(R.string.icon), module.icon ?: "-")
                    if (!module.description.isNullOrBlank()) {
                        InfoRow(stringResource(R.string.description), module.description!!)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            onDeleteClick()
                            onDismissRequest()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.close))
                    }
                }
            }
        }
    }

    if (showPinFallback) {
        AlertDialog(
            onDismissRequest = { showPinFallback = false },
            title = { Text(stringResource(R.string.add_widget_unsupported_title)) },
            text = { Text(stringResource(R.string.add_widget_unsupported_message)) },
            confirmButton = {
                TextButton(onClick = { showPinFallback = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

private const val TAG = "AddToWidget"

/**
 * Ask the launcher to pin a widget for this module on the home screen.
 *
 * Returns false when the device cannot pin widgets directly (pre-Android 8, or a
 * launcher that does not report APPWIDGET_PINNED support), so the caller can fall
 * back to telling the user how to add it manually.
 *
 * The chosen module is written to [ModuleWidgetProvider.PENDING_ID] first: some
 * launchers pin the widget without ever launching the configure activity, which
 * would otherwise leave the widget permanently blank.
 */
private fun requestPinWidget(context: Context, module: Module): Boolean {
    Log.i(TAG, "add_to_widget pressed: module id=${module.id} name=${module.name}")
    Log.i(TAG, "sdk=${Build.VERSION.SDK_INT}, required>=${Build.VERSION_CODES.O}")

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Log.w(TAG, "blocked: Android version too old for requestPinAppWidget")
        return false
    }

    val manager = AppWidgetManager.getInstance(context)
    val supported = manager.isRequestPinAppWidgetSupported
    Log.i(TAG, "isRequestPinAppWidgetSupported=$supported")
    if (!supported) {
        Log.w(TAG, "blocked: launcher does not support pinning widgets")
        return false
    }

    context.getSharedPreferences(ModuleWidgetProvider.WIDGET_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(ModuleWidgetProvider.PENDING_ID, module.id)
        .putString(ModuleWidgetProvider.PENDING_NAME, module.name)
        .putString(ModuleWidgetProvider.PENDING_ICON, module.icon)
        .putString(ModuleWidgetProvider.PENDING_PATH, module.path)
        .putBoolean(ModuleWidgetProvider.PENDING_SHOW_NAME, true)
        .apply()
    Log.i(TAG, "pending widget config written for module=${module.id}")

    // Use the class rather than a hardcoded string so a future package rename
    // cannot silently break the pin request.
    val provider = ComponentName(context, ModuleWidgetProvider::class.java)
    Log.i(TAG, "widget ids already bound to provider: ${manager.getAppWidgetIds(provider).toList()}")

    // The launcher fires this only once the widget is really placed, which is the
    // only way to distinguish a real pin from a silently dropped request.
    val callback = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetPinReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    Log.i(TAG, "calling requestPinAppWidget provider=${provider.flattenToString()}")
    val accepted = manager.requestPinAppWidget(provider, null, callback)
    Log.i(TAG, "requestPinAppWidget returned accepted=$accepted")
    return true
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModuleIcon(
    module: Module,
    modifier: Modifier = Modifier
) {
    val iconPath = module.icon
    if (!iconPath.isNullOrBlank()) {
        val iconFile = java.io.File(module.path, iconPath)
        if (iconFile.exists()) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(model = iconFile),
                contentDescription = module.name,
                modifier = modifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            return
        }
    }
    Box(
        modifier = modifier
            .then(
                Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.shapes.medium
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = module.name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
