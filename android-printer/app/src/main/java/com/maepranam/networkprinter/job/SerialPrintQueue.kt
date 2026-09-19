package com.maepranam.networkprinter.job

import java.util.concurrent.ArrayBlockingQueue

class SerialPrintQueue(capacity: Int) {
    private val queue = ArrayBlockingQueue<PrintRequest>(capacity)

    fun offer(request: PrintRequest): Boolean = queue.offer(request)

    @Throws(InterruptedException::class)
    fun take(): PrintRequest = queue.take()

    fun size(): Int = queue.size

    fun clear() {
        queue.clear()
    }
}
