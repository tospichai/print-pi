package com.maepranam.networkprinter.escpos

import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun extremeTallImageIsSampledBelowPixelBudget() {
        val sample = ImageSampling.sampleSize(
            width = 1,
            height = 100_000_000,
            maxWidth = 1_152,
            maxPixels = 8_000_000,
        )

        val sampledWidth = ImageSampling.ceilDiv(1, sample)
        val sampledHeight = ImageSampling.ceilDiv(100_000_000, sample)
        assertTrue(sampledWidth.toLong() * sampledHeight <= 8_000_000)
    }
}
