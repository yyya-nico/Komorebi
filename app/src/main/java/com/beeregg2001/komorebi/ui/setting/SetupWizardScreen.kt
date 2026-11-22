package com.beeregg2001.komorebi.ui.setting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.beeregg2001.komorebi.common.AppStrings
import com.beeregg2001.komorebi.common.safeRequestFocusWithRetry
import com.beeregg2001.komorebi.data.model.StreamSource
import com.beeregg2001.komorebi.ui.theme.KomorebiTheme
import com.beeregg2001.komorebi.viewmodel.SetupPage
import com.beeregg2001.komorebi.viewmodel.SetupViewModel

@Composable
internal fun SetupWizardScreen(viewModel: SetupViewModel, onExit: () -> Unit) {
    val page = viewModel.page
    val colors = KomorebiTheme.colors
    val connection = when (page) {
        SetupPage.MAIN_CONNECTION -> viewModel.connection(viewModel.mainSystem)
        SetupPage.SOURCE_CONNECTION -> viewModel.alternateSource?.let(viewModel::connection)
        else -> null
    }
    val skipCloudflare = page == SetupPage.CLOUDFLARE &&
        viewModel.clientId.isBlank() && viewModel.clientSecret.isBlank()
    BackHandler {
        if (page == SetupPage.WELCOME) onExit() else viewModel.back()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
            .then(if (connection != null) Modifier.imePadding() else Modifier)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        key(page) {
            val initialFocus = remember { FocusRequester() }
            val reusesMainConnection = page == SetupPage.SOURCE_CONNECTION && viewModel.alternateSource == null
            val focusNext = page == SetupPage.WELCOME || reusesMainConnection
            if (connection == null) {
                LaunchedEffect(viewModel.isSaving) {
                    if (!viewModel.isSaving) initialFocus.safeRequestFocusWithRetry("SetupWizard")
                }
            }

            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "${page.ordinal + 1} / ${SetupPage.entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Text(
                    when {
                        connection != null -> connection.title
                        reusesMainConnection -> "ライブ視聴の接続先"
                        else -> page.title
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary
                )

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (page) {
                        SetupPage.WELCOME -> SetupInstructions(
                            "テレビ番組や録画を視聴するために、利用するシステムと接続先を設定しましょう。"
                        )
                        SetupPage.SYSTEM -> SetupChoices(
                            options = listOf(
                                StreamSource.KONOMITV to "KonomiTV",
                                StreamSource.EDCB to "EDCB (EpgTimerSrv)",
                                StreamSource.EPGSTATION to "EPGStation"
                            ),
                            selected = viewModel.mainSystem,
                            initialFocus = initialFocus,
                            onSelect = { viewModel.mainSystem = it }
                        )
                        SetupPage.MAIN_CONNECTION, SetupPage.SOURCE_CONNECTION -> {
                            if (connection == null) {
                                SetupInstructions("ライブ視聴には、メインシステムで設定した接続先を使用します。")
                            } else {
                                ServerConnectionFields(
                                    connection, viewModel.error,
                                    showHttpPort = page == SetupPage.MAIN_CONNECTION,
                                    startEditing = connection.host.isBlank(),
                                    onChange = viewModel::updateConnection
                                )
                            }
                        }
                        SetupPage.SOURCE -> SetupChoices(
                            options = listOf(
                                StreamSource.KONOMITV to "メインシステムに従う（トランスコード）",
                                StreamSource.MIRAKURUN to "Mirakurun を優先",
                                StreamSource.EDCB to if (viewModel.mainSystem == StreamSource.EDCB) {
                                    "EDCB（ダイレクトストリーミング）"
                                } else {
                                    "EDCB (TCP) を優先"
                                }
                            ),
                            selected = viewModel.preferredSource,
                            initialFocus = initialFocus,
                            onSelect = { viewModel.preferredSource = it }
                        )
                        SetupPage.FILE_LIBRARY -> {
                            SetupInstructions("NASなどのSMBサーバーにある動画をファイルライブラリに追加できます。")
                            val smb = viewModel.smb
                            SetupField("表示名", smb.name, { viewModel.smb = smb.copy(name = it) },
                                placeholder = "リビングのNAS", focusRequester = initialFocus)
                            ConnectionAddressFields(
                                host = smb.ip,
                                manual = viewModel.smbManualAddress,
                                allowUrl = false,
                                onChange = viewModel::updateSmbAddress
                            )
                            SetupField("ポート番号", smb.port, { viewModel.smb = smb.copy(port = it) },
                                placeholder = "445", keyboardType = KeyboardType.Number)
                            SetupField("ユーザー名（空欄でゲスト接続）", smb.user, { viewModel.smb = smb.copy(user = it) })
                            SetupField("パスワード", smb.password, { viewModel.smb = smb.copy(password = it) },
                                isPassword = true)
                        }
                        SetupPage.CLOUDFLARE -> {
                            SetupInstructions("Cloudflare Access を利用する場合は、サービストークンを入力してください。")
                            SetupField("Client ID", viewModel.clientId, { viewModel.clientId = it },
                                placeholder = AppStrings.SETTINGS_PLACEHOLDER_CF_CLIENT_ID,
                                focusRequester = initialFocus)
                            SetupField("Client Secret", viewModel.clientSecret, { viewModel.clientSecret = it },
                                isPassword = true)
                        }
                    }
                }

                if (connection == null) {
                    viewModel.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (page != SetupPage.WELCOME) {
                        OutlinedButton(onClick = viewModel::back, enabled = !viewModel.isSaving) { Text("戻る") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (page == SetupPage.FILE_LIBRARY) {
                        OutlinedButton(onClick = { viewModel.next(skip = true) }, enabled = !viewModel.isSaving) {
                            Text("スキップ")
                        }
                    }
                    Button(
                        onClick = { viewModel.next(skip = skipCloudflare) },
                        modifier = if (focusNext) Modifier.focusRequester(initialFocus) else Modifier,
                        enabled = !viewModel.isSaving
                    ) {
                        Text(when {
                            viewModel.isSaving -> "保存中…"
                            page == SetupPage.WELCOME -> "セットアップを始める"
                            skipCloudflare -> "スキップして完了"
                            page == SetupPage.CLOUDFLARE -> "設定を完了"
                            else -> "次へ"
                        })
                    }
                }
            }
        }
    }
}

@Composable
internal fun SetupReadErrorScreen(onRetry: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.safeRequestFocusWithRetry("SetupReadError") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("設定を読み込めませんでした。")
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focusRequester)) { Text("再試行") }
        }
    }
}

@Composable
private fun SetupChoices(
    options: List<Pair<StreamSource, String>>,
    selected: StreamSource,
    initialFocus: FocusRequester,
    onSelect: (StreamSource) -> Unit
) {
    options.forEach { (source, label) ->
        SelectionDialogItem(
            label = label,
            isSelected = selected == source,
            onClick = { onSelect(source) },
            modifier = if (selected == source) Modifier.focusRequester(initialFocus) else Modifier
        )
    }
}

@Composable
private fun SetupInstructions(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = KomorebiTheme.colors.textPrimary)
}

@Composable
internal fun SetupField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    keyboardType: KeyboardType = KeyboardType.Text,
    startEditing: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = KomorebiTheme.colors.textSecondary)
        DialogTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            isPassword = isPassword,
            focusRequester = focusRequester,
            keyboardType = keyboardType,
            startEditing = startEditing
        )
    }
}
