package com.maepranam.networkprinter.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPrinterTest {
    @Test
    fun retriesTwiceThenSucceedsOnThirdAttempt() {
        val factory = FakeSocketFactory(failuresBeforeSuccess = 2)

        NetworkPrinter(factory, attempts = 3, retryDelayMs = 0)
            .send("printer.local", 9100, byteArrayOf(1, 2))

        assertEquals(3, factory.created)
        assertEquals(3, factory.closed)
        assertArrayEquals(byteArrayOf(1, 2), factory.bytes.toByteArray())
    }

    @Test
    fun stopsAfterExactlyThreeFailedAttempts() {
        val factory = FakeSocketFactory(failuresBeforeSuccess = Int.MAX_VALUE)

        assertThrows(IOException::class.java) {
            NetworkPrinter(factory, attempts = 3, retryDelayMs = 0)
                .send("bad", 9100, byteArrayOf(1))
        }

        assertEquals(3, factory.created)
        assertEquals(3, factory.closed)
    }

    @Test
    fun stalledWriteTimesOutAndClosesConnection() {
        val factory = BlockingSocketFactory()
        val startedAt = System.nanoTime()

        assertThrows(SocketTimeoutException::class.java) {
            NetworkPrinter(factory, attempts = 1, retryDelayMs = 0, attemptTimeoutMs = 50)
                .send("printer.local", 9100, byteArrayOf(1))
        }

        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        assertTrue("Attempt took ${elapsedMs}ms", elapsedMs < 1_000)
        assertEquals(1, factory.closed)
    }

    @Test
    fun cancelActiveStopsBlockingAttempt() {
        val factory = BlockingSocketFactory()
        val printer = NetworkPrinter(factory, attempts = 3, retryDelayMs = 0, attemptTimeoutMs = 5_000)
        val failure = AtomicReference<Throwable>()
        val sender = Thread {
            try {
                printer.send("printer.local", 9100, byteArrayOf(1))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        sender.start()
        assertTrue(factory.writeStarted.await(1, TimeUnit.SECONDS))

        printer.cancelActive()
        sender.join(1_000)

        assertFalse("Sender thread remained blocked", sender.isAlive)
        assertTrue(failure.get() is InterruptedException)
        assertEquals(1, factory.closed)
    }

    private class FakeSocketFactory(
        private val failuresBeforeSuccess: Int,
    ) : SocketConnectionFactory {
        var created = 0
        var closed = 0
        val bytes = ByteArrayOutputStream()

        override fun create(): SocketConnection {
            created += 1
            val attempt = created
            return object : SocketConnection {
                override fun connect(host: String, port: Int, timeoutMs: Int) {
                    if (attempt <= failuresBeforeSuccess) throw IOException("connect failed")
                }

                override fun write(bytes: ByteArray) {
                    this@FakeSocketFactory.bytes.write(bytes)
                }

                override fun close() {
                    closed += 1
                }
            }
        }
    }

    private class BlockingSocketFactory : SocketConnectionFactory {
        val writeStarted = CountDownLatch(1)
        private val released = CountDownLatch(1)
        var closed = 0

        override fun create(): SocketConnection = object : SocketConnection {
            override fun connect(host: String, port: Int, timeoutMs: Int) = Unit

            override fun write(bytes: ByteArray) {
                writeStarted.countDown()
                released.await()
                if (closed > 0) throw IOException("closed")
            }

            override fun close() {
                closed += 1
                released.countDown()
            }
        }
    }
}
