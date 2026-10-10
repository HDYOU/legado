package io.legado.app.ui.widget.components.dialog

import androidx.compose.ui.Modifier
import io.legado.app.lib.theme.eInkGrayscale

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import io.legado.app.R
// 确认弹窗
@Composable
fun AppConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = stringResource(R.string.cancel),
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // 弹层是独立窗口，页面那层灰阶罩不到，这里再贴一次
        modifier = Modifier.eInkGrayscale(),
        containerColor = containerColor,
        shape = RectangleShape,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(dismissText)
            }
        },
    )
}
