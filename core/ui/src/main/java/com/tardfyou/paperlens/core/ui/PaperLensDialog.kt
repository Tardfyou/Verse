package com.tardfyou.paperlens.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun PLConfirmDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            PLPrimaryButton(text = "确认", onClick = onConfirm)
        },
        dismissButton = {
            PLSecondaryButton(text = "取消", onClick = onDismiss)
        },
    )
}
