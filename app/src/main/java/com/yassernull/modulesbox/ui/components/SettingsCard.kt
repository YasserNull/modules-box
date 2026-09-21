package com.yassernull.modulesbox.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ListItem
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    title: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier
            .then(if (isEnabled) Modifier else Modifier.alpha(0.5f))
            .combinedClickable(
                enabled = isEnabled,
                role = Role.Button,
                indication = ripple(),
                interactionSource = interactionSource,
                onClick = onClick
            ),
        headlineContent = title,
        supportingContent = description,
        leadingContent = startWidget,
        trailingContent = endWidget
    )
}
