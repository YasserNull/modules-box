package com.yassernull.modulesbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.termux.terminal.TerminalSession
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.ui.activities.terminal.isDistributionMode

// درج الجلسات للطرفية: يعرض الجلسات مع خيارات الإضافة والحذف والأزرار الإضافية.
@Composable
fun TerminalDrawer(
    sessions: List<TerminalSession>,
    sessionIds: List<String>,
    sessionDisplayNames: Map<String, String>,
    sessionModes: Map<String, Int>,
    currentSessionIndex: Int,
    keepScreenOn: Boolean,
    onSelectSession: (Int) -> Unit,
    onAddSession: () -> Unit,
    onDeleteSession: (Int) -> Unit,
    onKillProcess: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onReset: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(230.dp)
            .fillMaxSize(),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.sessions), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onAddSession) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_session)
                    )
                }
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(sessions) { index, session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (index == currentSessionIndex) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onSelectSession(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sessionId = sessionIds.getOrNull(index)
                        if (isDistributionMode(sessionId?.let { sessionModes[it] })) {
                            AsyncImage(
                                model = "file:///android_asset/icons/alpine.png",
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color(0xFF00FF00),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        val explicitName = sessionId?.let { sessionDisplayNames[it] }
                        val defaultLabel = stringResource(R.string.session_label, index + 1)
                        val sessionTitle = explicitName ?: session.title
                        Text(
                            text = if (sessionTitle.isNullOrBlank()) defaultLabel else sessionTitle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        IconButton(onClick = { onDeleteSession(index) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                DrawerActionRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    text = stringResource(R.string.kill_session),
                    onClick = onKillProcess
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleKeepScreenOn() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.keep_screen_on), modifier = Modifier.weight(1f))
                    Checkbox(checked = keepScreenOn, onCheckedChange = { onToggleKeepScreenOn() })
                }
                DrawerActionRow(
                    icon = { Icon(imageVector = Icons.Default.Refresh, contentDescription = null) },
                    text = stringResource(R.string.reset),
                    onClick = onReset
                )
            }
        }
    }
}

@Composable
private fun DrawerActionRow(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Text(text)
    }
}
