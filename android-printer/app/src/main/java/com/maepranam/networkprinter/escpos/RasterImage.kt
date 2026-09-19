package com.maepranam.networkprinter.escpos

data class RasterImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0) { "Image width must be positive" }
        require(height > 0) { "Image height must be positive" }
        require(argb.size == width * height) { "Pixel count must match image dimensions" }
    }

    override fun equals(other: Any?): Boolean =
        other is RasterImage &&
            width == other.width &&
            height == other.height &&
            argb.contentEquals(other.argb)

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + argb.contentHashCode()
        return result
    }
}
