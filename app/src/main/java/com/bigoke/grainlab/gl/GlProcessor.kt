package com.bigoke.grainlab.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import com.bigoke.grainlab.edit.EditParams
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Off-screen GPU renderer used for the editor preview, exports, captured photos and batch jobs.
 * All GL work runs on one dedicated thread that owns the EGL context.
 */
object GlProcessor {
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GrainLab-GL").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program: GlProgram? = null
    private var srcTex = 0
    private var lutTex = 0
    private var lastLut: FloatArray? = null
    private var fbo = 0
    private var fboTex = 0
    private var fboW = 0
    private var fboH = 0
    var maxTextureSize = 4096
        private set

    /** Reference size the effect radii and grain size are tuned for. */
    private const val REFERENCE_SIDE = 1600f

    private val maskTex = IntArray(4)

    suspend fun process(
        src: Bitmap,
        params: EditParams,
        lut: FloatArray,
        seed: Float = 0.37f,
        masks: List<Bitmap> = emptyList(),
    ): Bitmap =
        withContext(dispatcher) {
            init()
            val input = if (max(src.width, src.height) > maxTextureSize) {
                val s = maxTextureSize.toFloat() / max(src.width, src.height)
                Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
            } else src
            val bmp = if (input.config != Bitmap.Config.ARGB_8888) input.copy(Bitmap.Config.ARGB_8888, false) else input
            val w = bmp.width
            val h = bmp.height

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)

            if (lut !== lastLut) {
                GlUtil.uploadLut(lutTex, lut)
                lastLut = lut
            }
            // Render in horizontal strips: the whole photo is one texture (so blur and grain
            // are seamless), but only a strip-sized framebuffer is needed, even at 50 MP.
            val stripH = minOf(h, maxOf(256, 8_000_000 / w))
            ensureFbo(w, stripH)
            val scale = (max(w, h) / REFERENCE_SIDE).coerceIn(0.25f, 8f)
            val fx = Effects.from(params)
            val maskCount = minOf(masks.size, 4)
            for (i in 0 until maskCount) {
                if (maskTex[i] == 0) maskTex[i] = GlUtil.newTexture(GLES20.GL_TEXTURE_2D)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex[i])
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, masks[i], 0)
            }
            val maskParams = Effects.maskParams(params)
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            var y0 = 0
            while (y0 < h) {
                val rows = minOf(stripH, h - y0)
                // Shift the full-size viewport so framebuffer row 0 is photo row y0.
                GLES20.glViewport(0, -y0, w, h)
                program!!.draw(
                    srcTex = srcTex,
                    lutTex = lutTex,
                    fx = fx,
                    texelW = 1f / w,
                    texelH = 1f / h,
                    scale = scale,
                    aspect = w.toFloat() / h,
                    seed = seed,
                    masks = maskTex,
                    maskParams = maskParams,
                    maskCount = maskCount,
                )
                buf.position(y0 * w * 4)
                GLES20.glReadPixels(0, 0, w, rows, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
                y0 += rows
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            buf.position(0)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.copyPixelsFromBuffer(buf)
            if (bmp !== src && bmp !== input) bmp.recycle()
            if (input !== src) input.recycle()
            out
        }

    private fun init() {
        if (display != EGL14.EGL_NO_DISPLAY) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) { "No EGL config" }
        val config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        surface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }

        val maxSize = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0)
        maxTextureSize = maxSize[0].coerceIn(2048, 16384)

        program = GlProgram(external = false)
        srcTex = GlUtil.newTexture(GLES20.GL_TEXTURE_2D)
        lutTex = GlUtil.newTexture(GLES30.GL_TEXTURE_3D)
        val f = IntArray(1)
        GLES20.glGenFramebuffers(1, f, 0)
        fbo = f[0]
    }

    private fun ensureFbo(w: Int, h: Int) {
        if (w == fboW && h == fboH && fboTex != 0) return
        if (fboTex != 0) GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
        fboTex = GlUtil.newTexture(GLES20.GL_TEXTURE_2D)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        fboW = w; fboH = h
    }
}
