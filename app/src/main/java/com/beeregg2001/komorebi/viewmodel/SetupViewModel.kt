package com.beeregg2001.komorebi.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beeregg2001.komorebi.data.SettingsRepository
import com.beeregg2001.komorebi.data.SetupConnection
import com.beeregg2001.komorebi.data.model.StreamSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class SetupPage(val title: String) {
    WELCOME("Komorebiへようこそ"),
    SYSTEM("どのシステムをメインにしますか？"),
    MAIN_CONNECTION("接続先を入力してください"),
    SOURCE("どのソースを優先しますか？"),
    SOURCE_CONNECTION("接続先を入力してください"),
    FILE_LIBRARY("ファイルライブラリを追加"),
    CLOUDFLARE("Cloudflare Zero Trust")
}

internal enum class SetupRoute { LOADING, WIZARD, HOME, READ_ERROR }

@HiltViewModel
internal class SetupViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    var route by mutableStateOf(SetupRoute.LOADING)
        private set
    var page by mutableStateOf(SetupPage.WELCOME)
        private set
    var mainSystem by mutableStateOf(StreamSource.KONOMITV)
    var preferredSource by mutableStateOf(StreamSource.KONOMITV)
    var smb by mutableStateOf(SmbServer(name = "", ip = "", port = "445", user = "", password = ""))
    var clientId by mutableStateOf("")
    var clientSecret by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var addSmb = false
    private val connections = mutableStateMapOf(
        StreamSource.KONOMITV to SetupConnection(StreamSource.KONOMITV, "", "7000"),
        StreamSource.EDCB to SetupConnection(StreamSource.EDCB, "", "4510", "5510"),
        StreamSource.EPGSTATION to SetupConnection(StreamSource.EPGSTATION, "", "8888"),
        StreamSource.MIRAKURUN to SetupConnection(StreamSource.MIRAKURUN, "", "40772")
    )

    val alternateSource: StreamSource?
        get() = preferredSource.takeUnless { it == StreamSource.KONOMITV || it == mainSystem }

    init {
        load()
    }

    fun load() {
        route = SetupRoute.LOADING
        viewModelScope.launch {
            route = try {
                if (repository.needsInitialSetup()) SetupRoute.WIZARD else SetupRoute.HOME
            } catch (_: IOException) {
                SetupRoute.READ_ERROR
            }
        }
    }

    fun connection(source: StreamSource): SetupConnection = connections.getValue(source)

    fun updateConnection(connection: SetupConnection) {
        connections[connection.source] = connection
        error = null
    }

    fun back() {
        if (isSaving || page == SetupPage.WELCOME) return
        error = null
        page = SetupPage.entries[page.ordinal - 1]
    }

    fun next(skip: Boolean = false) {
        if (isSaving) return
        val message = when (page) {
            SetupPage.MAIN_CONNECTION -> connectionError(connection(mainSystem), true)
            SetupPage.SOURCE_CONNECTION -> alternateSource?.let {
                connectionError(connection(it), false)
            }
            SetupPage.FILE_LIBRARY -> if (skip) null else smbError()
            else -> null
        }
        error = message
        if (message != null) return

        if (page == SetupPage.CLOUDFLARE) {
            finish(skip)
        } else {
            if (page == SetupPage.FILE_LIBRARY) addSmb = !skip
            page = SetupPage.entries[page.ordinal + 1]
        }
    }

    private fun connectionError(connection: SetupConnection, includeHttpPort: Boolean): String? {
        val host = connection.host.trim()
        val url = if (host.contains("://")) host else "http://$host"
        if (host.isEmpty() || host.equals(SettingsRepository.KONOMI_PLACEHOLDER_HOST, ignoreCase = true) ||
            host.any { it.isWhitespace() } || url.toHttpUrlOrNull() == null
        ) {
            return "有効なホスト名、IPアドレス、またはURLを入力してください。"
        }
        if (!isValidPort(connection.port)) return "ポート番号は1〜65535で入力してください。"
        if (includeHttpPort && connection.httpPort?.let { !isValidPort(it) } == true) {
            return "HTTP/HTTPSポートは1〜65535で入力してください。"
        }
        return null
    }

    private fun smbError(): String? = when {
        smb.name.isBlank() -> "ファイルライブラリの表示名を入力してください。"
        smb.ip.isBlank() -> "SMBサーバーのホスト名またはIPアドレスを入力してください。"
        smb.port.isNotBlank() && !isValidPort(smb.port) -> "ポート番号は1〜65535で入力してください。"
        else -> null
    }

    private fun isValidPort(value: String): Boolean =
        value.trim().toIntOrNull()?.let { it in 1..65535 } == true

    private fun SetupConnection.normalized(includeHttpPort: Boolean) = copy(
        host = host.trim(),
        port = port.trim(),
        httpPort = httpPort?.trim()?.takeIf { includeHttpPort }
    )

    private fun finish(skip: Boolean) {
        val accessHeaders = if (skip) emptyMap() else
            SettingsRepository.buildCfAccessHeaders(clientId, clientSecret)
        if (!skip && accessHeaders.isEmpty()) {
            error = "Client ID と Client Secret の両方を入力してください。"
            return
        }
        if (accessHeaders.values.any { token -> token.any { it !in '!'..'~' } }) {
            error = "サービストークンを半角文字で入力してください。"
            return
        }

        val smbServer = smb.takeIf { addSmb }?.let {
            it.copy(name = it.name.trim(), ip = it.ip.trim(), port = it.port.trim().ifEmpty { "445" })
        }
        isSaving = true
        viewModelScope.launch {
            try {
                repository.completeInitialSetup(
                    main = connection(mainSystem).normalized(includeHttpPort = true),
                    preferredSource = preferredSource,
                    alternate = alternateSource?.let { connection(it).normalized(includeHttpPort = false) },
                    smbServer = smbServer,
                    accessHeaders = accessHeaders
                )
                smb = smb.copy(password = "")
                clientId = ""
                clientSecret = ""
                route = SetupRoute.HOME
            } catch (_: IOException) {
                error = "設定を保存できませんでした。もう一度お試しください。"
            } finally {
                isSaving = false
            }
        }
    }
}
