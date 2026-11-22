package com.beeregg2001.komorebi.data

internal data class KonomiConnection(
    val host: String = "",
    val port: String = DEFAULT_PORT,
    val manual: Boolean = false
) {
    fun validationError(): String? {
        if (host.trim().equals(SettingsRepository.KONOMI_PLACEHOLDER_HOST, ignoreCase = true)) {
            return "有効なホスト名またはURLを入力してください。"
        }
        return ConnectionAddress.validationError(host, manual)
            ?: if (!ConnectionAddress.isValidPort(port)) "ポート番号は1〜65535で入力してください。" else null
    }

    fun storageHost(): String {
        val address = host.trim()
        return when {
            !manual -> "https://${address.replace('.', '-')}$LOCAL_DOMAIN"
            ConnectionAddress.hasHttpScheme(address) -> address
            else -> "https://$address"
        }
    }

    companion object {
        const val DEFAULT_PORT = "7000"
        private const val LOCAL_DOMAIN = ".local.konomi.tv"
        private val localUrl = Regex(
            """https://([0-9]{1,3}(?:-[0-9]{1,3}){3})\.local\.konomi\.tv(?::([0-9]+))?/?""",
            RegexOption.IGNORE_CASE
        )

        fun fromSaved(host: String, port: String): KonomiConnection {
            val currentPort = port.ifBlank { DEFAULT_PORT }
            if (host.isBlank() || host.trim().equals(SettingsRepository.KONOMI_PLACEHOLDER_HOST, ignoreCase = true)) {
                return KonomiConnection(port = currentPort)
            }
            val localAddress = localUrl.matchEntire(host.trim())
            if (localAddress != null) {
                val (hostPart, portPart) = localAddress.destructured
                val ip = hostPart.replace('-', '.')
                if (ConnectionAddress.isIpv4(ip)) return KonomiConnection(ip, portPart.ifEmpty { currentPort })
            }
            return KonomiConnection(host, currentPort, manual = true)
        }
    }
}
