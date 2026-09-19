package com.maepranam.networkprinter.job

import com.google.gson.Gson
import com.maepranam.networkprinter.runtime.PrinterRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintEventAcceptorTest {
    @Test
    fun validEventQueuesReceiptWithoutLoggingSignedQuery() {
        val queue = SerialPrintQueue(1)
        val runtime = PrinterRuntime()
        val acceptor = PrintEventAcceptor(PrintJobParser(Gson()), queue, runtime)

        val accepted = acceptor.accept(
            "{\"fullPath\":\"https://shop.test/r.png?signature=secret\"}",
        )

        assertEquals(EventAcceptance.ACCEPTED, accepted)
        assertEquals("https://shop.test/r.png?signature=secret", (queue.take() as PrintRequest.Receipt).source.toString())
        assertTrue(runtime.snapshot().logs.last().message.contains("https://shop.test/r.png"))
        assertFalse(runtime.snapshot().logs.last().message.contains("secret"))
    }

    @Test
    fun invalidEventIsRejectedWithoutQueueing() {
        val queue = SerialPrintQueue(1)
        val runtime = PrinterRuntime()
        val acceptor = PrintEventAcceptor(PrintJobParser(Gson()), queue, runtime)

        val accepted = acceptor.accept("not json")

        assertEquals(EventAcceptance.INVALID, accepted)
        assertEquals(0, queue.size())
    }

    @Test
    fun fullQueueRejectsNewReceipt() {
        val queue = SerialPrintQueue(1)
        queue.offer(PrintRequest.TestPage)
        val runtime = PrinterRuntime()
        val acceptor = PrintEventAcceptor(PrintJobParser(Gson()), queue, runtime)

        val accepted = acceptor.accept("{\"fullPath\":\"https://shop.test/r.png\"}")

        assertEquals(EventAcceptance.QUEUE_FULL, accepted)
        assertEquals(1, queue.size())
    }
}
