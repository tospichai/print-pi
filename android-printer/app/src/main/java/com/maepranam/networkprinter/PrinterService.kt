package com.maepranam.networkprinter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.gson.Gson
import com.maepranam.networkprinter.config.PrinterConfig
import com.maepranam.networkprinter.config.PrinterConfigStore
import com.maepranam.networkprinter.escpos.AndroidImageAdapter
import com.maepranam.networkprinter.escpos.EscPosEncoder
import com.maepranam.networkprinter.escpos.RasterImage
import com.maepranam.networkprinter.job.ImageSource
import com.maepranam.networkprinter.job.PrintEventAcceptor
import com.maepranam.networkprinter.job.PrintJobParser
import com.maepranam.networkprinter.job.PrintPipeline
import com.maepranam.networkprinter.job.PrintRequest
import com.maepranam.networkprinter.job.PrintWorker
import com.maepranam.networkprinter.job.PrinterSink
import com.maepranam.networkprinter.job.ReceiptSource
import com.maepranam.networkprinter.job.SerialPrintQueue
import com.maepranam.networkprinter.net.NetworkPrinter
import com.maepranam.networkprinter.net.ReceiptDownloader
import com.maepranam.networkprinter.runtime.LogLevel
import com.maepranam.networkprinter.runtime.PrinterRuntime
import com.maepranam.networkprinter.runtime.PrinterStatus
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionState
import com.pusher.client.connection.ConnectionStateChange
import java.io.File

class PrinterService : Service() {
    private val runtime = PrinterRuntime.shared
    private val queue = SerialPrintQueue(QUEUE_CAPACITY)
    private val networkPrinter = NetworkPrinter()
    private val imageAdapter = AndroidImageAdapter()
    private lateinit var configStore: PrinterConfigStore
    private lateinit var pipeline: PrintPipeline
    private lateinit var eventAcceptor: PrintEventAcceptor

    @Volatile
    private var running = false
    private var shuttingDown = false
    private var workerThread: Thread? = null
    private var pusher: Pusher? = null

    override fun onCreate() {
        super.onCreate()
        configStore = PrinterConfigStore(this)
        val downloader = ReceiptDownloader(cacheDir)
        pipeline = PrintPipeline(
            receiptSource = ReceiptSource { receipt -> downloader.download(receipt.source) },
            imageSource = object : ImageSource {
                override fun decode(file: File): RasterImage = imageAdapter.decode(file)

                override fun testPage(): RasterImage = imageAdapter.createTestImage()
            },
            encoder = EscPosEncoder(),
            printerSink = PrinterSink { config, bytes ->
                networkPrinter.send(config.printerHost, config.printerPort, bytes)
            },
            runtime = runtime,
        )
        eventAcceptor = PrintEventAcceptor(PrintJobParser(Gson()), queue, runtime)
        showNotification("Starting printer service", enterForeground = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_TEST_PRINT -> enqueueTestPage()
            ACTION_STOP -> stopSelf()
            else -> runtime.appendLog("Unknown service action", LogLevel.WARNING)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shuttingDown = true
        pusher?.disconnect()
        pusher = null
        running = false
        workerThread?.interrupt()
        try {
            workerThread?.join(WORKER_JOIN_TIMEOUT_MS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        workerThread = null
        queue.clear()
        runtime.setStatus(PrinterStatus.STOPPED)
        runtime.appendLog("Printer service stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startListening() {
        val config = configStore.load()
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            runtime.setStatus(PrinterStatus.ERROR)
            runtime.appendLog(errors.joinToString("; "), LogLevel.ERROR)
            showNotification("Printer settings are invalid")
            return
        }

        shuttingDown = false
        startWorker()
        connectPusher(config)
    }

    private fun enqueueTestPage() {
        val config = configStore.load()
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            runtime.setStatus(PrinterStatus.ERROR)
            runtime.appendLog(errors.joinToString("; "), LogLevel.ERROR)
            return
        }
        startWorker()
        if (queue.offer(PrintRequest.TestPage)) {
            runtime.appendLog("Test page queued")
        } else {
            runtime.appendLog("Print queue is full; test page was skipped", LogLevel.WARNING)
        }
    }

    private fun startWorker() {
        if (workerThread?.isAlive == true) return
        running = true
        workerThread = Thread({
            PrintWorker(runtime).run(
                nextRequest = { queue.take() },
                isRunning = { running },
                process = { request ->
                    showNotification("Printing receipt")
                    try {
                        pipeline.process(request, configStore.load())
                    } finally {
                        if (running) showNotification("Waiting for print jobs")
                    }
                },
            )
        }, WORKER_THREAD_NAME).apply { start() }
    }

    private fun connectPusher(config: PrinterConfig) {
        pusher?.disconnect()
        runtime.setStatus(PrinterStatus.CONNECTING)
        showNotification("Connecting to Pusher")

        val client = Pusher(
            config.appKey,
            PusherOptions().setCluster(config.cluster).setUseTLS(true),
        )
        client.subscribe(CHANNEL_NAME).bind(EVENT_NAME) { event ->
            eventAcceptor.accept(event.data)
        }
        client.connect(connectionListener)
        pusher = client
    }

    private val connectionListener = object : ConnectionEventListener {
        override fun onConnectionStateChange(change: ConnectionStateChange) {
            if (shuttingDown) return
            when (change.currentState) {
                ConnectionState.CONNECTING,
                ConnectionState.RECONNECTING,
                -> {
                    runtime.setStatus(PrinterStatus.CONNECTING)
                    runtime.appendLog("Pusher is connecting")
                    showNotification("Connecting to Pusher")
                }

                ConnectionState.CONNECTED -> {
                    runtime.setStatus(PrinterStatus.READY)
                    runtime.appendLog("Ready for print jobs")
                    showNotification("Waiting for print jobs")
                }

                ConnectionState.DISCONNECTED -> {
                    runtime.setStatus(PrinterStatus.ERROR)
                    runtime.appendLog("Pusher disconnected", LogLevel.WARNING)
                    showNotification("Pusher disconnected")
                }

                else -> Unit
            }
        }

        override fun onError(message: String?, code: String?, exception: Exception?) {
            if (shuttingDown) return
            runtime.setStatus(PrinterStatus.ERROR)
            runtime.appendLog(
                listOfNotNull(message, code).joinToString(" / ").ifBlank { "Pusher connection error" },
                LogLevel.ERROR,
            )
            showNotification("Pusher connection error")
        }
    }

    private fun showNotification(text: String, enterForeground: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Printer service", NotificationManager.IMPORTANCE_LOW),
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setPackage(packageName)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mae Pra Nam Printer")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        if (enterForeground) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.maepranam.networkprinter.START"
        const val ACTION_STOP = "com.maepranam.networkprinter.STOP"
        const val ACTION_TEST_PRINT = "com.maepranam.networkprinter.TEST_PRINT"
        const val CHANNEL_ID = "printer_service"
        const val NOTIFICATION_ID = 9100

        private const val CHANNEL_NAME = "orders"
        private const val EVENT_NAME = "print"
        private const val QUEUE_CAPACITY = 50
        private const val WORKER_JOIN_TIMEOUT_MS = 2_000L
        private const val WORKER_THREAD_NAME = "receipt-print-worker"
    }
}
