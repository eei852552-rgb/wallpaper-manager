package com.wallpapermanager.app

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Compresses gallery images without decoding the original resolution into RAM. */
object ImageCompression {
    const val MAX_UPLOAD_BYTES = 15L * 1024L * 1024L
    private const val MAX_DIMENSION = 2560
    private const val JPEG_QUALITY = 88

    fun compressToJpeg(resolver: ContentResolver, source: Uri, output: File): Long {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot open selected image" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a readable image" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot reopen selected image" }
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("Could not decode selected image")
        try {
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) { "Image compression failed" }
            }
        } finally { bitmap.recycle() }
        require(output.length() <= MAX_UPLOAD_BYTES) { "Image is too large after compression (maximum 15 MB)" }
        return output.length()
    }

    fun decodeForWallpaper(bytes: ByteArray, maxDimension: Int = MAX_DIMENSION): Bitmap {
        require(bytes.size <= MAX_UPLOAD_BYTES) { "Wallpaper file exceeds the safe size limit" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid wallpaper image" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Could not decode wallpaper image")
    }

    private fun calculateSample(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxDimension) sample *= 2
        return sample
    }
}

fun safeTempImage(file: File): File {
    if (file.exists()) file.delete()
    file.parentFile?.mkdirs()
    return file
}
