package com.maepranam.networkprinter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import com.maepranam.networkprinter.config.PrinterConfig
import com.maepranam.networkprinter.config.PrinterConfigDraft
import com.maepranam.networkprinter.config.PrinterConfigStore
import com.maepranam.networkprinter.runtime.LogLevel
import com.maepranam.networkprinter.runtime.PrinterRuntime
import com.maepranam.networkprinter.runtime.PrinterStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private val runtime = PrinterRuntime.shared
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var configStore: PrinterConfigStore
    private lateinit var appKey: EditText
    private lateinit var cluster: EditText
    private lateinit var printerHost: EditText
    private lateinit var printerPort: EditText
    private lateinit var status: TextView
    private lateinit var logs: TextView

    private val refresh = object : Runnable {
        override fun run() {
            val snapshot = runtime.snapshot()
            status.text = statusLabel(snapshot.status)
            status.setTextColor(statusColor(snapshot.status))
            logs.text = snapshot.logs.joinToString("\n") { entry ->
                "${LOG_TIME_FORMAT.format(entry.time.atZone(ZoneId.systemDefault()))} ${entry.level}: ${entry.message}"
            }
            handler.postDelayed(this, STATUS_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configStore = PrinterConfigStore(this)
        bindViews()
        populateConfig(configStore.load())

        findViewById<TextView>(R.id.start).setOnClickListener { saveAndStart() }
        findViewById<TextView>(R.id.stop).setOnClickListener {
            startService(Intent(this, PrinterService::class.java).setAction(PrinterService.ACTION_STOP))
        }
        findViewById<TextView>(R.id.testPrint).setOnClickListener { testPrint() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            runtime.appendLog(
                "Notification permission denied; printer service continues without a drawer notification",
                LogLevel.WARNING,
            )
        }
    }

    private fun bindViews() {
        appKey = findViewById(R.id.appKey)
        cluster = findViewById(R.id.cluster)
        printerHost = findViewById(R.id.printerHost)
        printerPort = findViewById(R.id.printerPort)
        status = findViewById(R.id.status)
        logs = findViewById(R.id.logs)
    }

    private fun populateConfig(config: PrinterConfig) {
        appKey.setText(config.appKey)
        cluster.setText(config.cluster)
        printerHost.setText(config.printerHost)
        printerPort.setText(String.format(Locale.US, "%d", config.printerPort))
    }

    private fun readConfig(): PrinterConfig = PrinterConfigDraft(
        appKey = appKey.text.toString(),
        cluster = cluster.text.toString(),
        printerHost = printerHost.text.toString(),
        printerPort = printerPort.text.toString(),
    ).toConfig()

    private fun saveAndStart() {
        val config = readConfig()
        if (!validate(config)) return
        configStore.save(config)
        requestNotificationPermissionIfNeeded()
        startForegroundService(
            Intent(this, PrinterService::class.java).setAction(PrinterService.ACTION_START),
        )
    }

    private fun testPrint() {
        val config = readConfig()
        if (!validate(config)) return
        configStore.save(config)
        requestNotificationPermissionIfNeeded()
        startForegroundService(
            Intent(this, PrinterService::class.java).setAction(PrinterService.ACTION_TEST_PRINT),
        )
    }

    private fun validate(config: PrinterConfig): Boolean {
        val errors = config.validate()
        if (errors.isEmpty()) return true
        AlertDialog.Builder(this)
            .setTitle(R.string.validation_title)
            .setMessage(errors.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return false
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }
    }

    private fun statusLabel(value: PrinterStatus): String = when (value) {
        PrinterStatus.STOPPED -> getString(R.string.status_stopped)
        PrinterStatus.CONNECTING -> getString(R.string.status_connecting)
        PrinterStatus.READY -> getString(R.string.status_ready)
        PrinterStatus.PRINTING -> getString(R.string.status_printing)
        PrinterStatus.ERROR -> getString(R.string.status_error)
    }

    private fun statusColor(value: PrinterStatus): Int = when (value) {
        PrinterStatus.STOPPED -> Color.DKGRAY
        PrinterStatus.CONNECTING -> Color.rgb(180, 100, 0)
        PrinterStatus.READY -> Color.rgb(0, 120, 45)
        PrinterStatus.PRINTING -> Color.rgb(0, 80, 180)
        PrinterStatus.ERROR -> Color.rgb(190, 0, 0)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 100
        const val STATUS_REFRESH_MS = 500L
        val LOG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
