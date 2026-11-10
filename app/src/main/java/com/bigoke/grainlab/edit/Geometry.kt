package com.bigoke.grainlab.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rotation, flip, perspective, straighten and crop. Runs before the colour/effects shader. */
object Geometry {
    fun apply(src: Bitmap, p: EditParams): Bitmap {
        var bmp = src
        val rot = ((p["rotation"].roundToInt() % 4) + 4) % 4
        val flip = p["flipH"] > 0.5f
        if (rot != 0 || flip) bmp = rotateFlip(bmp, rot, flip)

        val pv = p["perspV"]
        val ph = p["perspH"]
        if (abs(pv) > 0.001f || abs(ph) > 0.001f) bmp = replace(bmp, src, perspective(bmp, pv, ph))

        val s = p["straighten"]
        if (abs(s) > 0.01f) bmp = replace(bmp, src, straighten(bmp, s))

        val crop = p["crop"].roundToInt()
        if (crop in 1 until Adjustments.cropRatios.size) bmp = replace(bmp, src, cropRatio(bmp, Adjustments.cropRatios[crop]))
        return bmp
    }

    private fun replace(old: Bitmap, original: Bitmap, new: Bitmap): Bitmap {
        if (old !== original && old !== new) old.recycle()
        return new
    }

    fun rotateFlip(src: Bitmap, quarterTurns: Int, flip: Boolean): Bitmap {
        val m = Matrix()
        if (flip) m.postScale(-1f, 1f)
        if (quarterTurns != 0) m.postRotate(quarterTurns * 90f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun mirror(src: Bitmap): Bitmap = rotateFlip(src, 0, true)

    private fun perspective(b: Bitmap, pv: Float, ph: Float): Bitmap {
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        val kv = 0.25f * abs(pv)
        val kh = 0.25f * abs(ph)
        // Source quad (TL, TR, BR, BL) stretched to the full frame.
        val q = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        if (pv > 0f) { q[0] += kv * w; q[2] -= kv * w } else if (pv < 0f) { q[6] += kv * w; q[4] -= kv * w }
        if (ph > 0f) { q[1] += kh * h; q[7] -= kh * h } else if (ph < 0f) { q[3] += kh * h; q[5] -= kh * h }
        val m = Matrix()
        m.setPolyToPoly(q, 0, floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, 4)
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun straighten(b: Bitmap, degrees: Float): Bitmap {
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        val rad = Math.toRadians(abs(degrees).toDouble())
        val scale = (cos(rad) + sin(rad) * max(w / h, h / w)).toFloat()
        val m = Matrix()
        m.postRotate(degrees, w / 2f, h / 2f)
        m.postScale(scale, scale, w / 2f, h / 2f)
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun cropRatio(b: Bitmap, ratio: Float): Bitmap {
        val w = b.width
        val h = b.height
        val r = if (w >= h) max(ratio, 1f / ratio) else min(ratio, 1f / ratio)
        val current = w.toFloat() / h
        return if (current > r) {
            val nw = (h * r).roundToInt().coerceIn(1, w)
            Bitmap.createBitmap(b, (w - nw) / 2, 0, nw, h)
        } else {
            val nh = (w / r).roundToInt().coerceIn(1, h)
            Bitmap.createBitmap(b, 0, (h - nh) / 2, w, nh)
        }
    }
}

/** Borders and the date stamp, drawn after the film shader. */
object Finishing {
    fun apply(src: Bitmap, p: EditParams): Bitmap {
        var out = src
        if (p["dateStamp"] > 0.5f) {
            if (!out.isMutable) out = out.copy(Bitmap.Config.ARGB_8888, true)
            drawDate(out)
        }
        val border = p["border"].roundToInt()
        val framed = when (border) {
            1 -> frame(out, 0.035f, 0.035f, 0.035f, 0.035f, Color.WHITE)
            2 -> frame(out, 0.06f, 0.06f, 0.06f, 0.22f, Color.rgb(246, 243, 235))
            3 -> frame(out, 0.045f, 0.045f, 0.045f, 0.045f, Color.rgb(12, 12, 12))
            else -> out
        }
        if (framed !== out && out !== src) out.recycle()
        return framed
    }

    private fun frame(b: Bitmap, l: Float, t: Float, r: Float, btm: Float, color: Int): Bitmap {
        val m = min(b.width, b.height).toFloat()
        val left = (l * m).roundToInt()
        val top = (t * m).roundToInt()
        val right = (r * m).roundToInt()
        val bottom = (btm * m).roundToInt()
        val out = Bitmap.createBitmap(b.width + left + right, b.height + top + bottom, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(color)
        c.drawBitmap(b, left.toFloat(), top.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        if (color == Color.rgb(12, 12, 12)) {
            // Soft rounded inner edge like a film gate.
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = m * 0.012f
                this.color = Color.rgb(12, 12, 12)
            }
            val rect = RectF(left.toFloat(), top.toFloat(), (left + b.width).toFloat(), (top + b.height).toFloat())
            c.drawRoundRect(rect, m * 0.02f, m * 0.02f, p)
        }
        return out
    }

    private fun drawDate(b: Bitmap) {
        val text = SimpleDateFormat("''yy  M  d", Locale.US).format(Date())
        val size = b.height * 0.035f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 138, 0)
            textSize = size
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(size * 0.35f, 0f, 0f, Color.argb(200, 255, 80, 0))
        }
        val x = b.width - paint.measureText(text) - b.width * 0.05f
        val y = b.height - b.height * 0.05f
        Canvas(b).drawText(text, x, y, paint)
    }
}
