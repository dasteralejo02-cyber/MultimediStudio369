package com.multimediastudio369.photo

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProcessorTest {

    private fun solidBitmap(color: Int, w: Int = 4, h: Int = 4): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    @Test
    fun blackImageHistogram() {
        val bmp = solidBitmap(Color.BLACK)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        assertEquals(16, hist[0])
        assertEquals(0, hist.sum() - hist[0])
        bmp.recycle()
    }

    @Test
    fun whiteImageHistogram() {
        val bmp = solidBitmap(Color.WHITE)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        assertEquals(16, hist[255])
        bmp.recycle()
    }

    @Test
    fun gray50Histogram() {
        val bmp = solidBitmap(Color.rgb(128, 128, 128))
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        // 0.299*128 + 0.587*128 + 0.114*128 ≈ 128
        assertTrue(hist[128] > 0)
        bmp.recycle()
    }

    @Test
    fun primaryRedHistogram() {
        val bmp = solidBitmap(Color.RED)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        // 0.299*255 ≈ 76
        assertTrue(hist[76] > 0 || hist[77] > 0)
        bmp.recycle()
    }

    @Test
    fun primaryGreenHistogram() {
        val bmp = solidBitmap(Color.GREEN)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        // 0.587*255 ≈ 150
        assertTrue(hist[149] > 0 || hist[150] > 0)
        bmp.recycle()
    }

    @Test
    fun primaryBlueHistogram() {
        val bmp = solidBitmap(Color.BLUE)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        // 0.114*255 ≈ 29
        assertTrue(hist[29] > 0 || hist[30] > 0)
        bmp.recycle()
    }

    @Test
    fun histogramHas256Bins() {
        val bmp = solidBitmap(Color.GRAY)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        assertEquals(256, hist.size)
        bmp.recycle()
    }

    @Test
    fun differentDimensions() {
        val bmp = solidBitmap(Color.BLACK, 8, 2)
        val hist = ImageProcessor.computeLuminanceHistogram(bmp)
        assertEquals(16, hist[0])
        bmp.recycle()
    }

    @Test
    fun adjustmentParamsDefaults() {
        val p = AdjustmentParams()
        assertEquals(0f, p.brightness, 0.001f)
        assertEquals(1f, p.contrast, 0.001f)
        assertEquals(1f, p.saturation, 0.001f)
    }

    @Test
    fun undoParameterRestore() {
        var params = AdjustmentParams(brightness = 0.5f, contrast = 1.2f)
        val history = mutableListOf<AdjustmentParams>()
        history.add(params)
        params = AdjustmentParams() // reset
        assertEquals(0f, params.brightness, 0.001f)
        params = history.removeLast()
        assertEquals(0.5f, params.brightness, 0.001f)
    }
}
