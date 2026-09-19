package com.maepranam.networkprinter.escpos

class EscPosEncoder(
    private val threshold: Int = DEFAULT_THRESHOLD,
) {
    fun encode(image: RasterImage): ByteArray {
        val rowBytes = (image.width + 7) / 8
        val body = ByteArray(rowBytes * image.height)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (isBlack(image.argb[y * image.width + x])) {
                    val index = y * rowBytes + x / 8
                    body[index] = (body[index].toInt() or (0x80 ushr (x % 8))).toByte()
                }
            }
        }

        val header = byteArrayOf(
            ESC, INITIALIZE,
            ESC, ALIGN, ALIGN_CENTER,
            GS, RASTER, RASTER_MODE, RASTER_NORMAL,
            (rowBytes and 0xff).toByte(),
            (rowBytes ushr 8).toByte(),
            (image.height and 0xff).toByte(),
            (image.height ushr 8).toByte(),
        )
        val footer = byteArrayOf(
            LINE_FEED, LINE_FEED, LINE_FEED, LINE_FEED,
            GS, CUT, CUT_FULL,
        )
        return header + body + footer
    }

    private fun isBlack(color: Int): Boolean {
        val alpha = color ushr 24 and 0xff
        if (alpha < MIN_PRINTABLE_ALPHA) return false

        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        val luminance = (red * 299 + green * 587 + blue * 114) / 1000
        return luminance < threshold
    }

    private companion object {
        const val DEFAULT_THRESHOLD = 160
        const val MIN_PRINTABLE_ALPHA = 128
        const val ESC: Byte = 0x1b
        const val GS: Byte = 0x1d
        const val INITIALIZE: Byte = 0x40
        const val ALIGN: Byte = 0x61
        const val ALIGN_CENTER: Byte = 0x01
        const val RASTER: Byte = 0x76
        const val RASTER_MODE: Byte = 0x30
        const val RASTER_NORMAL: Byte = 0x00
        const val LINE_FEED: Byte = 0x0a
        const val CUT: Byte = 0x56
        const val CUT_FULL: Byte = 0x00
    }
}
