package com.maepranam.networkprinter.job

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialPrintQueueTest {
    @Test
    fun remainsFifo() {
        val queue = SerialPrintQueue(2)
        val first = PrintRequest.Receipt(URI("https://x.test/1"))
        val second = PrintRequest.Receipt(URI("https://x.test/2"))

        assertTrue(queue.offer(first))
        assertTrue(queue.offer(second))
        assertEquals(first, queue.take())
        assertEquals(second, queue.take())
    }

    @Test
    fun fullQueueRejectsImmediately() {
        val queue = SerialPrintQueue(1)

        assertTrue(queue.offer(PrintRequest.Receipt(URI("https://x.test/1"))))
        assertFalse(queue.offer(PrintRequest.TestPage))
    }

    @Test
    fun clearDropsEveryPendingRequest() {
        val queue = SerialPrintQueue(2)
        queue.offer(PrintRequest.Receipt(URI("https://x.test/1")))
        queue.offer(PrintRequest.TestPage)

        queue.clear()

        assertEquals(0, queue.size())
    }
}
