package com.maepranam.networkprinter.job

import com.maepranam.networkprinter.config.PrinterConfig
import com.maepranam.networkprinter.escpos.EscPosEncoder
import com.maepranam.networkprinter.escpos.RasterImage
import com.maepranam.networkprinter.runtime.PrinterRuntime
import com.maepranam.networkprinter.runtime.PrinterStatus
import java.io.File
import java.io.IOException
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPipelineTest {
    @Test
    fun deletesDownloadedFileAfterSuccessfulPrint() {
        val file = temporaryReceipt()

        pipeline(file = file, printerFails = false).process(receipt(), config())

        assertFalse(file.exists())
    }

    @Test
    fun deletesDownloadedFileAfterPrinterFailure() {
        val file = temporaryReceipt()

        assertThrows(IOException::class.java) {
            pipeline(file = file, printerFails = true).process(receipt(), config())
        }

        assertFalse(file.exists())
    }

    @Test
    fun testPageBypassesReceiptDownload() {
        var downloaded = false
        var printed = false
        val pipeline = PrintPipeline(
            receiptSource = ReceiptSource { downloaded = true; temporaryReceipt() },
            imageSource = fakeImages(),
            encoder = EscPosEncoder(),
            printerSink = PrinterSink { _, _ -> printed = true },
            runtime = PrinterRuntime(),
        )

        pipeline.process(PrintRequest.TestPage, config())

        assertFalse(downloaded)
        assertTrue(printed)
    }

    @Test
    fun workerContinuesAfterFirstRequestFails() {
        val requests = listOf(receipt("1"), receipt("2")).iterator()
        val processed = mutableListOf<String>()
        val worker = PrintWorker(PrinterRuntime())

        worker.run(
            nextRequest = { requests.next() },
            isRunning = { requests.hasNext() },
            process = { request ->
                val path = (request as PrintRequest.Receipt).source.path
                if (path.endsWith("1")) error("first fails") else processed += path
            },
        )

        assertEquals(listOf("/2"), processed)
    }

    @Test
    fun standaloneTestPrintRestoresStoppedInsteadOfClaimingReady() {
        val runtime = PrinterRuntime()
        val requests = listOf<PrintRequest>(PrintRequest.TestPage).iterator()

        PrintWorker(runtime).run(
            nextRequest = { requests.next() },
            isRunning = { requests.hasNext() },
            process = { runtime.setStatus(PrinterStatus.PRINTING) },
        )

        assertEquals(PrinterStatus.STOPPED, runtime.snapshot().status)
    }

    @Test
    fun connectionErrorDuringPrintIsNotOverwrittenWithReady() {
        val runtime = PrinterRuntime().apply { setStatus(PrinterStatus.READY) }
        val requests = listOf<PrintRequest>(PrintRequest.TestPage).iterator()

        PrintWorker(runtime).run(
            nextRequest = { requests.next() },
            isRunning = { requests.hasNext() },
            process = {
                runtime.setStatus(PrinterStatus.PRINTING)
                runtime.setStatus(PrinterStatus.ERROR)
            },
        )

        assertEquals(PrinterStatus.ERROR, runtime.snapshot().status)
    }

    private fun pipeline(file: File, printerFails: Boolean): PrintPipeline = PrintPipeline(
        receiptSource = ReceiptSource { file },
        imageSource = fakeImages(),
        encoder = EscPosEncoder(),
        printerSink = PrinterSink { _, _ -> if (printerFails) throw IOException("printer failed") },
        runtime = PrinterRuntime(),
    )

    private fun fakeImages(): ImageSource = object : ImageSource {
        override fun decode(file: File): RasterImage = RasterImage(1, 1, intArrayOf(0xff000000.toInt()))

        override fun testPage(): RasterImage = RasterImage(1, 1, intArrayOf(0xff000000.toInt()))
    }

    private fun temporaryReceipt(): File = File.createTempFile("receipt-test-", ".png").apply {
        writeText("image")
        deleteOnExit()
    }

    private fun receipt(id: String = "1") = PrintRequest.Receipt(URI("https://shop.test/$id"))

    private fun config() = PrinterConfig("key", "ap1", "printer.local", 9100)
}
