package com.bigoke.grainlab.edit

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns masks into grey images (white = fully affected) the size of the photo's mask grid.
 * Sky and subject masks are found from the photo itself and cached.
 */
object MaskRaster {
    /** Masks are drawn at this size (longest side); the GPU smooths them when sampling. */
    const val SIZE = 1024

    private val autoCache = LinkedHashMap<String, FloatArray>()

    fun gridSize(w: Int, h: Int): Pair<Int, Int> {
        val s = SIZE.toFloat() / max(w, h)
        return max(8, (w * s).roundToInt()) to max(8, (h * s).roundToInt())
    }

    /** Renders every mask for [photo] (already cropped/straightened). Returns grey bitmaps in mask order. */
    suspend fun render(masks: List<Mask>, photo: Bitmap, photoKey: String): List<Bitmap> {
        if (masks.isEmpty()) return emptyList()
        val (gw, gh) = gridSize(photo.width, photo.height)
        val small by lazy { Bitmap.createScaledBitmap(photo, gw, gh, true) }
        return masks.take(Mask.MAX).map { m ->
            val weights: FloatArray = when (m.type) {
                Mask.RADIAL -> radial(m, gw, gh)
                Mask.LINEAR -> linear(m, gw, gh)
                Mask.BRUSH -> brush(m, gw, gh)
                Mask.SKY -> cached("sky|$photoKey|$gw") { sky(small) }
                else -> cached("subject|$photoKey|$gw") { subject(small) ?: FloatArray(gw * gh) }
            }
            toBitmap(if (m.invert) FloatArray(weights.size) { 1f - weights[it] } else weights, gw, gh)
        }
    }

    private suspend fun cached(key: String, make: suspend () -> FloatArray): FloatArray {
        synchronized(autoCache) { autoCache[key]?.let { return it } }
        val v = make()
        synchronized(autoCache) {
            autoCache[key] = v
            while (autoCache.size > 6) autoCache.remove(autoCache.keys.first())
        }
        return v
    }

