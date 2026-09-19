package com.maepranam.networkprinter.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
