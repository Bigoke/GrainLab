package com.bigoke.grainlab.auto

import android.graphics.Bitmap
import android.graphics.Color
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.LutBuilder
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

    private class Stats(
        val median: Float, val low: Float, val high: Float, val std: Float,
        val shadowFrac: Float, val clipFrac: Float, val meanSat: Float, val haze: Float,
    )

    /** Runs pixels through a lookup table (trilinear) and measures the result. */
    private fun measure(px: IntArray, lut: ByteArray?): Stats {
        val n = LutBuilder.N
        val hist = IntArray(256)
        var sum = 0.0; var sum2 = 0.0; var satSum = 0.0; var hazeSum = 0.0
        var shadows = 0; var clipped = 0
        val o = FloatArray(3)
        for (c in px) {
            var r = Color.red(c) / 255f; var g = Color.green(c) / 255f; var b = Color.blue(c) / 255f
            if (lut != null) {
                sample(lut, n, r, g, b, o)
                r = o[0]; g = o[1]; b = o[2]
            }
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            hist[(l * 255f).toInt().coerceIn(0, 255)]++
            sum += l; sum2 += l * l
            if (l < 0.12f) shadows++
            if (l > 0.96f) clipped++
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            satSum += if (mx > 0.01f) (mx - mn) / mx else 0f
            hazeSum += mn
        }
        val total = px.size.toFloat()
        fun pct(p: Float): Float {
            var acc = 0; val target = (p * total).toInt()
            for (i in 0..255) { acc += hist[i]; if (acc >= target) return i / 255f }
            return 1f
        }
        val mean = sum / total
        return Stats(
            median = pct(0.5f), low = pct(0.005f), high = pct(0.995f),
            std = sqrt(max(0.0, sum2 / total - mean * mean)).toFloat(),
            shadowFrac = shadows / total, clipFrac = clipped / total,
            meanSat = (satSum / total).toFloat(), haze = (hazeSum / total).toFloat(),
        )
    }

    private fun sample(lut: ByteArray, n: Int, r: Float, g: Float, b: Float, out: FloatArray) {
        val fr = r.coerceIn(0f, 1f) * (n - 1); val fg = g.coerceIn(0f, 1f) * (n - 1); val fb = b.coerceIn(0f, 1f) * (n - 1)
        val r0 = fr.toInt().coerceAtMost(n - 2); val g0 = fg.toInt().coerceAtMost(n - 2); val b0 = fb.toInt().coerceAtMost(n - 2)
        val dr = fr - r0; val dg = fg - g0; val db = fb - b0
        for (k in 0..2) {
            fun v(ri: Int, gi: Int, bi: Int) = (lut[((bi * n + gi) * n + ri) * 3 + k].toInt() and 0xFF) / 255f
            val c00 = v(r0, g0, b0) * (1 - dr) + v(r0 + 1, g0, b0) * dr
            val c10 = v(r0, g0 + 1, b0) * (1 - dr) + v(r0 + 1, g0 + 1, b0) * dr
            val c01 = v(r0, g0, b0 + 1) * (1 - dr) + v(r0 + 1, g0, b0 + 1) * dr
            val c11 = v(r0, g0 + 1, b0 + 1) * (1 - dr) + v(r0 + 1, g0 + 1, b0 + 1) * dr
            val c0 = c00 * (1 - dg) + c10 * dg
            val c1 = c01 * (1 - dg) + c11 * dg
            out[k] = c0 * (1 - db) + c1 * db
        }
    }

    private fun lin(v: Float) = v.toDouble().pow(2.2).toFloat()
    private fun log2(v: Double) = ln(v) / ln(2.0)

    /**
     * Auto fix in stages, each measured through the real colour pipeline:
     * 1) white balance from neutral and bright pixels, 2) exposure that keeps some of the
     * scene's mood, 3) levels, shadows, highlights, contrast, vibrance and dehaze from the
     * corrected image, 4) one exposure refinement pass.
     */
    fun autoFix(src: Bitmap): Map<String, Float> {
        val b = scaled(src, 384)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()
        val out = HashMap<String, Float>()
        val keys = listOf(
            "exposure", "contrast", "highlights", "shadows", "whites", "blacks",
            "temperature", "tint", "vibrance", "saturation", "dehaze", "clarity", "texture",
        )
        keys.forEach { out[it] = 0f }

        // ---- 1. White balance ----
        var nr = 0.0; var ng = 0.0; var nb = 0.0; var nCount = 0
        val bright = ArrayList<Int>()
        val lum = FloatArray(px.size)
        for ((i, c) in px.withIndex()) {
            val r = Color.red(c) / 255f; val g = Color.green(c) / 255f; val bl = Color.blue(c) / 255f
            val l = 0.299f * r + 0.587f * g + 0.114f * bl
            lum[i] = l
            val mx = max(r, max(g, bl)); val mn = min(r, min(g, bl))
            val sat = if (mx > 0.01f) (mx - mn) / mx else 0f
            if (l in 0.15f..0.92f && sat < 0.3f) {
                nr += lin(r); ng += lin(g); nb += lin(bl); nCount++
            }
        }
        val sortedIdx = lum.indices.sortedByDescending { lum[it] }
        val topN = max(1, px.size / 100)
        for (k in 0 until topN) { val i = sortedIdx[k]; if (lum[i] < 0.985f) bright.add(px[i]) }
        var wr = 0.0; var wg = 0.0; var wb = 0.0
        for (c in bright) { wr += lin(Color.red(c) / 255f); wg += lin(Color.green(c) / 255f); wb += lin(Color.blue(c) / 255f) }

        fun norm(r: Double, g: Double, b: Double): DoubleArray {
            val l = (r + g + b) / 3.0
            return if (l <= 1e-6) doubleArrayOf(1.0, 1.0, 1.0) else doubleArrayOf(r / l, g / l, b / l)
        }
        val gray = if (nCount > px.size / 50) norm(nr, ng, nb) else null
        val white = if (bright.size > 10) norm(wr, wg, wb) else null
        val est = when {
            gray != null && white != null -> DoubleArray(3) { gray[it] * 0.65 + white[it] * 0.35 }
            gray != null -> gray
            white != null -> white
            else -> doubleArrayOf(1.0, 1.0, 1.0)
        }
        // Gains that would neutralise the cast, mapped onto the temperature/tint model.
        val gr = 1.0 / est[0]; val gg = 1.0 / est[1]; val gb = 1.0 / est[2]
        val rb = gr / gb
        var temp = ((rb - 1.0) / (0.22 * (rb + 1.0))).toFloat()
        temp = (temp * 0.75f).coerceIn(-0.8f, 0.8f)
        val tGain = kotlin.math.sqrt((1.0 + 0.22 * temp) * (1.0 - 0.22 * temp))
        val gRel = gg / kotlin.math.sqrt(gr * gb) * tGain
        var tint = ((1.0 - gRel) / 0.15).toFloat()
        tint = (tint * 0.75f).coerceIn(-0.6f, 0.6f)
        out["temperature"] = temp
        out["tint"] = tint

        // ---- 2. Exposure ----
        var params = EditParams().withAll(mapOf("temperature" to temp, "tint" to tint))
        var st = measure(px, LutBuilder.build(params))
        val med = max(st.median, 0.02f)
        // Pull toward mid grey but keep part of the scene's own brightness (snow stays bright, night stays dark).
        val target = (0.46f + (med - 0.46f) * 0.3f).coerceIn(0.36f, 0.58f)
        var exposure = log2(lin(target).toDouble() / lin(med).toDouble()).toFloat()
        if (exposure > 0f) {
            val headroom = log2(lin(0.98f).toDouble() / lin(max(st.high, 0.05f)).toDouble()).toFloat() + 0.6f
            exposure = min(exposure, max(headroom, 0f))
        }
        exposure = (exposure * 0.9f).coerceIn(-1.6f, 1.8f)
        out["exposure"] = exposure

        // ---- 3. Tone and colour from the corrected image ----
        params = params.with("exposure", exposure)
        st = measure(px, LutBuilder.build(params))
        out["blacks"] = when {
            st.low > 0.04f -> (-(st.low - 0.015f) / 0.12f).coerceIn(-0.9f, 0f)
            st.low < 0.003f && st.shadowFrac > 0.25f -> 0.15f
            else -> 0f
        }
        out["whites"] = if (st.high < 0.94f) ((1f - (st.high + 0.02f)) / 0.15f).coerceIn(0f, 0.8f) else 0f
        out["shadows"] = ((st.shadowFrac - 0.06f) * 2.5f).coerceIn(0f, 0.6f)
        out["highlights"] = -((st.clipFrac - 0.005f) * 8f).coerceIn(0f, 0.7f)
        out["contrast"] = ((0.22f - st.std) * 2.2f).coerceIn(-0.25f, 0.35f)
        out["vibrance"] = ((0.3f - st.meanSat) * 1.6f).coerceIn(-0.15f, 0.45f)
        out["dehaze"] = if (st.haze > 0.22f && st.low > 0.06f) ((st.haze - 0.18f) * 1.6f).coerceIn(0f, 0.5f) else 0f
        out["clarity"] = 0.1f
        out["texture"] = 0.05f

        // ---- 4. Refine exposure with everything applied ----
        params = params.withAll(out)
        st = measure(px, LutBuilder.build(params))
        val finalMed = max(st.median, 0.02f)
        if (finalMed < target - 0.05f || finalMed > target + 0.05f) {
            val adj = log2(lin(target).toDouble() / lin(finalMed).toDouble()).toFloat() * 0.6f
            out["exposure"] = (exposure + adj).coerceIn(-1.8f, 2f)
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
