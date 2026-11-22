package com.beeregg2001.komorebi.data

import com.beeregg2001.komorebi.data.model.StreamSource

internal data class SetupConnection(
    val source: StreamSource,
    val host: String = "",
    val port: String = defaultPort(source),
    val httpPort: String? = null,
    val manualAddress: Boolean = false
) {
    val serviceName: String
        get() = when (source) {
            StreamSource.KONOMITV -> "KonomiTV"
            StreamSource.EDCB -> "EDCB"
            StreamSource.EPGSTATION -> "EPGStation"
            StreamSource.MIRAKURUN -> "Mirakurun"
        }

    val title: String get() = "$serviceName の接続先"

    fun validationError(): String? {
        if (source == StreamSource.KONOMITV) {
            return KonomiConnection(host, port, manualAddress).validationError()
        }
        return ConnectionAddress.validationError(host, manualAddress) ?: when {
            !ConnectionAddress.isValidPort(port) -> "ポート番号は1〜65535で入力してください。"
            httpPort?.let { !ConnectionAddress.isValidPort(it) } == true -> "HTTP/HTTPSポートは1〜65535で入力してください。"
            else -> null
        }
    }

    fun normalized(savedHost: String? = null) = copy(
        host = if (source != StreamSource.KONOMITV) {
            host.trim()
        } else if (manualAddress && savedHost != null && savedHost.trim() == host.trim()) {
            savedHost
        } else {
            KonomiConnection(host, port, manualAddress).storageHost()
        },
        port = port.trim(),
        httpPort = httpPort?.trim()
    )

    companion object {
        fun defaultPort(source: StreamSource): String = when (source) {
            StreamSource.KONOMITV -> KonomiConnection.DEFAULT_PORT
            StreamSource.EDCB -> "4510"
            StreamSource.EPGSTATION -> "8888"
            StreamSource.MIRAKURUN -> "40772"
        }

        fun fromSaved(source: StreamSource, host: String, port: String): SetupConnection {
            if (source == StreamSource.KONOMITV) {
                val konomi = KonomiConnection.fromSaved(host, port)
                return SetupConnection(source, konomi.host, konomi.port, manualAddress = konomi.manual)
            }
            return SetupConnection(
                source, host, port.ifBlank { defaultPort(source) },
                manualAddress = ConnectionAddress.usesHostName(host)
            )
        }
    }
}
