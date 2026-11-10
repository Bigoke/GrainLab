package com.bigoke.grainlab.edit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Bakes every colour and tone setting (light, colour, HSL, grading, film look) into one
 * 33x33x33 lookup table. The same table drives the editor, the live camera and video.
 * Layout: RGB floats (0..1), index = ((b * N + g) * N + r) * 3. Uploaded as half floats, so
 * smooth gradients such as skies do not band.
 */
object LutBuilder {
    const val N = 33

    private class Ctx(p: EditParams) {
        val film = FilmLooks.byId(p.filmId)
        val filmStrength = p["filmStrength"]
        val expMul = 2.0.pow(p["exposure"].toDouble()).toFloat()
        val temp = p["temperature"]
        val tint = p["tint"]
        val lo = -0.12f * p["blacks"]
        val hi = 1f - 0.15f * p["whites"]
        val shadows = p["shadows"]
        val highlights = p["highlights"]
        val contrast = p["contrast"]
        val saturation = p["saturation"]
        val vibrance = p["vibrance"]
        val fade = p["fade"]
        val hslH = FloatArray(8) { p[Adjustments.hslKey(it, 'h')] }
        val hslS = FloatArray(8) { p[Adjustments.hslKey(it, 's')] }
        val hslL = FloatArray(8) { p[Adjustments.hslKey(it, 'l')] }
        val hslActive = (0 until 8).any { abs(hslH[it]) + abs(hslS[it]) + abs(hslL[it]) > 1e-4f }
        val shSat = p["shadowSat"]
        val hiSat = p["highlightSat"]
        val balance = p["gradeBalance"]
        val shColor = hueColor(p["shadowHue"])
        val hiColor = hueColor(p["highlightHue"])
        /** Tone curves (all channels, then red, green, blue); null when untouched. */
        val curves: Array<Curve?> = Array(4) { ci ->
            val ch = Adjustments.curveChannels[ci]
            val ys = FloatArray(5) { p[Adjustments.curveKey(ch, it)] }
            if ((0..4).all { abs(ys[it] - it / 4f) < 1e-4f }) null else Curve(ys)
        }
    }

    fun build(p: EditParams): FloatArray {
        val ctx = Ctx(p)
        val out = FloatArray(N * N * N * 3)
        val c = FloatArray(3)
        val tmp = FloatArray(3)
        var i = 0
        for (b in 0 until N) for (g in 0 until N) for (r in 0 until N) {
            c[0] = r / (N - 1f); c[1] = g / (N - 1f); c[2] = b / (N - 1f)
            process(c, tmp, ctx)
            out[i++] = c[0].coerceIn(0f, 1f); out[i++] = c[1].coerceIn(0f, 1f); out[i++] = c[2].coerceIn(0f, 1f)
        }
        return out
    }

