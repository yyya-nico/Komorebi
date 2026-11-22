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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import com.beeregg2001.komorebi.data.SetupConnection
import com.beeregg2001.komorebi.data.model.StreamSource
import com.beeregg2001.komorebi.ui.theme.KomorebiTheme
import kotlinx.coroutines.flow.first

@Composable
internal fun ConnectionAddressFields(
    host: String,
    manual: Boolean,
    focusRequester: FocusRequester = remember { FocusRequester() },
    startEditing: Boolean = false,
    allowUrl: Boolean = true,
    onChange: (host: String, manual: Boolean) -> Unit
) {
    var alternateHost by remember { mutableStateOf("") }
    var hasSwitchedMode by remember { mutableStateOf(false) }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(manual, windowInfo) {
        if (hasSwitchedMode) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            focusRequester.safeRequestFocusWithRetry("ConnectionAddress")
        }
    }

    val manualLabel = if (allowUrl) "ホスト名またはURL" else "ホスト名"
    // 入力モードごとにテキスト欄を作り直し、IMEの入力セッションを切り替える。
    key(manual) {
        SetupField(
            label = if (manual) manualLabel else "IPアドレス",
            value = host,
            onChange = { onChange(it, manual) },
            placeholder = when {
                !manual -> "192.168.1.10"
                allowUrl -> "tv.example.com"
                else -> "nas.local"
            },
            focusRequester = focusRequester,
            keyboardType = if (manual) KeyboardType.Text else KeyboardType.Decimal,
            startEditing = hasSwitchedMode || (startEditing && !manual)
        )
    }
    MultiSelectionDialogItem(
        label = if (allowUrl) "ホスト名・URLで入力" else "ホスト名で入力",
        isSelected = manual,
        onClick = {
            val nextHost = alternateHost
            alternateHost = host
            hasSwitchedMode = true
            onChange(nextHost, !manual)
        },
        modifier = Modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(manual)
        }
    )
}

@Composable
internal fun ServerConnectionFields(
    connection: SetupConnection,
    error: String?,
    showHttpPort: Boolean = true,
    startEditing: Boolean = true,
    onChange: (SetupConnection) -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        withFrameNanos { }
        initialFocus.safeRequestFocusWithRetry("ServerConnection")
    }

    ConnectionAddressFields(
        host = connection.host,
        manual = connection.manualAddress,
        focusRequester = initialFocus,
        startEditing = startEditing
    ) { host, manual ->
        onChange(connection.copy(host = host, manualAddress = manual))
    }
    SetupField(
        label = if (connection.source == StreamSource.EDCB) "TCPポート" else "ポート番号",
        value = connection.port,
        onChange = { onChange(connection.copy(port = it)) },
        keyboardType = KeyboardType.Number
    )
    connection.httpPort?.takeIf { showHttpPort }?.let { httpPort ->
        SetupField(
            label = "HTTP/HTTPSポート",
            value = httpPort,
            onChange = { onChange(connection.copy(httpPort = it)) },
            keyboardType = KeyboardType.Number
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
internal fun ConnectionEditorDialog(
    initial: SetupConnection,
    onDismiss: () -> Unit,
    onConfirm: (SetupConnection) -> Unit
) {
    var connection by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = KomorebiTheme.colors.surface)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(connection.title, style = MaterialTheme.typography.headlineSmall)
                ServerConnectionFields(connection, error) {
                    connection = it
                    error = null
                }
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
    }
}
