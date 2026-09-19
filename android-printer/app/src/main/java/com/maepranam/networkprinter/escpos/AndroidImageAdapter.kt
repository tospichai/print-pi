package com.maepranam.networkprinter.escpos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class AndroidImageAdapter {
    @Throws(IOException::class)
    fun decode(file: File): RasterImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Downloaded receipt is not a valid image")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("Downloaded receipt could not be decoded")
        return try {
            toRaster(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun createTestImage(): RasterImage {
        val bitmap = Bitmap.createBitmap(PRINTER_WIDTH_DOTS, TEST_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
            }
            paint.textSize = 42f
            paint.isFakeBoldText = true
            canvas.drawText("MAE PRA NAM", PRINTER_WIDTH_DOTS / 2f, 52f, paint)
            paint.textSize = 28f
            paint.isFakeBoldText = false
            canvas.drawText("ANDROID PRINTER TEST", PRINTER_WIDTH_DOTS / 2f, 94f, paint)
            paint.textSize = 22f
            canvas.drawText(
                LocalDateTime.now().format(TEST_DATE_FORMAT),
                PRINTER_WIDTH_DOTS / 2f,
                130f,
                paint,
            )
            canvas.drawText("576 dots", PRINTER_WIDTH_DOTS / 2f, 162f, paint)
            paint.style = Paint.Style.FILL
            for (x in 0 until PRINTER_WIDTH_DOTS step CHECKER_SIZE) {
                if ((x / CHECKER_SIZE) % 2 == 0) {
                    canvas.drawRect(x.toFloat(), 184f, (x + CHECKER_SIZE).toFloat(), 210f, paint)
                }
            }
            toRaster(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun toRaster(bitmap: Bitmap): RasterImage {
        val scaled = if (bitmap.width > PRINTER_WIDTH_DOTS) {
            val scale = PRINTER_WIDTH_DOTS.toDouble() / bitmap.width
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, PRINTER_WIDTH_DOTS, height, true)
        } else {
            bitmap
        }
        return try {
            val width = scaled.width
            val height = scaled.height
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            RasterImage(width, height, pixels)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (
            width / sample > TARGET_DECODE_WIDTH ||
            (width.toLong() / sample) * (height.toLong() / sample) > MAX_DECODE_PIXELS
        ) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val PRINTER_WIDTH_DOTS = 576
        const val TARGET_DECODE_WIDTH = 1_152
        const val MAX_DECODE_PIXELS = 8_000_000L
        const val TEST_IMAGE_HEIGHT = 220
        const val CHECKER_SIZE = 24
        val TEST_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
