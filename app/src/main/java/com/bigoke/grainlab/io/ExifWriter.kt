package com.bigoke.grainlab.io

import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies camera details (make, model, lens, exposure, date) into a saved JPEG so other apps
 * show them. Location is only written when the user has turned it on.
 */
object ExifWriter {
    private val cameraTags = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
        ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_EXPOSURE_PROGRAM,
    )
    private val gpsTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    )

    fun fromJpeg(bytes: ByteArray): ExifInterface? =
        runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()

    fun fromUri(context: Context, uri: Uri): ExifInterface? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()

    /** Writes tags into the JPEG at [dest]. Safe to call for any format; non-JPEG is skipped. */
    fun write(
        context: Context,
        dest: Uri,
        mime: String,
        source: ExifInterface?,
        keepLocation: Boolean,
        location: Location? = null,
        width: Int = 0,
        height: Int = 0,
    ) {
        if (mime != "image/jpeg") return
        runCatching {
            context.contentResolver.openFileDescriptor(dest, "rw")?.use { pfd ->
                val e = ExifInterface(pfd.fileDescriptor)
                if (source != null) {
                    for (t in cameraTags) source.getAttribute(t)?.let { e.setAttribute(t, it) }
                    if (keepLocation) for (t in gpsTags) source.getAttribute(t)?.let { e.setAttribute(t, it) }
                }
                if (keepLocation && location != null) e.setGpsInfo(location)
                if (e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                    val now = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
                    e.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, now)
                }
                e.setAttribute(ExifInterface.TAG_SOFTWARE, "GrainLab")
                e.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                if (width > 0 && height > 0) {
                    e.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
                    e.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
                }
                e.saveAttributes()
            }
        }
    }
}
