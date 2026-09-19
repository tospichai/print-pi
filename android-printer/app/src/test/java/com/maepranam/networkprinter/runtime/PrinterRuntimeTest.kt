package com.maepranam.networkprinter.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterRuntimeTest {
    @Test
    fun runtimeKeepsOnlyNewestOneHundredLogLines() {
        val runtime = PrinterRuntime(maxLogs = 100)
        repeat(105) { runtime.appendLog("line-$it") }

        val logs = runtime.snapshot().logs

        assertEquals(100, logs.size)
        assertEquals("line-5", logs.first().message)
        assertEquals("line-104", logs.last().message)
    }

    @Test
    fun snapshotContainsLatestStatus() {
        val runtime = PrinterRuntime()

        runtime.setStatus(PrinterStatus.PRINTING)

        assertEquals(PrinterStatus.PRINTING, runtime.snapshot().status)
    }
}
