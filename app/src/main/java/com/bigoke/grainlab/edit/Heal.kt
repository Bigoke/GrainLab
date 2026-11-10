package com.bigoke.grainlab.edit

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spot healing: each spot is covered with a nearby patch whose surrounding ring matches best,
 * brightness-matched and feathered into place.
 */
object Heal {
    fun apply(src: Bitmap, spots: List<Spot>): Bitmap {
        if (spots.isEmpty()) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val side = min(w, h).toFloat()
        for (s in spots) healOne(px, w, h, s.x * w, s.y * h, (s.r * side).coerceAtLeast(2f))
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun lum(c: Int) = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    private fun healOne(px: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float) {
        val outer = r * 1.35f
        // Sample points on the ring around the spot.
        val ring = ArrayList<Pair<Int, Int>>()
        val steps = (outer * 2f).toInt().coerceIn(24, 160)
        for (i in 0 until steps) {
            val a = i * 2.0 * Math.PI / steps
            for (rr in listOf(r * 1.05f, outer)) {
                ring.add(((cx + cos(a) * rr).roundToInt()) to ((cy + sin(a) * rr).roundToInt()))
            }
        }
        fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h

        // Pick the source offset (16 directions, two distances) whose ring best matches.
        var bestDx = 0
        var bestDy = 0
        var bestCost = Float.MAX_VALUE
        var bestShift = 0f
        for (dist in listOf(2.4f, 3.2f)) for (k in 0 until 16) {
            val a = k * 2.0 * Math.PI / 16
            val dx = (cos(a) * r * dist).roundToInt()
            val dy = (sin(a) * r * dist).roundToInt()
            var cost = 0f
            var n = 0
            var tSum = 0f
            var sSum = 0f
            for ((x, y) in ring) {
                if (!inside(x, y) || !inside(x + dx, y + dy)) continue
                val t = px[y * w + x]
                val s = px[(y + dy) * w + (x + dx)]
                cost += kotlin.math.abs(Color.red(t) - Color.red(s)) + kotlin.math.abs(Color.green(t) - Color.green(s)) +
                    kotlin.math.abs(Color.blue(t) - Color.blue(s))
                tSum += lum(t); sSum += lum(s)
                n++
            }
            if (n < ring.size / 2) continue
            val c = cost / n
            if (c < bestCost) {
                bestCost = c; bestDx = dx; bestDy = dy
                bestShift = (tSum - sSum) / n
            }
        }
        if (bestCost == Float.MAX_VALUE) return

        val x0 = (cx - outer).toInt().coerceAtLeast(0)
        val x1 = (cx + outer).toInt().coerceAtMost(w - 1)
        val y0 = (cy - outer).toInt().coerceAtLeast(0)
        val y1 = (cy + outer).toInt().coerceAtMost(h - 1)
        val copy = IntArray((x1 - x0 + 1) * (y1 - y0 + 1))
        for (y in y0..y1) for (x in x0..x1) copy[(y - y0) * (x1 - x0 + 1) + (x - x0)] = px[y * w + x]
        val shift = bestShift.roundToInt()
        for (y in y0..y1) for (x in x0..x1) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            if (d > outer) continue
            val sx = x + bestDx
            val sy = y + bestDy
            if (!inside(sx, sy)) continue
            val t = ((outer - d) / (outer - r * 0.8f)).coerceIn(0f, 1f)
            val alpha = t * t * (3 - 2 * t)
            val s = if (sx in x0..x1 && sy in y0..y1) copy[(sy - y0) * (x1 - x0 + 1) + (sx - x0)] else px[sy * w + sx]
            val dst = px[y * w + x]
            fun mix(a: Int, b: Int) = (a + (b + shift - a) * alpha).roundToInt().coerceIn(0, 255)
            px[y * w + x] = Color.rgb(
                mix(Color.red(dst), Color.red(s)),
                mix(Color.green(dst), Color.green(s)),
                mix(Color.blue(dst), Color.blue(s)),
            )
        }
    }
}
