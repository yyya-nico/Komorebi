package com.beeregg2001.komorebi.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object ConnectionAddress {
    fun isIpv4(value: String): Boolean {
        val octets = value.trim().split('.')
        return octets.size == 4 && octets.all {
            it.length in 1..3 && it.all { digit -> digit in '0'..'9' } && it.toInt() <= 255
        }
    }

    fun usesHostName(value: String): Boolean = value.isNotBlank() && !isIpv4(value)

    fun hasHttpScheme(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    fun hostForSmb(value: String): String {
        val address = value.trim()
        if (isIpv4(address)) return address
        val url = if (hasHttpScheme(address)) address else "http://$address"
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        return if (':' in host) "[$host]" else host
    }

    fun validationError(host: String, manual: Boolean, allowUrl: Boolean = true): String? {
        val address = host.trim()
        if (!manual) {
            return if (isIpv4(address)) null else "有効なIPv4アドレスを入力してください。"
        }
        if (!allowUrl) {
            // SMB はホスト名の後ろに共有名やサブディレクトリを付けられる。
            val smbHost = address.substringBefore('/')
            val validHost = smbHost.isNotBlank() && smbHost.none { it.isWhitespace() || it in "\\?#@" } &&
                "http://$smbHost".toHttpUrlOrNull() != null
            return if (validHost && !address.contains("://")) null else "有効なホスト名を入力してください。"
        }
        if (address.isBlank() || address.any { it.isWhitespace() } || address.first() in "/\\") {
            return "有効なホスト名またはURLを入力してください。"
        }
        val url = if (hasHttpScheme(address)) address else "http://$address"
        return if ((!address.contains("://") || hasHttpScheme(address)) && url.toHttpUrlOrNull() != null) {
            null
        } else {
            "有効なホスト名またはURLを入力してください。"
        }
    }

    fun isValidPort(value: String): Boolean =
        value.trim().toIntOrNull()?.let { it in 1..65535 } == true
}
