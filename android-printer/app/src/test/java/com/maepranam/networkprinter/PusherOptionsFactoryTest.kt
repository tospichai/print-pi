package com.maepranam.networkprinter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PusherOptionsFactoryTest {
    @Test
    fun reconnectsForLongOutagesInsteadOfStoppingAfterSdkDefault() {
        val options = PusherOptionsFactory.create("ap1")

        assertTrue(options.isUseTLS)
        assertEquals(Int.MAX_VALUE, options.maxReconnectionAttempts)
        assertEquals(30, options.maxReconnectGapInSeconds)
    }
}
