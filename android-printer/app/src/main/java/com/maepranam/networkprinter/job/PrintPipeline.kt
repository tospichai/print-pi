package com.maepranam.networkprinter.job

import com.maepranam.networkprinter.config.PrinterConfig
import com.maepranam.networkprinter.escpos.EscPosEncoder
import com.maepranam.networkprinter.escpos.RasterImage
import com.maepranam.networkprinter.runtime.LogLevel
import com.maepranam.networkprinter.runtime.PrinterRuntime
import com.maepranam.networkprinter.runtime.PrinterStatus
import java.io.File

fun interface ReceiptSource {
    fun download(receipt: PrintRequest.Receipt): File
}

interface ImageSource {
    fun decode(file: File): RasterImage

    fun testPage(): RasterImage
}

fun interface PrinterSink {
    fun send(config: PrinterConfig, bytes: ByteArray)
}

class PrintPipeline(
    private val receiptSource: ReceiptSource,
    private val imageSource: ImageSource,
    private val encoder: EscPosEncoder,
    private val printerSink: PrinterSink,
    private val runtime: PrinterRuntime,
) {
    fun process(request: PrintRequest, config: PrinterConfig) {
        runtime.setStatus(PrinterStatus.PRINTING)
        when (request) {
            is PrintRequest.Receipt -> processReceipt(request, config)
            PrintRequest.TestPage -> sendImage(imageSource.testPage(), config)
        }
        runtime.appendLog("Print completed")
    }

    private fun processReceipt(receipt: PrintRequest.Receipt, config: PrinterConfig) {
        var downloaded: File? = null
        try {
            downloaded = receiptSource.download(receipt)
            sendImage(imageSource.decode(downloaded), config)
        } finally {
            downloaded?.delete()
        }
    }

    private fun sendImage(image: RasterImage, config: PrinterConfig) {
        printerSink.send(config, encoder.encode(image))
    }
}

class PrintWorker(
    private val runtime: PrinterRuntime,
) {
    fun run(
        nextRequest: () -> PrintRequest,
        isRunning: () -> Boolean,
        process: (PrintRequest) -> Unit,
    ) {
        while (isRunning()) {
            val request = try {
                nextRequest()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

            val previousStatus = runtime.snapshot().status
            try {
                process(request)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (failure: Exception) {
                runtime.setStatus(PrinterStatus.ERROR)
                runtime.appendLog(
                    failure.message ?: "Print job failed",
                    LogLevel.ERROR,
                )
            } finally {
                runtime.compareAndSetStatus(PrinterStatus.PRINTING, previousStatus)
            }
        }
    }
}
