package com.bigoke.grainlab.edit

/** Monotone tone curve through 5 points at x = 0, 0.25, 0.5, 0.75, 1. */
class Curve(private val ys: FloatArray) {
    private val table: FloatArray by lazy { buildTable() }

    fun eval(x: Float): Float {
        val t = x.coerceIn(0f, 1f) * SIZE
        val i = t.toInt().coerceAtMost(SIZE - 1)
        val f = t - i
        return table[i] + (table[i + 1] - table[i]) * f
    }

    private fun buildTable(): FloatArray {
        val n = ys.size
        val xs = FloatArray(n) { it / (n - 1f) }
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        // Fritsch-Carlson limiter keeps the curve monotone
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / kotlin.math.sqrt(s)
                m[i] = tau * a * d[i]; m[i + 1] = tau * b * d[i]
            }
        }
        val out = FloatArray(SIZE + 1)
        for (j in 0..SIZE) {
            val x = j / SIZE.toFloat()
            var k = (x * (n - 1)).toInt().coerceAtMost(n - 2)
            if (k < 0) k = 0
            val h = xs[k + 1] - xs[k]
            val t = (x - xs[k]) / h
            val t2 = t * t; val t3 = t2 * t
            out[j] = (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] +
                (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
        }
        return out
    }

    companion object {
        private const val SIZE = 1024
        val IDENTITY = Curve(floatArrayOf(0f, .25f, .5f, .75f, 1f))
    }
}

private fun cv(a: Float, b: Float, c: Float, d: Float, e: Float) = Curve(floatArrayOf(a, b, c, d, e))
private fun rgb(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

/**
 * A film look: colour response (baked into the lookup table) plus the film's grain,
 * vignette, halation, border and date-stamp defaults.
 */
class FilmLook(
    val id: String,
    val name: String,
    val pack: String,
    val note: String,
    val r: Curve = Curve.IDENTITY,
    val g: Curve = Curve.IDENTITY,
    val b: Curve = Curve.IDENTITY,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val fade: Float = 0f,
    val warmth: Float = 0f,
    val shadowTint: FloatArray = rgb(0f, 0f, 0f),
    val highlightTint: FloatArray = rgb(0f, 0f, 0f),
    val bw: FloatArray? = null,
    val effects: Map<String, Float> = emptyMap(),
)

