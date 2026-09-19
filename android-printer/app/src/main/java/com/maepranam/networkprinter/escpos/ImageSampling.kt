package com.maepranam.networkprinter.escpos

object ImageSampling {
    fun sampleSize(width: Int, height: Int, maxWidth: Int, maxPixels: Long): Int {
        require(width > 0 && height > 0)
        require(maxWidth > 0 && maxPixels > 0)
        var sample = 1
        while (true) {
            val sampledWidth = ceilDiv(width, sample)
            val sampledHeight = ceilDiv(height, sample)
            if (sampledWidth <= maxWidth && sampledWidth.toLong() * sampledHeight <= maxPixels) {
                return sample
            }
            if (sample > Int.MAX_VALUE / 2) return Int.MAX_VALUE
            sample *= 2
        }
    }

    fun ceilDiv(value: Int, divisor: Int): Int {
        require(value >= 0 && divisor > 0)
        return ((value.toLong() + divisor - 1) / divisor).toInt()
    }
}
