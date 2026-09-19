package com.maepranam.networkprinter.config

data class PrinterConfig(
    val appKey: String = "",
    val cluster: String = "",
    val printerHost: String = "",
    val printerPort: Int = DEFAULT_PRINTER_PORT,
) {
    fun validate(): List<String> = buildList {
        if (appKey.isBlank()) add("Pusher App Key is required")
        if (cluster.isBlank()) {
            add("Pusher Cluster is required")
        } else if (!cluster.matches(CLUSTER_PATTERN)) {
            add("Pusher Cluster may contain only letters, numbers, and hyphens")
        }
        if (printerHost.isBlank()) add("Printer IP or hostname is required")
        if (printerPort !in 1..65_535) add("Printer port must be between 1 and 65535")
    }

    companion object {
        const val DEFAULT_PRINTER_PORT = 9100
        private val CLUSTER_PATTERN = Regex("^[A-Za-z0-9-]+$")
    }
}

data class PrinterConfigDraft(
    val appKey: String,
    val cluster: String,
    val printerHost: String,
    val printerPort: String,
) {
    fun toConfig(): PrinterConfig = PrinterConfig(
        appKey = appKey,
        cluster = cluster,
        printerHost = printerHost,
        printerPort = printerPort.toIntOrNull() ?: -1,
    )
}
