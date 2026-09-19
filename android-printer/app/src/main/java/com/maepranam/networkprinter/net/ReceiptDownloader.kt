package com.maepranam.networkprinter.net

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI

class DownloadPolicy(
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    init {
        require(maxBytes > 0) { "Download limit must be positive" }
    }

    @Throws(IOException::class)
    fun validateLength(contentLength: Long) {
        if (contentLength > maxBytes) {
            throw IOException("Receipt image exceeds $maxBytes bytes")
        }
    }

    @Throws(IOException::class)
    fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long = this.maxBytes,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IOException("Receipt image exceeds $maxBytes bytes")
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 10L * 1024 * 1024
    }
}

class ReceiptDownloader(
    private val cacheDir: File,
    private val policy: DownloadPolicy = DownloadPolicy(),
    private val attempts: Int = DEFAULT_ATTEMPTS,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
) {
    init {
        require(attempts > 0) { "Attempts must be positive" }
        require(retryDelayMs >= 0) { "Retry delay must not be negative" }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun download(source: URI): File {
        var finalFailure: IOException? = null
        repeat(attempts) { attempt ->
            try {
                return downloadOnce(source)
            } catch (failure: IOException) {
                finalFailure = failure
                if (attempt < attempts - 1) sleepBeforeRetry()
            }
        }
        throw finalFailure ?: IOException("Receipt download failed")
    }

    private fun downloadOnce(source: URI): File {
        val directory = File(cacheDir, RECEIPT_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create receipt cache")
        }
        val target = File.createTempFile("receipt-", ".part", directory)
        var connection: HttpURLConnection? = null
        try {
            connection = (source.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Receipt download returned HTTP $responseCode")
            policy.validateLength(connection.contentLengthLong)
            connection.inputStream.use { input ->
                target.outputStream().buffered().use { output ->
                    policy.copyLimited(input, output)
                }
            }
            return target
        } catch (failure: Exception) {
            target.delete()
            if (failure is IOException) throw failure
            throw IOException("Receipt download failed", failure)
        } finally {
            connection?.disconnect()
        }
    }

    private fun sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    private companion object {
        const val RECEIPT_DIRECTORY = "receipts"
        const val DEFAULT_ATTEMPTS = 3
        const val DEFAULT_RETRY_DELAY_MS = 1_000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
