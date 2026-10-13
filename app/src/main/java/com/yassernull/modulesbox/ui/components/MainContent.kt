package com.yassernull.modulesbox.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppBrowser
import com.yassernull.modulesbox.core.AppPreferences
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.ui.activities.ModuleWebViewActivity
import com.yassernull.modulesbox.ui.dialogs.ModuleLogDialog
import com.yassernull.modulesbox.ui.dialogs.ModuleOptionsDialog
import com.yassernull.modulesbox.ui.viewmodels.ModuleViewModel
import com.yassernull.modulesbox.utils.ModuleInstaller
import com.yassernull.modulesbox.utils.PortManager
import java.io.File

@Composable
fun MainContent(
    viewModel: ModuleViewModel,
    searchQuery: String
) {
    val modules by viewModel.modules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val runningModules by viewModel.runningModules.collectAsState()
    val startingModules by viewModel.startingModules.collectAsState()

    var showLogDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedModule by remember { mutableStateOf<Module?>(null) }
    var selectedModuleLogs by remember { mutableStateOf("") }

    val filteredModules = if (searchQuery.isBlank()) {
        modules
    } else {
        modules.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.author.contains(searchQuery, ignoreCase = true) ||
            it.description?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    val context = LocalContext.current
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = { viewModel.refreshModules() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && filteredModules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredModules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val message = if (modules.isEmpty()) {
                    stringResource(id = R.string.no_modules_found)
                } else {
                    stringResource(id = R.string.no_search_results)
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            val preferences = remember { AppPreferences(context) }
            val defaultBrowser by preferences.getDefaultBrowser().collectAsState(initial = AppBrowser.MODULES_BOX)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items = filteredModules, key = { it.id }) { module ->
                    val runningState = runningModules[module.id]
                    val isRunning = runningState != null
                    val isStarting = startingModules.contains(module.id)
                    val port = runningState?.port

                    ModuleItem(
                        module = module,
                        isRunning = isRunning,
                        isStarting = isStarting,
                        port = port,
                        onModuleClick = {
                            openModule(context, viewModel, module, port) { url ->
                                openResolvedUrl(context, module, url, defaultBrowser)
                            }
                        },
                        onOpenInBrowserClick = {
                            openModule(context, viewModel, module, port) { url ->
                                openInBrowser(context, url)
                            }
                        },
                        onStartClick = {
                            viewModel.startModule(context, module)
                        },
                        onStopClick = {
                            viewModel.stopModule(context, module)
                        },
                        onDeleteClick = {
                            viewModel.uninstallModule(module)
                        },
                        onLogClick = {
                            selectedModuleLogs = viewModel.getModuleLogs(module.id)
                            showLogDialog = true
                        },
                        onLongClick = {
                            selectedModule = module
                            showOptionsDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showLogDialog) {
        ModuleLogDialog(
            logText = selectedModuleLogs,
            onDismissRequest = { showLogDialog = false }
        )
    }

    if (showOptionsDialog && selectedModule != null) {
        val optionsModule = selectedModule!!
        val port = runningModules[optionsModule.id]?.port
        ModuleOptionsDialog(
            module = optionsModule,
            onDismissRequest = { showOptionsDialog = false },
            onDeleteClick = {
                viewModel.uninstallModule(optionsModule)
            },
            onOpenInBrowserClick = {
                openModule(context, viewModel, optionsModule, port) { url ->
                    openInBrowser(context, url)
                }
            },
            onOpenInAppClick = {
                openModule(context, viewModel, optionsModule, port) { url ->
                    ModuleWebViewActivity.launch(context, optionsModule, url)
                }
            }
        )
    }
}

/**
 * Opens the module, starting its server first when it is not already running.
 *
 * @param openWith decides how to open the resolved URL once the module is up, so the
 * same start-then-open flow serves the card tap, "open in app" and "open in browser".
 */
private fun openModule(
    context: android.content.Context,
    viewModel: ModuleViewModel,
    module: Module,
    currentPort: Int?,
    openWith: (url: String) -> Unit
) {
    // Node modules have no html file — they serve from the server root (/).
    val url = moduleUrl(module, currentPort)
    if (url != null) {
        openWith(url)
        return
    }

    // Not running yet: start the server and open as soon as it reports its port.
    // The card already shows a spinner for this module while it is in flight.
    viewModel.startModule(context, module, onStarted = { port ->
        val startedUrl = moduleUrl(module, port) ?: return@startModule
        openWith(startedUrl)
    })
}

/** Opens [url] in the app WebView, or in the external browser when that is the default. */
private fun openResolvedUrl(
    context: android.content.Context,
    module: Module,
    url: String,
    defaultBrowser: AppBrowser
) {
    if (defaultBrowser == AppBrowser.DEFAULT_BROWSER) {
        openInBrowser(context, url)
    } else {
        ModuleWebViewActivity.launch(context, module, url)
    }
}

/** Opens [url] in the device's default browser. */
private fun openInBrowser(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        )
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.error_opening_link, url),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** Public URL of a running module, or null when it is not running. */
private fun moduleUrl(module: Module, currentPort: Int?): String? {
    val port = currentPort ?: return null
    val page = module.html?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "/"
    return "http://localhost:$port$page"
}
