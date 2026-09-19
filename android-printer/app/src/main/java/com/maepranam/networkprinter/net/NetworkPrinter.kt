package com.maepranam.networkprinter.net

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

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
) {
    init {
        require(attempts > 0) { "Attempts must be positive" }
        require(retryDelayMs >= 0) { "Retry delay must not be negative" }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun send(host: String, port: Int, bytes: ByteArray) {
        var finalFailure: IOException? = null

        repeat(attempts) { attempt ->
            try {
                connectionFactory.create().use { connection ->
                    connection.connect(host, port, CONNECT_TIMEOUT_MS)
                    connection.write(bytes)
                }
                return
            } catch (failure: IOException) {
                finalFailure = failure
                if (attempt < attempts - 1) sleepBeforeRetry()
            }
        }

        throw finalFailure ?: IOException("Printer connection failed")
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
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}

private class JavaSocketConnection : SocketConnection {
    private val socket = Socket()

    override fun connect(host: String, port: Int, timeoutMs: Int) {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = WRITE_TIMEOUT_MS
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

    private companion object {
        const val WRITE_TIMEOUT_MS = 10_000
    }
}
