package com.maepranam.networkprinter.net

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDownloaderPolicyTest {
    private val policy = DownloadPolicy(maxBytes = 10L * 1024 * 1024)

    @Test
    fun acceptsUnknownAndBoundedLengths() {
        policy.validateLength(-1)
        policy.validateLength(10L * 1024 * 1024)
    }

    @Test
    fun rejectsDeclaredOversizeResponse() {
        assertThrows(IOException::class.java) {
            policy.validateLength(10L * 1024 * 1024 + 1)
        }
    }

    @Test
    fun streamingLimitRejectsOversizeBody() {
        assertThrows(IOException::class.java) {
            policy.copyLimited(
                ByteArrayInputStream(ByteArray(9)),
                ByteArrayOutputStream(),
                maxBytes = 8,
            )
        }
    }

    @Test
    fun streamingLimitPreservesBodyAtExactLimit() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        policy.copyLimited(ByteArrayInputStream(expected), output, maxBytes = 4)

        assertArrayEquals(expected, output.toByteArray())
    }

    @Test
    fun cancelActiveStopsBlockedDownloadBeforeRetrying() {
        val readStarted = CountDownLatch(1)
        val released = CountDownLatch(1)
        var opened = 0
        val downloader = ReceiptDownloader(
            cacheDir = createTempDirectory("receipt-downloader-test-").toFile().apply { deleteOnExit() },
            attempts = 3,
            retryDelayMs = 0,
            connectionFactory = ReceiptConnectionFactory {
                opened += 1
                object : ReceiptConnection {
                    override val responseCode = 200
                    override val contentLength = -1L
                    override val inputStream = object : InputStream() {
                        override fun read(): Int {
                            readStarted.countDown()
                            released.await()
                            throw IOException("disconnected")
                        }
                    }

                    override fun disconnect() {
                        released.countDown()
                    }
                }
            },
        )
        val failure = AtomicReference<Throwable>()
        val thread = Thread {
            try {
                downloader.download(URI("https://shop.test/receipt.png"))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        thread.start()
        assertTrue(readStarted.await(1, TimeUnit.SECONDS))

        downloader.cancelActive()
        thread.join(1_000)

        assertTrue(failure.get() is InterruptedException)
        assertEquals(1, opened)
    }
}
