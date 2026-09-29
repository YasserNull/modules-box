package com.yassernull.modulesbox.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.data.model.RemoteModule
import com.yassernull.modulesbox.ui.viewmodels.DownloadState
import java.io.File

// عنصر يمثل وحدة (Module) واحدة متاحة للتنزيل في المستودع.
@Composable
fun RepoItem(
    module: RemoteModule,
    downloadState: DownloadState,
    onButtonClick: () -> Unit,
    onModuleClick: () -> Unit
) {
    val repositoryUrlNotFoundString = stringResource(R.string.repository_url_not_found)
    val context = LocalContext.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RepoIcon(
                    module = module,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RectangleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable {
                            val url = module.repository
                            if (url.isBlank()) {
                                Toast.makeText(context, repositoryUrlNotFoundString, Toast.LENGTH_SHORT).show()
                            } else if (url.startsWith("http", ignoreCase = true)) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_opening_link, url), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.invalid_repository_url), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = module.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (module.description.isNotBlank()) {
                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // The index carries no download count, so the row shows the version the
                    // download button will actually fetch.
                    Text(
                        text = module.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                when (downloadState) {
                    DownloadState.DOWNLOADING -> {
                        SizedCircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            size = 24.dp
                        )
                    }
                    DownloadState.FAILED -> {
                        IconButton(
                            onClick = onButtonClick,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.retry),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = onButtonClick,
                            modifier = Modifier.padding(end = 8.dp),
                            enabled = downloadState != DownloadState.DOWNLOADING
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.download),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoIcon(
    module: RemoteModule,
    modifier: Modifier = Modifier
) {
    // iconPath is null until the icon finishes downloading (or when the index entry has no
    // icon), so the letter avatar is the placeholder rather than a broken image.
    val iconFile = module.iconPath?.takeIf { it.isNotBlank() }?.let(::File)

    if (iconFile != null && iconFile.exists() && iconFile.length() > 0L) {
        Image(
            painter = rememberAsyncImagePainter(model = iconFile),
            contentDescription = module.name,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
        return
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
fun SizedCircularProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = 2.5.dp
    )
}