    private fun toBitmap(w: FloatArray, gw: Int, gh: Int): Bitmap {
        val px = IntArray(w.size) {
            val v = (w[it].coerceIn(0f, 1f) * 255f).roundToInt()
            Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    /** Ellipse centred at (cx, cy); size is the radius as a share of the shorter side. */
    private fun radial(m: Mask, gw: Int, gh: Int): FloatArray {
        val side = min(gw, gh).toFloat()
        val r = max(1f, m.size * side)
        val inner = r * (1f - m.feather.coerceIn(0.02f, 1f))
        val a = Math.toRadians(m.angle.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        return FloatArray(gw * gh) { i ->
            val x = i % gw - m.cx * gw
            val y = i / gw - m.cy * gh
            // Slightly oval: 1.4 times wider than tall, rotated by angle.
            val u = (x * ca + y * sa) / 1.4f
            val v = -x * sa + y * ca
            val d = sqrt(u * u + v * v)
            1f - smooth((d - inner) / max(1f, r - inner))
        }
    }

    /** Linear gradient: full effect on one side of a line through (cx, cy) at angle, fading over feather. */
    private fun linear(m: Mask, gw: Int, gh: Int): FloatArray {
        val a = Math.toRadians(m.angle.toDouble() + 90.0)
        val nx = cos(a).toFloat(); val ny = sin(a).toFloat()
        val width = max(2f, m.feather.coerceIn(0.02f, 1f) * 0.6f * max(gw, gh))
        return FloatArray(gw * gh) { i ->
            val d = (i % gw - m.cx * gw) * nx + (i / gw - m.cy * gh) * ny
            1f - smooth((d + width / 2f) / width)
        }
    }

    private fun brush(m: Mask, gw: Int, gh: Int): FloatArray {
        val bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        val side = min(gw, gh).toFloat()
        for (s in m.strokes) {
            val rad = max(1f, s.radius * side)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (s.erase) Color.BLACK else Color.WHITE
                strokeWidth = rad * 2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                style = Paint.Style.STROKE
                maskFilter = BlurMaskFilter(max(1f, rad * m.feather.coerceIn(0.05f, 1f)), BlurMaskFilter.Blur.NORMAL)
                if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            }
            val pts = s.points
            if (pts.size == 2) {
                paint.style = Paint.Style.FILL
                c.drawCircle(pts[0] * gw, pts[1] * gh, rad, paint)
            } else {
                var i = 0
                while (i + 3 < pts.size) {
                    c.drawLine(pts[i] * gw, pts[i + 1] * gh, pts[i + 2] * gw, pts[i + 3] * gh, paint)
                    i += 2
                }
            }
        }
        val px = IntArray(gw * gh)
        bmp.getPixels(px, 0, gw, 0, 0, gw, gh)
        bmp.recycle()
        return FloatArray(px.size) { Color.red(px[it]) / 255f }
    }

    /**
     * Sky: blue or bright, low-texture pixels connected to the top edge, then softened.
     */
    private fun sky(b: Bitmap): FloatArray {
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(px.size) {
            val c = px[it]
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        }
        val cand = BooleanArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val r = Color.red(c) / 255f; val g = Color.green(c) / 255f; val bl = Color.blue(c) / 255f
            val x = i % w; val y = i / w
            val mx = max(r, max(g, bl)); val mn = min(r, min(g, bl))
            val sat = if (mx > 0.01f) (mx - mn) / mx else 0f
            var tex = 0f
            if (x in 1 until w - 1 && y in 1 until h - 1) {
                tex = kotlin.math.abs(lum[i - 1] - lum[i + 1]) + kotlin.math.abs(lum[i - w] - lum[i + w])
            }
            val blue = bl > r * 1.05f && bl >= g * 0.92f && lum[i] > 0.25f
            val overcast = lum[i] > 0.62f && sat < 0.18f
            cand[i] = (blue || overcast) && tex < 0.09f
        }
        // Keep only areas connected to the top edge.
        val keep = BooleanArray(px.size)
        val queue = ArrayDeque<Int>()
        for (x in 0 until w) if (cand[x]) { keep[x] = true; queue.add(x) }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w; val y = i / w
            val nb = intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)
            for (n in nb) if (n >= 0 && !keep[n] && cand[n]) { keep[n] = true; queue.add(n) }
        }
        return blur(FloatArray(px.size) { if (keep[it]) 1f else 0f }, w, h, max(2, w / 120))
    }

    /** Main subject via ML Kit's on-device model (downloaded by Google Play services on first use). */
    private suspend fun subject(b: Bitmap): FloatArray? = suspendCancellableCoroutine { cont ->
        try {
            val segmenter = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
            )
            segmenter.process(InputImage.fromBitmap(b, 0))
                .addOnSuccessListener { result ->
                    val buf = result.foregroundConfidenceMask
                    val out = if (buf == null) null else {
                        buf.rewind()
                        val n = b.width * b.height
                        FloatArray(n) { if (buf.hasRemaining()) buf.get() else 0f }
                    }
                    segmenter.close()
                    if (cont.isActive) cont.resume(out?.let { blur(it, b.width, b.height, 1) })
                }
                .addOnFailureListener {
                    segmenter.close()
                    if (cont.isActive) cont.resume(null)
                }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Box blur, run twice (close to a Gaussian). */
    private fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        var a = src
        repeat(2) {
            val tmp = FloatArray(a.size)
            for (y in 0 until h) {
                var acc = 0f
                for (x in -r..r) acc += a[y * w + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[y * w + x] = acc / (2 * r + 1)
                    acc += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)]
                }
            }
            val out = FloatArray(a.size)
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    out[y * w + x] = acc / (2 * r + 1)
                    acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
                }
            }
            a = out
        }
        return a
    }
}
