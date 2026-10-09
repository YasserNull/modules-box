package com.yassernull.modulesbox.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.core.AppBrowser

// حوار لاختيار المتصفح الافتراضي لتشغيل الوحدات.
@Composable
fun DefaultBrowserDialog(
    currentBrowser: AppBrowser,
    onDismissRequest: () -> Unit,
    onBrowserSelected: (AppBrowser) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_default_browser),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                ) {
                    AppBrowser.values().forEach { browser ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = when (browser) {
                                        AppBrowser.MODULES_BOX -> stringResource(R.string.browser_modules_box)
                                        AppBrowser.DEFAULT_BROWSER -> stringResource(R.string.browser_default)
                                    }
                                )
                            },
                            modifier = Modifier.selectable(
                                selected = (browser == currentBrowser),
                                onClick = {
                                    onBrowserSelected(browser)
                                    onDismissRequest()
                                },
                                role = Role.RadioButton
                            ),
                            leadingContent = {
                                RadioButton(selected = (browser == currentBrowser), onClick = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
}
