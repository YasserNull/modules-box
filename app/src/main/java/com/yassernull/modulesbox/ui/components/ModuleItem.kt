package com.yassernull.modulesbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.data.model.Module
import java.io.File

@Composable
fun ModuleItem(
    module: Module,
    isRunning: Boolean,
    isStarting: Boolean,
    port: Int?,
    onModuleClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLogClick: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Clicking the module opens the site in the in-app WebView.
        // Node modules have no html file — they serve from the server root (/).
        val siteUrl = if (isRunning && port != null) {
            val page = module.html?.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "/"
            "http://localhost:$port$page"
        } else {
            null
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .clickable { onModuleClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModuleIcon(
                    module = module,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RectangleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        module.permission?.let { permission ->
                            if (permission != "default") {
                                Spacer(modifier = Modifier.width(8.dp))
                                PermissionBadge(permission = permission)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = module.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    module.description?.let {
                        if (it.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isRunning && port != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.module_port, port),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (siteUrl != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = siteUrl,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = TextDecoration.Underline
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onOpenInBrowserClick() }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (isRunning) onStopClick() else onStartClick()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = stringResource(if (isRunning) R.string.stop else R.string.start),
                                tint = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isRunning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        IconButton(
                            onClick = onLogClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = stringResource(R.string.module_log),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isRunning) {
                        Spacer(modifier = Modifier.height(4.dp))

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleIcon(
    module: Module,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconPath = module.icon

    if (!iconPath.isNullOrBlank()) {
        val iconFile = File(module.path, iconPath)
        if (iconFile.exists()) {
            Image(
                painter = rememberAsyncImagePainter(model = iconFile),
                contentDescription = module.name,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RectangleShape
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

@Composable
private fun PermissionBadge(permission: String) {
    val (text, color) = when (permission) {
        "shizuku" -> "Shizuku & Root" to MaterialTheme.colorScheme.tertiary
        "root" -> "Root" to MaterialTheme.colorScheme.error
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
