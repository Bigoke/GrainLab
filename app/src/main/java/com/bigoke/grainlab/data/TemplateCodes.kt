package com.bigoke.grainlab.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Turns templates into short codes (and QR codes) that another phone can scan. */
object TemplateCodes {
    private const val PREFIX = "GRAINLAB1:"

    fun encode(t: Template): String {
        // Only the look is shared: no photo-specific data.
        val o = t.copy(favourite = false).toJson()
        val raw = o.toString().toByteArray(Charsets.UTF_8)
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /** Accepts a scanned code or the text of a shared template file. */
    fun decode(text: String): String? {
        val s = text.trim()
        if (s.startsWith("{")) return s
        if (!s.startsWith(PREFIX)) return null
        return runCatching {
            val bytes = Base64.decode(s.removePrefix(PREFIX), Base64.NO_WRAP or Base64.URL_SAFE)
            val inf = Inflater()
            inf.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            inf.end()
            val json = out.toString("UTF-8")
            JSONObject(json)
            json
        }.getOrNull()
    }

    fun qr(text: String, size: Int = 720): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /** A small scene (sky, sun, hills, a face-coloured shape) for template previews. */
    val sampleScene: Bitmap by lazy {
        val w = 240; val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, Color.rgb(70, 130, 200), Color.rgb(230, 200, 170), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.62f, p)
        p.shader = null
        p.color = Color.rgb(255, 230, 160); c.drawCircle(w * 0.78f, h * 0.22f, h * 0.09f, p)
        p.color = Color.rgb(70, 120, 60); c.drawOval(-w * 0.2f, h * 0.48f, w * 0.7f, h * 1.2f, p)
        p.color = Color.rgb(110, 150, 70); c.drawOval(w * 0.35f, h * 0.55f, w * 1.3f, h * 1.3f, p)
        p.color = Color.rgb(215, 160, 125); c.drawCircle(w * 0.28f, h * 0.5f, h * 0.13f, p)
        p.color = Color.rgb(160, 40, 40); c.drawRect(w * 0.18f, h * 0.62f, w * 0.38f, h * 0.95f, p)
        p.color = Color.rgb(30, 30, 35); c.drawRect(w * 0.55f, h * 0.7f, w * 0.68f, h * 0.9f, p)
        b
    }
}
