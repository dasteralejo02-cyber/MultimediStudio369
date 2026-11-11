package com.multimediastudio369.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessorSampleSizeTest {
    @Test
    fun smallImageReturnsOne() {
        assertEquals(1, ImageProcessor.calculateInSampleSize(100, 100, 2048))
    }

    @Test
    fun largeImageScalesDown() {
        assertEquals(2, ImageProcessor.calculateInSampleSize(4000, 3000, 2048))
    }

    @Test
    fun extremeDimensionsUseLongArithmetic() {
        val sample = ImageProcessor.calculateInSampleSize(1_000_000, 500_000, 2048)
        assert(sample >= 512)
    }
}
