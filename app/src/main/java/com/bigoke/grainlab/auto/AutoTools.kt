package com.bigoke.grainlab.auto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** One-tap analysis. Each tool returns slider values, so results stay fully editable. */
object AutoTools {

    private fun scaled(src: Bitmap, maxSide: Int): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxSide) return src
        val s = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(src, max(1, (src.width * s).toInt()), max(1, (src.height * s).toInt()), true)
    }

    /** Exposure, levels, white balance, shadows/highlights and vibrance from the histogram. */
    fun autoFix(src: Bitmap): Map<String, Float> {
        val b = scaled(src, 320)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()

        val hist = IntArray(256)
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
        var dark = 0; var bright = 0
        for (c in px) {
            val r = Color.red(c); val g = Color.green(c); val bl = Color.blue(c)
            val l = ((r * 299 + g * 587 + bl * 114) / 1000).coerceIn(0, 255)
            hist[l]++
            if (l < 38) dark++
            if (l > 242) bright++
            if (l in 40..220) { sr += r; sg += g; sb += bl; n++ }
        }
        val total = px.size.toFloat()
        fun pct(p: Float): Float {
            var acc = 0; val target = (p * total).toInt()
            for (i in 0..255) { acc += hist[i]; if (acc >= target) return i / 255f }
            return 1f
        }
        val p1 = pct(0.01f); val p5 = pct(0.05f); val med = pct(0.5f); val p95 = pct(0.95f); val p99 = pct(0.99f)

        val out = HashMap<String, Float>()
        val medLin = max(med, 0.02f).toDouble().pow(2.2)
        val target = 0.45.pow(2.2)
        out["exposure"] = ((ln(target / medLin) / ln(2.0)) * 0.6).toFloat().coerceIn(-1.5f, 1.5f)
        out["blacks"] = if (p1 > 0.06f) (-(p1 - 0.03f) * 5f).coerceIn(-0.8f, 0f) else 0f
        out["whites"] = if (p99 < 0.9f) ((0.97f - p99) * 4f).coerceIn(0f, 0.8f) else 0f
        out["contrast"] = if (p95 - p5 < 0.6f) 0.15f else 0f
        out["shadows"] = if (dark / total > 0.2f) 0.3f else 0.1f
        out["highlights"] = if (bright / total > 0.04f) -0.35f else -0.1f
        out["vibrance"] = 0.15f
        if (n > 50) {
            val ar = sr / n; val ag = sg / n; val ab = sb / n
            val rb = (ar / max(ab, 1.0))
            out["temperature"] = (-ln(rb) * 1.1).toFloat().coerceIn(-0.6f, 0.6f)
            out["tint"] = ((ag / ((ar + ab) / 2.0) - 1.0) * 2.0).toFloat().coerceIn(-0.5f, 0.5f)
        }
        return out
    }

    /**
     * Finds the tilt of horizon and vertical lines with an edge-orientation histogram and returns
     * "straighten" (degrees), plus "perspV" when vertical lines converge (keystone).
     */
    fun autoAlign(src: Bitmap): Map<String, Float> {
        val b = scaled(src, 480)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()
        if (w < 16 || h < 16) return emptyMap()
        val g = FloatArray(w * h) { i ->
            val c = px[i]
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        }

        val bins = 121 // -15..+15 degrees in 0.25 steps
        val hist = DoubleArray(bins)
        var totalWeight = 0.0
        val leftDev = ArrayList<Pair<Float, Float>>()
        val rightDev = ArrayList<Pair<Float, Float>>()

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = (g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - 1] + g[i + w - 1])
                val gy = (g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - w] + g[i - w + 1])
                val mag = sqrt(gx * gx + gy * gy)
                if (mag < 0.5f) continue
                totalWeight += mag
                var line = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat() + 90f
                while (line > 90f) line -= 180f
                while (line <= -90f) line += 180f
                val vertical = abs(line) > 45f
                val dev = if (!vertical) line else if (line > 0f) line - 90f else line + 90f
                if (abs(dev) > 15f) continue
                val weight = if (vertical) mag * 0.7f else mag
                val bin = ((dev + 15f) * 4f).toInt().coerceIn(0, bins - 1)
                hist[bin] += weight.toDouble()
                if (vertical && abs(dev) < 10f) {
                    if (x < w / 3) leftDev.add(dev to mag) else if (x > 2 * w / 3) rightDev.add(dev to mag)
                }
            }
        }
        if (totalWeight <= 0.0) return emptyMap()

        var best = -1
        var bestVal = 0.0
        for (i in 0 until bins) {
            var s = 0.0
            for (k in -2..2) { val j = i + k; if (j in 0 until bins) s += hist[j] * (3 - abs(k)) }
            if (s > bestVal) { bestVal = s; best = i }
        }
        val out = HashMap<String, Float>()
        val tilt = if (best >= 0) best / 4f - 15f else 0f
        if (bestVal / 9.0 > totalWeight * 0.015 && abs(tilt) >= 0.3f) out["straighten"] = -tilt
        else out["straighten"] = 0f

        fun weightedMean(list: List<Pair<Float, Float>>): Float? {
            if (list.size < 40) return null
            var s = 0.0; var ws = 0.0
            for ((d, m) in list) { s += (d - tilt) * m; ws += m }
            return if (ws > 0) (s / ws).toFloat() else null
        }
        val l = weightedMean(leftDev)
        val r = weightedMean(rightDev)
        if (l != null && r != null && l * r < 0f) {
            val conv = (l - r) / 2f
            if (abs(conv) > 0.7f) {
                val k = (h.toFloat() / w) * tan(Math.toRadians(conv.toDouble())).toFloat()
                out["perspV"] = (k / 0.25f * 0.8f).coerceIn(-0.8f, 0.8f)
            } else out["perspV"] = 0f
        } else out["perspV"] = 0f
        return out
    }

    /** Estimates sensor noise (Immerkaer method) and returns a noise-reduction strength 0..1. */
    fun autoNoise(src: Bitmap): Float {
        val side = min(512, min(src.width, src.height))
        if (side < 16) return 0f
        val x0 = (src.width - side) / 2
        val y0 = (src.height - side) / 2
        val px = IntArray(side * side)
        src.getPixels(px, 0, side, x0, y0, side, side)
        val g = FloatArray(px.size) { i ->
            val c = px[i]
            0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }
        var sum = 0.0
        for (y in 1 until side - 1) for (x in 1 until side - 1) {
            val i = y * side + x
            val v = g[i - side - 1] - 2 * g[i - side] + g[i - side + 1] -
                2 * g[i - 1] + 4 * g[i] - 2 * g[i + 1] +
                g[i + side - 1] - 2 * g[i + side] + g[i + side + 1]
            sum += abs(v)
        }
        val sigma = sum * sqrt(0.5 * Math.PI) / (6.0 * (side - 2) * (side - 2))
        return ((sigma - 1.5) / 8.0).toFloat().coerceIn(0f, 1f)
    }
}
