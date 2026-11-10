package com.bigoke.grainlab.edit

import android.graphics.Bitmap
import com.bigoke.grainlab.gl.GlProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Geometry -> GPU (lookup table + effects) -> borders and date stamp. */
object Pipeline {
    private val lock = Any()
    private var cachedKey: String? = null
    private var cachedLut: ByteArray? = null

    fun lutFor(p: EditParams, useCache: Boolean = true): ByteArray {
        val key = p.lutSignature()
        if (useCache) synchronized(lock) {
            if (key == cachedKey) cachedLut?.let { return it }
        }
        val lut = LutBuilder.build(p)
        if (useCache) synchronized(lock) {
            cachedKey = key
            cachedLut = lut
        }
        return lut
    }

    suspend fun render(
        src: Bitmap,
        p: EditParams,
        finishing: Boolean = true,
        useCache: Boolean = true,
        seed: Float = 0.37f,
    ): Bitmap {
        val geo = withContext(Dispatchers.Default) { Geometry.apply(src, p) }
        val lut = withContext(Dispatchers.Default) { lutFor(p, useCache) }
        val out = GlProcessor.process(geo, p, lut, seed)
        if (geo !== src) geo.recycle()
        return if (finishing) withContext(Dispatchers.Default) { Finishing.apply(out, p) } else out
    }
}
