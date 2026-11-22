package com.beeregg2001.komorebi.data

import com.beeregg2001.komorebi.data.model.StreamSource

internal data class SetupConnection(
    val source: StreamSource,
    val host: String,
    val port: String,
    val httpPort: String? = null,
    val manualKonomi: Boolean = false
) {
    val konomi get() = KonomiConnection(host, port, manualKonomi)
}
