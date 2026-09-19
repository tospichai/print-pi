package com.maepranam.networkprinter.config

data class PrinterConfig(
    val appKey: String = "",
    val cluster: String = "",
    val printerHost: String = "",
    val printerPort: Int = DEFAULT_PRINTER_PORT,
) {
    fun validate(): List<String> = buildList {
        if (appKey.isBlank()) add("Pusher App Key is required")
        if (cluster.isBlank()) add("Pusher Cluster is required")
        if (printerHost.isBlank()) add("Printer IP or hostname is required")
        if (printerPort !in 1..65_535) add("Printer port must be between 1 and 65535")
    }

    companion object {
        const val DEFAULT_PRINTER_PORT = 9100
    }
}
