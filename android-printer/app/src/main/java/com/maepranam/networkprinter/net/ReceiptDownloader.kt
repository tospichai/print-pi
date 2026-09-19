package com.maepranam.networkprinter.net

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface ReceiptConnection {
    val responseCode: Int
    val contentLength: Long
    val inputStream: InputStream
    fun disconnect()
}

fun interface ReceiptConnectionFactory {
    fun open(source: URI): ReceiptConnection
}

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
    private val connectionFactory: ReceiptConnectionFactory = ReceiptConnectionFactory { source ->
        JavaReceiptConnection(source)
    },
) {
    private val cancellationGeneration = AtomicLong()
    private val activeConnection = AtomicReference<ReceiptConnection?>()

    init {
        require(attempts > 0) { "Attempts must be positive" }
        require(retryDelayMs >= 0) { "Retry delay must not be negative" }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun download(source: URI): File {
        val generation = cancellationGeneration.get()
        var finalFailure: IOException? = null
        repeat(attempts) { attempt ->
            ensureNotCancelled(generation)
            try {
                return downloadOnce(source, generation)
            } catch (failure: IOException) {
                ensureNotCancelled(generation)
                finalFailure = failure
                if (attempt < attempts - 1) sleepBeforeRetry()
            }
        }
        throw finalFailure ?: IOException("Receipt download failed")
    }

    fun cancelActive() {
        cancellationGeneration.incrementAndGet()
        activeConnection.getAndSet(null)?.disconnect()
    }

    private fun downloadOnce(source: URI, generation: Long): File {
        val directory = File(cacheDir, RECEIPT_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create receipt cache")
        }
        val target = File.createTempFile("receipt-", ".part", directory)
        var connection: ReceiptConnection? = null
        try {
            connection = connectionFactory.open(source)
            if (!activeConnection.compareAndSet(null, connection)) {
                connection.disconnect()
                throw IOException("Another receipt download is active")
            }
            ensureNotCancelled(generation)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Receipt download returned HTTP $responseCode")
            policy.validateLength(connection.contentLength)
            connection.inputStream.use { input ->
                target.outputStream().buffered().use { output ->
                    policy.copyLimited(input, output)
                }
            }
            ensureNotCancelled(generation)
            return target
        } catch (failure: Exception) {
            target.delete()
            ensureNotCancelled(generation)
            if (failure is InterruptedException) throw failure
            if (failure is IOException) throw failure
            throw IOException("Receipt download failed", failure)
        } finally {
            connection?.let { active ->
                if (activeConnection.compareAndSet(active, null)) active.disconnect()
            }
        }
    }

    private fun ensureNotCancelled(generation: Long) {
        if (generation != cancellationGeneration.get()) throw InterruptedException("Download cancelled")
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
    }
}

private class JavaReceiptConnection(source: URI) : ReceiptConnection {
    private val connection = (source.toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        useCaches = false
    }

    override val responseCode: Int get() = connection.responseCode
    override val contentLength: Long get() = connection.contentLengthLong
    override val inputStream: InputStream get() = connection.inputStream
    override fun disconnect() = connection.disconnect()
}
