package com.maepranam.networkprinter.config

import android.content.Context

class PrinterConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): PrinterConfig = PrinterConfig(
        appKey = preferences.getString(KEY_APP_KEY, "").orEmpty(),
        cluster = preferences.getString(KEY_CLUSTER, "").orEmpty(),
        printerHost = preferences.getString(KEY_PRINTER_HOST, "").orEmpty(),
        printerPort = preferences.getInt(KEY_PRINTER_PORT, PrinterConfig.DEFAULT_PRINTER_PORT),
    )

    fun save(config: PrinterConfig) {
        preferences.edit()
            .putString(KEY_APP_KEY, config.appKey.trim())
            .putString(KEY_CLUSTER, config.cluster.trim())
            .putString(KEY_PRINTER_HOST, config.printerHost.trim())
            .putInt(KEY_PRINTER_PORT, config.printerPort)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "printer_config"
        const val KEY_APP_KEY = "app_key"
        const val KEY_CLUSTER = "cluster"
        const val KEY_PRINTER_HOST = "printer_host"
        const val KEY_PRINTER_PORT = "printer_port"
    }
}
