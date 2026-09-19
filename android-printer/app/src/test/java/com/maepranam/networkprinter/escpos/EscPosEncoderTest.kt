package com.maepranam.networkprinter.escpos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosEncoderTest {
    private val encoder = EscPosEncoder(threshold = 160)

    @Test
    fun padsRowsWhoseWidthIsNotDivisibleByEight() {
        val black = 0xff000000.toInt()

        val bytes = encoder.encode(RasterImage(9, 1, IntArray(9) { black }))

        assertArrayEquals(
            byteArrayOf(
                0x1b, 0x40, 0x1b, 0x61, 0x01,
                0x1d, 0x76, 0x30, 0x00, 0x02, 0x00, 0x01, 0x00,
                0xff.toByte(), 0x80.toByte(),
                0x0a, 0x0a, 0x0a, 0x0a, 0x1d, 0x56, 0x00,
            ),
            bytes,
        )
    }

    @Test
    fun transparentPixelsPrintWhite() {
        val bytes = encoder.encode(RasterImage(8, 1, IntArray(8) { 0x00000000 }))

        assertEquals(0x00, bytes[13].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPixelCountThatDoesNotMatchDimensions() {
        RasterImage(2, 2, intArrayOf(0)).also(encoder::encode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDimensions() {
        RasterImage(0, 1, intArrayOf()).also(encoder::encode)
    }
}
