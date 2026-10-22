package za.co.devwell.data.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Turns a picked photo into a small square JPEG that is cheap to upload and store. */
object AvatarImage {
    private const val SIZE = 512
    private const val QUALITY = 85

    fun prepare(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        return try {

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null

            // Phone cameras often store photos sideways with a rotation tag.
            val degrees = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val upright = if (degrees == 0f) decoded else Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(degrees) }, true,
            )

            // Centre-crop to a square, then shrink to at most 512 px.
            val side = min(upright.width, upright.height)
            val square = Bitmap.createBitmap(
                upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side,
            )
            val finalSize = min(side, SIZE)
            val scaled = if (finalSize == side) square
            else Bitmap.createScaledBitmap(square, finalSize, finalSize, true)

            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }
}