package com.maepranam.networkprinter.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterConfigTest {
    @Test
    fun validConfigHasNoErrors() {
        assertTrue(
            PrinterConfig("key", "ap1", "192.168.1.50", 9100)
                .validate()
                .isEmpty(),
        )
    }

    @Test
    fun everyRequiredFieldAndPortAreValidated() {
        val errors = PrinterConfig(" ", "", " ", 70000).validate()

        assertEquals(
            listOf(
                "Pusher App Key is required",
                "Pusher Cluster is required",
                "Printer IP or hostname is required",
                "Printer port must be between 1 and 65535",
            ),
            errors,
        )
    }

    @Test
    fun invalidPortTextBecomesAValidationErrorWithoutThrowing() {
        val config = PrinterConfigDraft("key", "ap1", "printer.local", "not-a-number").toConfig()

        assertEquals(-1, config.printerPort)
        assertTrue(config.validate().contains("Printer port must be between 1 and 65535"))
    }
}
