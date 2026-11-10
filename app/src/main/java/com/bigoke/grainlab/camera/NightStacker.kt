package com.bigoke.grainlab.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.max

/**
 * Night mode: several handheld frames are aligned and averaged, which cuts noise roughly by the
 * square root of the frame count. Pixels that moved (people, cars) fall back to the sharpest frame.
 */
object NightStacker {

    private fun decode(jpeg: ByteArray, rotation: Int, mirror: Boolean, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val d = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        if (rotation == 0 && !mirror) return d
        val m = Matrix()
        if (rotation != 0) m.postRotate(rotation.toFloat())
        if (mirror) m.postScale(-1f, 1f)
        val r = Bitmap.createBitmap(d, 0, 0, d.width, d.height, m, true)
        if (r !== d) d.recycle()
        return r
    }

    private fun gray(px: IntArray): FloatArray = FloatArray(px.size) { i ->
        val c = px[i]
        0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
    }

    /** Integer shift (dx, dy) that best lines [frame] up with [ref], searched on a small copy. */
    private fun align(ref: FloatArray, frame: FloatArray, w: Int, h: Int, radius: Int): Pair<Int, Int> {
        var best = Float.MAX_VALUE
        var bx = 0; var by = 0
        val x0 = w / 4; val x1 = 3 * w / 4; val y0 = h / 4; val y1 = 3 * h / 4
        for (dy in -radius..radius) for (dx in -radius..radius) {
            var sad = 0f
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    sad += abs(ref[y * w + x] - frame[(y + dy) * w + (x + dx)])
                    x += 2
                }
                y += 2
            }
            if (sad < best) { best = sad; bx = dx; by = dy }
        }
        return bx to by
    }

    fun stack(frames: List<Pair<ByteArray, Int>>, mirror: Boolean, maxSide: Int = 4096): Bitmap? {
        if (frames.isEmpty()) return null
        val refBmp = decode(frames[0].first, frames[0].second, mirror, maxSide) ?: return null
        val w = refBmp.width; val h = refBmp.height
        val refPx = IntArray(w * h)
        refBmp.getPixels(refPx, 0, w, 0, 0, w, h)

        // Small copies for alignment (1/8 size).
        val sw = max(16, w / 8); val sh = max(16, h / 8)
        fun small(b: Bitmap): FloatArray {
            val s = Bitmap.createScaledBitmap(b, sw, sh, true)
            val p = IntArray(sw * sh); s.getPixels(p, 0, sw, 0, 0, sw, sh)
            if (s !== b) s.recycle()
            return gray(p)
        }
        val refSmall = small(refBmp)
        refBmp.recycle()

        val sumR = IntArray(w * h); val sumG = IntArray(w * h); val sumB = IntArray(w * h); val count = IntArray(w * h)
        for (i in refPx.indices) {
            val c = refPx[i]
            sumR[i] = Color.red(c); sumG[i] = Color.green(c); sumB[i] = Color.blue(c); count[i] = 1
        }

        val px = IntArray(w * h)
        for (k in 1 until frames.size) {
            val b = decode(frames[k].first, frames[k].second, mirror, maxSide) ?: continue
            if (b.width != w || b.height != h) { b.recycle(); continue }
            val (sdx, sdy) = align(refSmall, small(b), sw, sh, minOf(6, sw / 4 - 1, sh / 4 - 1).coerceAtLeast(0))
            val dx = sdx * w / sw; val dy = sdy * h / sh
            b.getPixels(px, 0, w, 0, 0, w, h)
            b.recycle()
            for (y in 0 until h) {
                val sy = y + dy
                if (sy < 0 || sy >= h) continue
                for (x in 0 until w) {
                    val sx = x + dx
                    if (sx < 0 || sx >= w) continue
                    val c = px[sy * w + sx]
                    val i = y * w + x
                    val r0 = refPx[i]
                    // Ghost rejection: skip pixels that differ a lot from the reference frame.
                    val diff = abs(Color.red(c) - Color.red(r0)) + abs(Color.green(c) - Color.green(r0)) + abs(Color.blue(c) - Color.blue(r0))
                    if (diff > 90) continue
                    sumR[i] += Color.red(c); sumG[i] += Color.green(c); sumB[i] += Color.blue(c); count[i]++
                }
            }
        }
        for (i in refPx.indices) {
            val n = count[i]
            refPx[i] = Color.rgb(sumR[i] / n, sumG[i] / n, sumB[i] / n)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(refPx, 0, w, 0, 0, w, h)
        return out
    }
}
