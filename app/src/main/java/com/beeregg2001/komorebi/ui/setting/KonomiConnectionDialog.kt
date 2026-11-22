package com.beeregg2001.komorebi.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.beeregg2001.komorebi.common.safeRequestFocusWithRetry
import com.beeregg2001.komorebi.data.KonomiConnection
import com.beeregg2001.komorebi.ui.theme.KomorebiTheme

@Composable
internal fun KonomiConnectionFields(
    connection: KonomiConnection,
    initialFocus: FocusRequester,
    onChange: (KonomiConnection) -> Unit
) {
    var alternateHost by remember { mutableStateOf("") }
    SetupField(
        label = if (connection.manual) "FQDN・URL" else "IPアドレス",
        value = connection.host,
        onChange = { onChange(connection.copy(host = it)) },
        placeholder = if (connection.manual) "tv.example.com" else "192.168.1.10",
        focusRequester = initialFocus,
        keyboardType = if (connection.manual) KeyboardType.Uri else KeyboardType.Decimal
    )
    SetupField(
        label = "ポート番号",
        value = connection.port,
        onChange = { onChange(connection.copy(port = it)) },
        keyboardType = KeyboardType.Number
    )
    OutlinedButton(onClick = {
        val nextHost = alternateHost
        alternateHost = connection.host
        onChange(connection.copy(host = nextHost, manual = !connection.manual))
    }) {
        Text(if (connection.manual) "IPアドレスで設定する" else "手動設定")
    }
}

@Composable
internal fun KonomiConnectionDialog(
    initial: KonomiConnection,
    onDismiss: () -> Unit,
    onConfirm: (KonomiConnection) -> Unit
) {
    var connection by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val initialFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = KomorebiTheme.colors.surface)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("KonomiTV の接続先", style = MaterialTheme.typography.headlineSmall)
                KonomiConnectionFields(connection, initialFocus) {
                    connection = it
                    error = null
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("キャンセル") }
                    Button(onClick = {
                        val message = connection.validationError()
                        error = message
                        if (message == null) onConfirm(connection)
                    }) { Text("保存") }
                }
            }
        }
        LaunchedEffect(Unit) { initialFocus.safeRequestFocusWithRetry("KonomiConnectionDialog") }
    }
}
