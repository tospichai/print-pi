package com.maepranam.networkprinter.net

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
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
}
