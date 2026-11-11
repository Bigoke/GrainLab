package com.bigoke.grainlab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bigoke.grainlab.camera.NightStacker
import com.bigoke.grainlab.data.EditHistory
import com.bigoke.grainlab.data.Settings
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.io.BitmapLoader
import com.bigoke.grainlab.io.ExifWriter
import com.bigoke.grainlab.io.ExportFormat
import com.bigoke.grainlab.io.MediaSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max

/**
 * Develops captured photos in the background so the shutter is ready again immediately.
 * Shots are processed one at a time to keep memory use low.
 */
object PhotoDeveloper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _last = MutableStateFlow<Pair<Uri, Bitmap>?>(null)
    val last: StateFlow<Pair<Uri, Bitmap>?> = _last.asStateFlow()

    private val _errors = MutableStateFlow(0)
    val errors: StateFlow<Int> = _errors.asStateFlow()

    private fun enqueue(block: suspend () -> Pair<Uri, Bitmap>?) {
        _pending.value += 1
        scope.launch {
            lock.withLock {
                val ok = runCatching { block() }.getOrNull()
                if (ok != null) _last.value = ok else _errors.value += 1
            }
            _pending.value -= 1
        }
    }

    fun submit(
        context: Context,
        jpeg: ByteArray,
        rotation: Int,
        params: EditParams,
        mirror: Boolean,
        keepOriginal: Boolean,
        location: Location? = null,
    ) {
        val app = context.applicationContext
        enqueue {
            val bmp = decode(jpeg, rotation, mirror) ?: return@enqueue null
            finish(app, bmp, ExifWriter.fromJpeg(jpeg), params, keepOriginal, location)
        }
    }

    /** Night mode: align and average several frames, then develop the result. */
    fun submitNight(
        context: Context,
        frames: List<Pair<ByteArray, Int>>,
        params: EditParams,
        mirror: Boolean,
        keepOriginal: Boolean,
        location: Location? = null,
    ) {
        val app = context.applicationContext
        enqueue {
            val bmp = NightStacker.stack(frames, mirror) ?: return@enqueue null
            finish(app, bmp, frames.firstOrNull()?.let { ExifWriter.fromJpeg(it.first) }, params, keepOriginal, location)
        }
    }

    private fun decode(jpeg: ByteArray, rotation: Int, mirror: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 8192) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            jpeg, 0, jpeg.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        var bmp = decoded
        if (rotation != 0 || mirror) {
            val m = Matrix()
            if (rotation != 0) m.postRotate(rotation.toFloat())
            if (mirror) m.postScale(-1f, 1f)
            bmp = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (bmp !== decoded) decoded.recycle()
        }
        return BitmapLoader.scaleToMax(bmp, 8192)
    }

    private suspend fun finish(
        context: Context,
        bmp: Bitmap,
        exif: ExifInterface?,
        p: EditParams,
        keepOriginal: Boolean,
        location: Location?,
    ): Pair<Uri, Bitmap>? {
        val keepLoc = Settings.keepLocation
        val ts = MediaSaver.timestamp()
        if (keepOriginal) {
            MediaSaver.saveBitmap(context, bmp, ExportFormat.JPEG, 95, "GrainLab_${ts}_original")?.let {
                ExifWriter.write(context, it, "image/jpeg", exif, keepLoc, location, bmp.width, bmp.height)
            }
        }
        val seed = abs(ts.hashCode() % 1000) / 1000f
        val out = if (p.isIdentity()) bmp else Pipeline.render(bmp, p, useCache = false, seed = seed, consumeSource = true)
        val uri = MediaSaver.saveBitmap(context, out, ExportFormat.JPEG, 94, "GrainLab_$ts") ?: return null
        ExifWriter.write(context, uri, "image/jpeg", exif, keepLoc, location, out.width, out.height)
        EditHistory.recordOutput(context, uri, null, p)
        val thumb = BitmapLoader.scaleToMax(out, 256).let { if (it === out) out.copy(Bitmap.Config.ARGB_8888, false) else it }
        out.recycle()
        if (!bmp.isRecycled) bmp.recycle()
        return uri to thumb
    }
}
