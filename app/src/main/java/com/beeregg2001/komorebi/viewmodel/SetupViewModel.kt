package com.beeregg2001.komorebi.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beeregg2001.komorebi.data.ConnectionAddress
import com.beeregg2001.komorebi.data.SettingsRepository
import com.beeregg2001.komorebi.data.SetupConnection
import com.beeregg2001.komorebi.data.model.StreamSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.launch

internal enum class SetupPage(val step: Int, val title: String, val isChoice: Boolean = false) {
    WELCOME(1, "Komorebiへようこそ"),
    SYSTEM(2, "どのシステムをメインにしますか？", isChoice = true),
    MAIN_CONNECTION(3, "接続先を入力してください"),
    SOURCE(4, "どのソースを優先しますか？", isChoice = true),
    SOURCE_CONNECTION(5, "接続先を入力してください"),
    FILE_LIBRARY_CHOICE(6, "ファイルライブラリを利用しますか？", isChoice = true),
    FILE_LIBRARY(6, "ファイルライブラリを追加"),
    CLOUDFLARE_CHOICE(7, "Cloudflare Access を利用しますか？", isChoice = true),
    CLOUDFLARE(7, "Cloudflare Zero Trust")
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
    var mainSystem by mutableStateOf<StreamSource?>(null)
        private set
    var preferredSource by mutableStateOf<StreamSource?>(null)
        private set
    var useFileLibrary by mutableStateOf<Boolean?>(null)
        private set
    var useCloudflare by mutableStateOf<Boolean?>(null)
        private set
    var smb by mutableStateOf(SmbServer(name = "", ip = "", port = "445", user = "", password = ""))
    var smbManualAddress by mutableStateOf(false)
        private set
    var clientId by mutableStateOf("")
    var clientSecret by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val editedConnectionHosts = mutableSetOf<StreamSource>()
    private var smbHostEdited = false
    private val connections = mutableStateMapOf(
        StreamSource.KONOMITV to SetupConnection(StreamSource.KONOMITV),
        StreamSource.EDCB to SetupConnection(StreamSource.EDCB, httpPort = "5510"),
        StreamSource.EPGSTATION to SetupConnection(StreamSource.EPGSTATION),
        StreamSource.MIRAKURUN to SetupConnection(StreamSource.MIRAKURUN)
    )

    val mainConnection: SetupConnection
        get() = connection(checkNotNull(mainSystem))

    val alternateSource: StreamSource?
        get() = preferredSource?.takeUnless { it == StreamSource.KONOMITV || it == mainSystem }

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

    fun selectMainSystem(source: StreamSource) {
        if (page != SetupPage.SYSTEM) return
        mainSystem = source
        advance()
    }

    fun selectPreferredSource(source: StreamSource) {
        if (page != SetupPage.SOURCE) return
        preferredSource = source
        advance()
    }

    fun selectFileLibrary(use: Boolean) {
        if (page != SetupPage.FILE_LIBRARY_CHOICE) return
        useFileLibrary = use
        if (use) advance() else page = SetupPage.CLOUDFLARE_CHOICE
    }

    fun selectCloudflare(use: Boolean) {
        if (isSaving || page != SetupPage.CLOUDFLARE_CHOICE) return
        useCloudflare = use
        error = null
        if (use) advance() else finish(skipCloudflare = true)
    }

    fun updateConnection(connection: SetupConnection) {
        val previous = connections.getValue(connection.source)
        if (connection.host != previous.host || connection.manualAddress != previous.manualAddress) {
            editedConnectionHosts.add(connection.source)
        }
        connections[connection.source] = connection
        error = null
    }

    fun updateSmbAddress(host: String, manual: Boolean) {
        smbHostEdited = true
        smb = smb.copy(ip = host)
        smbManualAddress = manual
        error = null
    }

    fun back() {
        if (isSaving || page == SetupPage.WELCOME) return
        error = null
        page = when (page) {
            SetupPage.CLOUDFLARE_CHOICE ->
                if (useFileLibrary == true) SetupPage.FILE_LIBRARY else SetupPage.FILE_LIBRARY_CHOICE
            else -> SetupPage.entries[page.ordinal - 1]
        }
    }

    fun next() {
        if (isSaving || page.isChoice) return
        val message = when (page) {
            SetupPage.MAIN_CONNECTION -> mainConnection.validationError()
            SetupPage.SOURCE_CONNECTION -> alternateSource?.let {
                connection(it).copy(httpPort = null).validationError()
            }
            SetupPage.FILE_LIBRARY -> smbError()
            else -> null
        }
        error = message
        if (message != null) return

        if (page == SetupPage.CLOUDFLARE) {
            finish(skipCloudflare = false)
        } else {
            advance()
        }
    }

    private fun advance() {
        val nextPage = SetupPage.entries[page.ordinal + 1]
        prefillAddress(nextPage)
        page = nextPage
    }

    private fun prefillAddress(page: SetupPage) {
        when (page) {
            SetupPage.SOURCE_CONNECTION -> {
                val source = alternateSource ?: return
                if (source !in editedConnectionHosts) {
                    val main = mainConnection
                    connections[source] = connection(source).copy(
                        host = main.host.trim(),
                        manualAddress = main.manualAddress
                    )
                }
            }
            SetupPage.FILE_LIBRARY -> if (!smbHostEdited) {
                val previous = alternateSource?.let(::connection) ?: mainConnection
                val host = ConnectionAddress.hostForSmb(previous.host)
                smb = smb.copy(ip = host)
                smbManualAddress = ConnectionAddress.usesHostName(host)
            }
            else -> Unit
        }
    }

    private fun smbError(): String? {
        if (smb.name.isBlank()) return "ファイルライブラリの表示名を入力してください。"
        return ConnectionAddress.validationError(smb.ip, smbManualAddress, allowUrl = false)
            ?: if (smb.port.isNotBlank() && !ConnectionAddress.isValidPort(smb.port)) {
                "ポート番号は1〜65535で入力してください。"
            } else null
    }

    private fun finish(skipCloudflare: Boolean) {
        val accessHeaders = if (skipCloudflare) emptyMap() else
            SettingsRepository.buildCfAccessHeaders(clientId, clientSecret)
        if (!skipCloudflare && accessHeaders.isEmpty()) {
            error = "Client ID と Client Secret の両方を入力してください。"
            return
        }
        if (accessHeaders.values.any { token -> token.any { it !in '!'..'~' } }) {
            error = "サービストークンを半角文字で入力してください。"
            return
        }

        val smbServer = smb.takeIf { useFileLibrary == true }?.let {
            it.copy(name = it.name.trim(), ip = it.ip.trim(), port = it.port.trim().ifEmpty { "445" })
        }
        isSaving = true
        viewModelScope.launch {
            try {
                repository.completeInitialSetup(
                    main = mainConnection.normalized(),
                    preferredSource = checkNotNull(preferredSource),
                    alternate = alternateSource?.let { connection(it).copy(httpPort = null).normalized() },
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
