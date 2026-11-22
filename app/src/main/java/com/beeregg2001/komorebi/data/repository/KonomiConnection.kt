package com.beeregg2001.komorebi.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class KonomiConnection(
    val host: String = "",
    val port: String = DEFAULT_PORT,
    val manual: Boolean = false
) {
    fun validationError(): String? {
        val address = host.trim()
        if (manual) {
            if (address.isEmpty() || address.equals(SettingsRepository.KONOMI_PLACEHOLDER_HOST, ignoreCase = true) ||
                address.any { it.isWhitespace() } ||
                (address.contains("://") && !hasHttpScheme(address)) ||
                storageHost().toHttpUrlOrNull() == null
            ) {
                return "有効なFQDNまたはURLを入力してください。"
            }
        } else if (!isIpv4(address)) {
            return "有効なIPv4アドレスを入力してください。"
        }
        if (port.trim().toIntOrNull()?.let { it in 1..65535 } != true) {
            return "ポート番号は1〜65535で入力してください。"
        }
        return null
    }

    fun storageHost(): String {
        val address = host.trim()
        return when {
            !manual -> "https://${address.replace('.', '-')}$LOCAL_DOMAIN"
            hasHttpScheme(address) -> address
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
                if (isIpv4(ip)) return KonomiConnection(ip, portPart.ifEmpty { currentPort })
            }
            return KonomiConnection(host, currentPort, manual = true)
        }

        private fun hasHttpScheme(address: String): Boolean =
            address.startsWith("http://", ignoreCase = true) ||
                address.startsWith("https://", ignoreCase = true)

        private fun isIpv4(value: String): Boolean {
            val octets = value.split('.')
            return octets.size == 4 && octets.all {
                it.length in 1..3 && it.all { digit -> digit in '0'..'9' } && it.toInt() <= 255
            }
        }
    }
}