    /** Media3 SingleColorLut wants cube[r][g][b] as ARGB ints. */
    fun toCube(lut: FloatArray): Array<Array<IntArray>> =
        Array(N) { r ->
            Array(N) { g ->
                IntArray(N) { b ->
                    val i = ((b * N + g) * N + r) * 3
                    val rr = (lut[i] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val gg = (lut[i + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bb = (lut[i + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
            }
        }


    private fun luma(c: FloatArray) = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]
    private fun toLin(v: Float) = if (v <= 0f) 0f else v.pow(2.2f)
    private fun toGamma(v: Float) = if (v <= 0f) 0f else v.pow(1f / 2.2f)
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun process(c: FloatArray, f: FloatArray, x: Ctx) {
        // 1. Exposure and white balance in linear light
        var lr = toLin(c[0]) * x.expMul * (1f + 0.22f * x.temp)
        var lg = toLin(c[1]) * x.expMul * (1f - 0.15f * x.tint)
        var lb = toLin(c[2]) * x.expMul * (1f - 0.22f * x.temp)
        c[0] = toGamma(lr); c[1] = toGamma(lg); c[2] = toGamma(lb)

        // 2. Whites / blacks (levels)
        val range = x.hi - x.lo
        for (k in 0..2) c[k] = (c[k] - x.lo) / range

        // 3. Shadows / highlights on luminance
        if (x.shadows != 0f || x.highlights != 0f) {
            val l = luma(c).coerceIn(0f, 1f)
            val sm = (1f - l) * (1f - l) * smooth(0f, 0.15f, l + 0.1f)
            val hm = l * l
            val d = 0.32f * x.shadows * sm + 0.3f * x.highlights * hm
            for (k in 0..2) c[k] += d
        }

        // 4. Contrast (S-curve around mid grey)
        if (x.contrast != 0f) {
            val k = 1f + 0.8f * x.contrast
            for (j in 0..2) c[j] = 0.5f + (c[j].coerceIn(0f, 1.2f) - 0.5f) * k
        }

        // 5. Film look, blended by film strength
        val film = x.film
        if (film != null && x.filmStrength > 0f) {
            f[0] = c[0].coerceIn(0f, 1f); f[1] = c[1].coerceIn(0f, 1f); f[2] = c[2].coerceIn(0f, 1f)
            if (film.warmth != 0f) { f[0] *= 1f + 0.08f * film.warmth; f[2] *= 1f - 0.08f * film.warmth }
            val bw = film.bw
            if (bw != null) {
                val l = (f[0] * bw[0] + f[1] * bw[1] + f[2] * bw[2]).coerceIn(0f, 1f)
                f[0] = l; f[1] = l; f[2] = l
            }
            f[0] = film.r.eval(f[0]); f[1] = film.g.eval(f[1]); f[2] = film.b.eval(f[2])
            if (film.contrast != 0f) {
                val k = 1f + 0.8f * film.contrast
                for (j in 0..2) f[j] = 0.5f + (f[j] - 0.5f) * k
            }
            if (film.saturation != 1f && bw == null) {
                val l = luma(f)
                for (j in 0..2) f[j] = l + (f[j] - l) * film.saturation
            }
            val l = luma(f).coerceIn(0f, 1f)
            val sm = (1f - l) * (1f - l); val hm = l * l
            for (j in 0..2) f[j] += film.shadowTint[j] * sm * 1.5f + film.highlightTint[j] * hm * 1.5f
            if (film.fade > 0f) for (j in 0..2) f[j] = film.fade + f[j] * (1f - film.fade)
            val s = x.filmStrength
            for (j in 0..2) c[j] = c[j] + (f[j] - c[j]) * s
        }

        // 6. Saturation and vibrance
        if (x.saturation != 0f || x.vibrance != 0f) {
            val l = luma(c)
            val mx = max(c[0], max(c[1], c[2])); val mn = min(c[0], min(c[1], c[2]))
            val sat = (mx - mn).coerceIn(0f, 1f)
            val k = (1f + x.saturation) * (1f + x.vibrance * (1f - sat))
            for (j in 0..2) c[j] = l + (c[j] - l) * k
        }

        // 6b. Tone curves
        x.curves[0]?.let { cm -> for (j in 0..2) c[j] = cm.eval(c[j].coerceIn(0f, 1f)) }
        for (j in 0..2) x.curves[j + 1]?.let { c[j] = it.eval(c[j].coerceIn(0f, 1f)) }

        // 7. HSL per colour band
        if (x.hslActive) applyHsl(c, x)

        // 8. Split toning
        if (x.shSat > 0f || x.hiSat > 0f) {
            val l = luma(c).coerceIn(0f, 1f)
            val pivot = 0.5f + 0.3f * x.balance
            val sm = smooth(pivot + 0.2f, 0f, l)
            val hm = smooth(pivot - 0.2f, 1f, l)
            for (j in 0..2) c[j] += (x.shColor[j] - 0.5f) * x.shSat * 0.3f * sm +
                (x.hiColor[j] - 0.5f) * x.hiSat * 0.3f * hm
        }

        // 9. Fade (lifted blacks, softened whites)
        if (x.fade > 0f) for (j in 0..2) c[j] = 0.16f * x.fade + c[j] * (1f - 0.22f * x.fade)

        for (j in 0..2) c[j] = c[j].coerceIn(0f, 1f)
    }

    private fun applyHsl(c: FloatArray, x: Ctx) {
        val r = c[0].coerceIn(0f, 1f); val g = c[1].coerceIn(0f, 1f); val b = c[2].coerceIn(0f, 1f)
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val v = mx
        val d = mx - mn
        val s = if (mx <= 0f) 0f else d / mx
        if (d < 1e-5f) return
        var h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f
        var dh = 0f; var ds = 0f; var dl = 0f
        for (i in 0 until 8) {
            val centre = Adjustments.bands[i].second
            var dist = abs(h - centre); if (dist > 180f) dist = 360f - dist
            val w = (1f - dist / 45f).coerceIn(0f, 1f)
            if (w <= 0f) continue
            val ww = w * w * (3 - 2 * w)
            dh += ww * x.hslH[i]; ds += ww * x.hslS[i]; dl += ww * x.hslL[i]
        }
        var nh = h + dh * 30f
        if (nh < 0f) nh += 360f
        if (nh >= 360f) nh -= 360f
        val ns = (s * (1f + ds)).coerceIn(0f, 1f)
        val nv = (v * (1f + dl * 0.5f * s)).coerceIn(0f, 1f)
        hsvToRgb(nh, ns, nv, c)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
        val cc = v * s
        val hp = (h / 60f) % 6f
        val xx = cc * (1f - abs(hp % 2f - 1f))
        val m = v - cc
        val (r, g, b) = when (hp.toInt()) {
            0 -> Triple(cc, xx, 0f)
            1 -> Triple(xx, cc, 0f)
            2 -> Triple(0f, cc, xx)
            3 -> Triple(0f, xx, cc)
            4 -> Triple(xx, 0f, cc)
            else -> Triple(cc, 0f, xx)
        }
        out[0] = r + m; out[1] = g + m; out[2] = b + m
    }

    private fun hueColor(h: Float): FloatArray {
        val o = FloatArray(3)
        hsvToRgb(((h % 360f) + 360f) % 360f, 1f, 1f, o)
        return o
    }
}
