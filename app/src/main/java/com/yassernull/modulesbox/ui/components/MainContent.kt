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
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.ui.activities.ModuleWebViewActivity
import com.yassernull.modulesbox.ui.dialogs.ModuleLogDialog
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
            val context = LocalContext.current

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
                            openModule(context, module, port)
                        },
                        onOpenInBrowserClick = {
                            openInBrowser(context, module, port)
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
}

private fun openModule(
    context: android.content.Context,
    module: Module,
    currentPort: Int?
) {
    // Node modules have no html file — they serve from the server root (/).
    val url = moduleUrl(module, currentPort)
    if (url != null) {
        ModuleWebViewActivity.launch(context, module.path, url, module.name)
    } else {
        Toast.makeText(context, "الوحدة غير قيد التشغيل", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun openInBrowser(
    context: android.content.Context,
    module: Module,
    currentPort: Int?
) {
    val url = moduleUrl(module, currentPort)
    if (url != null) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "الوحدة غير قيد التشغيل", android.widget.Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "الوحدة غير قيد التشغيل", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Public URL of a running module, or null when it is not running. */
private fun moduleUrl(module: Module, currentPort: Int?): String? {
    val port = currentPort ?: return null
    val page = module.html?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "/"
    return "http://localhost:$port$page"
}
