package com.maepranam.networkprinter.runtime

import java.time.Instant
import java.util.ArrayDeque

enum class PrinterStatus {
    STOPPED,
    CONNECTING,
    READY,
    PRINTING,
    ERROR,
}

enum class LogLevel {
    INFO,
    WARNING,
    ERROR,
}

data class LogEntry(
    val time: Instant,
    val level: LogLevel,
    val message: String,
)

data class PrinterSnapshot(
    val status: PrinterStatus,
    val logs: List<LogEntry>,
)

class PrinterRuntime(
    private val maxLogs: Int = DEFAULT_MAX_LOGS,
) {
    private val lock = Any()
    private val logs = ArrayDeque<LogEntry>()
    private var status = PrinterStatus.STOPPED

    init {
        require(maxLogs > 0) { "Log limit must be positive" }
    }

    fun setStatus(status: PrinterStatus) {
        synchronized(lock) {
            this.status = status
        }
    }

    fun compareAndSetStatus(expected: PrinterStatus, newStatus: PrinterStatus): Boolean = synchronized(lock) {
        if (status != expected) return@synchronized false
        status = newStatus
        true
    }

    fun appendLog(message: String, level: LogLevel = LogLevel.INFO) {
        synchronized(lock) {
            logs.addLast(LogEntry(Instant.now(), level, message))
            while (logs.size > maxLogs) logs.removeFirst()
        }
    }

    fun snapshot(): PrinterSnapshot = synchronized(lock) {
        PrinterSnapshot(status, logs.toList())
    }

    companion object {
        private const val DEFAULT_MAX_LOGS = 100
        val shared = PrinterRuntime()
    }
}
