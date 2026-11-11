package com.multimediastudio369.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import java.io.OutputStream

data class AdjustmentParams(
    val brightness: Float = 0f,      // -1..1
    val contrast: Float = 1f,        // 0.5..2
    val saturation: Float = 1f,      // 0..2
    val exposure: Float = 0f,        // -2..2
    val temperature: Float = 0f,     // -1..1 (cool to warm)
    val tint: Float = 0f             // -1..1 (green to magenta)
)

object ImageProcessor {

    fun applyAdjustments(
        context: Context,
        uri: Uri,
        params: AdjustmentParams,
        maxDimension: Int = 2048
    ): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                options.inJustDecodeBounds = false
                options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
                // Re-open stream because decodeStream consumed it
                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    val original = BitmapFactory.decodeStream(stream2, null, options) ?: return null
                    try {
                        applyToBitmap(original, params)
                    } finally {
                        if (!original.isRecycled) original.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Power-of-two inSampleSize guaranteeing
     * ceil(max(width,height) / sample) <= maxDimension for valid positive inputs.
     * Uses long arithmetic to avoid overflow on extreme dimensions.
     * Caps at a practical maximum to prevent infinite loops on pathological values.
     */
    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        val longest = maxOf(width.toLong(), height.toLong())
        var sample = 1
        val maxSample = 1 shl 30
        while (sample < maxSample && kotlin.math.ceil(longest.toDouble() / sample).toInt() > maxDimension) {
            sample = sample shl 1
        }
        return sample
    }

    private fun applyToBitmap(source: Bitmap, params: AdjustmentParams): Bitmap {
        val result = createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        // Brightness
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, params.brightness * 255,
            0f, 1f, 0f, 0f, params.brightness * 255,
            0f, 0f, 1f, 0f, params.brightness * 255,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(brightnessMatrix)

        // Contrast
        val contrast = params.contrast
        val scale = contrast
        val translate = (1f - contrast) * 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrastMatrix)

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(params.saturation)
        cm.postConcat(satMatrix)

        // Exposure (simple gain)
        val exp = params.exposure
        val gain = Math.pow(2.0, exp.toDouble()).toFloat()
        val exposureMatrix = ColorMatrix(floatArrayOf(
            gain, 0f, 0f, 0f, 0f,
            0f, gain, 0f, 0f, 0f,
            0f, 0f, gain, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(exposureMatrix)

        // Temperature / Tint (simplified)
        val temp = params.temperature
        val tint = params.tint
        val tempMatrix = ColorMatrix(floatArrayOf(
            1f + temp * 0.2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - temp * 0.2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tempMatrix)

        val tintMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f + tint * 0.1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tintMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun exportBitmap(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MultimediaStudio369")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            val stream = resolver.openOutputStream(uri) ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            stream.use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                if (!success) {
                    resolver.delete(uri, null, null)
                    return null
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                val updated = resolver.update(uri, values, null, null)
                if (updated <= 0) {
                    resolver.delete(uri, null, null)
                    return null
                }
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Computes a simple luminance histogram (256 bins). Real pixel walk. */
    fun computeLuminanceHistogram(bitmap: Bitmap): IntArray {
        val hist = IntArray(256)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            hist[lum]++
        }
        return hist
    }
}
