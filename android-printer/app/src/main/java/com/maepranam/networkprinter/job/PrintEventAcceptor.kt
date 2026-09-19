package com.maepranam.networkprinter.job

import com.maepranam.networkprinter.runtime.LogLevel
import com.maepranam.networkprinter.runtime.PrinterRuntime
import java.net.URI

enum class EventAcceptance {
    ACCEPTED,
    INVALID,
    QUEUE_FULL,
}

class PrintEventAcceptor(
    private val parser: PrintJobParser,
    private val queue: SerialPrintQueue,
    private val runtime: PrinterRuntime,
) {
    fun accept(json: String): EventAcceptance = parser.parse(json).fold(
        onSuccess = { receipt ->
            if (queue.offer(receipt)) {
                runtime.appendLog("Receipt queued: ${safeDisplayUrl(receipt.source)}")
                EventAcceptance.ACCEPTED
            } else {
                runtime.appendLog("Print queue is full; receipt was skipped", LogLevel.WARNING)
                EventAcceptance.QUEUE_FULL
            }
        },
        onFailure = { failure ->
            runtime.appendLog(failure.message ?: "Invalid print event", LogLevel.WARNING)
            EventAcceptance.INVALID
        },
    )

    private fun safeDisplayUrl(source: URI): String {
        val host = source.host?.let { if (':' in it) "[$it]" else it }.orEmpty()
        val port = if (source.port >= 0) ":${source.port}" else ""
        return "${source.scheme}://$host$port${source.rawPath.orEmpty()}"
    }
}
