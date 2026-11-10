package com.bigoke.grainlab.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.Pipeline
import com.bigoke.grainlab.io.BitmapLoader
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

    fun submit(context: Context, jpeg: ByteArray, rotation: Int, params: EditParams, mirror: Boolean, keepOriginal: Boolean) {
        val app = context.applicationContext
        _pending.value += 1
        scope.launch {
            lock.withLock {
                val ok = runCatching { develop(app, jpeg, rotation, params, mirror, keepOriginal) }.getOrNull()
                if (ok != null) _last.value = ok else _errors.value += 1
            }
            _pending.value -= 1
        }
    }

    private suspend fun develop(
        context: Context,
        jpeg: ByteArray,
        rotation: Int,
        p: EditParams,
        mirror: Boolean,
        keepOriginal: Boolean,
    ): Pair<Uri, Bitmap>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 4096) sample *= 2
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
        bmp = BitmapLoader.scaleToMax(bmp, 4096)

        val ts = MediaSaver.timestamp()
        if (keepOriginal) MediaSaver.saveBitmap(context, bmp, ExportFormat.JPEG, 95, "GrainLab_${ts}_original")
        val seed = abs(ts.hashCode() % 1000) / 1000f
        val out = if (p.isIdentity()) bmp else Pipeline.render(bmp, p, useCache = false, seed = seed)
        val uri = MediaSaver.saveBitmap(context, out, ExportFormat.JPEG, 94, "GrainLab_$ts") ?: return null
        val thumb = BitmapLoader.scaleToMax(out, 256).let { if (it === out) out.copy(Bitmap.Config.ARGB_8888, false) else it }
        if (out !== bmp) out.recycle()
        bmp.recycle()
        return uri to thumb
    }
}
