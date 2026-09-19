package com.maepranam.networkprinter.net

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface SocketConnection : Closeable {
    fun connect(host: String, port: Int, timeoutMs: Int)

    fun write(bytes: ByteArray)
}

fun interface SocketConnectionFactory {
    fun create(): SocketConnection
}

class NetworkPrinter(
    private val connectionFactory: SocketConnectionFactory = SocketConnectionFactory { JavaSocketConnection() },
    private val attempts: Int = DEFAULT_ATTEMPTS,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val attemptTimeoutMs: Long = DEFAULT_ATTEMPT_TIMEOUT_MS,
) {
    private val cancellationGeneration = AtomicLong()
    private val activeConnection = AtomicReference<SocketConnection?>()

    init {
        require(attempts > 0) { "Attempts must be positive" }
        require(retryDelayMs >= 0) { "Retry delay must not be negative" }
        require(attemptTimeoutMs > 0) { "Attempt timeout must be positive" }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun send(host: String, port: Int, bytes: ByteArray) {
        val generation = cancellationGeneration.get()
        var finalFailure: IOException? = null

        repeat(attempts) { attempt ->
            ensureNotCancelled(generation)
            try {
                sendAttempt(host, port, bytes)
                ensureNotCancelled(generation)
                return
            } catch (failure: IOException) {
                ensureNotCancelled(generation)
                finalFailure = failure
                if (attempt < attempts - 1) sleepBeforeRetry()
            }
        }

        throw finalFailure ?: IOException("Printer connection failed")
    }

    fun cancelActive() {
        cancellationGeneration.incrementAndGet()
        activeConnection.getAndSet(null)?.closeQuietly()
    }

    private fun sendAttempt(host: String, port: Int, bytes: ByteArray) {
        val connection = connectionFactory.create()
        if (!activeConnection.compareAndSet(null, connection)) {
            connection.closeQuietly()
            throw IOException("Another printer connection is active")
        }
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "printer-socket-attempt").apply { isDaemon = true }
        }
        val future = executor.submit {
            connection.connect(host, port, CONNECT_TIMEOUT_MS)
            connection.write(bytes)
        }
        try {
            future.get(attemptTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            closeActive(connection)
            future.cancel(true)
            throw SocketTimeoutException("Printer attempt timed out after ${attemptTimeoutMs}ms")
        } catch (interrupted: InterruptedException) {
            closeActive(connection)
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            when (cause) {
                is IOException -> throw cause
                is InterruptedException -> throw cause
                else -> throw IOException("Printer connection failed", cause)
            }
        } finally {
            closeActive(connection)
            executor.shutdownNow()
        }
    }

    private fun closeActive(connection: SocketConnection) {
        if (activeConnection.compareAndSet(connection, null)) connection.closeQuietly()
    }

    private fun ensureNotCancelled(generation: Long) {
        if (generation != cancellationGeneration.get()) throw InterruptedException("Print cancelled")
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
        const val DEFAULT_ATTEMPTS = 3
        const val DEFAULT_RETRY_DELAY_MS = 1_000L
        const val DEFAULT_ATTEMPT_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}

private fun SocketConnection.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // The original transport failure is more useful than a close failure.
    }
}

private class JavaSocketConnection : SocketConnection {
    private val socket = Socket()

    override fun connect(host: String, port: Int, timeoutMs: Int) {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
    }

    override fun write(bytes: ByteArray) {
        socket.getOutputStream().apply {
            write(bytes)
            flush()
        }
    }

    override fun close() {
        socket.close()
    }
}
