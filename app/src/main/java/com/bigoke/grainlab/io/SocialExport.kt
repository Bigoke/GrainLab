package com.bigoke.grainlab.io

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/** Export sizes, including ready-made social media formats. */
enum class ExportSize(val label: String, val maxSide: Int, val frameW: Int = 0, val frameH: Int = 0, val quality: Int = 0) {
    FULL("Full", 16384),
    PX4096("4096 px", 4096),
    PX2048("2048 px", 2048),
    PX1080("1080 px", 1080),
    INSTAGRAM("Instagram 4:5", 1350, 1080, 1350),
    STORY("Story 9:16", 1920, 1080, 1920),
    WHATSAPP("WhatsApp", 1600, quality = 82);

    val framed get() = frameW > 0
}

object SocialExport {
    /** Fits the photo into a fixed frame, filling the space with a soft blurred copy of itself. */
    fun frame(src: Bitmap, size: ExportSize): Bitmap {
        if (!size.framed) return src
        val out = Bitmap.createBitmap(size.frameW, size.frameH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // Background: the photo scaled to cover, heavily downsampled then upscaled (cheap blur), darkened.
        val cover = max(size.frameW.toFloat() / src.width, size.frameH.toFloat() / src.height)
        val tiny = Bitmap.createScaledBitmap(src, max(1, src.width / 24), max(1, src.height / 24), true)
        val bw = src.width * cover
        val bh = src.height * cover
        val dst = RectF((size.frameW - bw) / 2f, (size.frameH - bh) / 2f, (size.frameW + bw) / 2f, (size.frameH + bh) / 2f)
        c.drawBitmap(tiny, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(Color.argb(90, 0, 0, 0))
        tiny.recycle()
        // Foreground: the whole photo, fitted.
        val fit = min(size.frameW.toFloat() / src.width, size.frameH.toFloat() / src.height)
        val fw = src.width * fit
        val fh = src.height * fit
        val fg = RectF((size.frameW - fw) / 2f, (size.frameH - fh) / 2f, (size.frameW + fw) / 2f, (size.frameH + fh) / 2f)
        c.drawBitmap(src, Rect(0, 0, src.width, src.height), fg, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Small semi-transparent text in the bottom-right corner. */
    fun watermark(src: Bitmap, text: String): Bitmap {
        if (text.isBlank()) return src
        val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val size = min(out.width, out.height) * 0.032f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(size * 0.25f, 0f, 0f, Color.argb(140, 0, 0, 0))
        }
        val margin = min(out.width, out.height) * 0.03f
        c.drawText(text, out.width - p.measureText(text) - margin, out.height - margin, p)
        return out
    }
}
