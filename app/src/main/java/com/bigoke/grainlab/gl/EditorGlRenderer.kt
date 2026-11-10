package com.bigoke.grainlab.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Live editor preview drawn straight to the screen by the GPU. Slider changes only update
 * shader values (or the lookup table), so the photo responds instantly instead of being
 * re-rendered and copied back for every movement.
 */
class EditorGlRenderer(private val view: GLSurfaceView) : GLSurfaceView.Renderer {
    private var program: GlProgram? = null
    private var imageTex = 0
    private var lutTex = 0
    private val maskTex = IntArray(4)
    private var imageW = 1
    private var imageH = 1
    private var viewW = 1
    private var viewH = 1
    private val flipY = FloatArray(16).also {
        Matrix.setIdentityM(it, 0)
        Matrix.translateM(it, 0, 0f, 1f, 0f)
        Matrix.scaleM(it, 0, 1f, -1f, 1f)
    }

    @Volatile private var pendingImage: Bitmap? = null
    @Volatile private var pendingLut: FloatArray? = null
    @Volatile private var pendingMasks: List<Bitmap>? = null
    @Volatile private var hasImage = false

    @Volatile var effects = Effects()
        set(v) { field = v; view.requestRender() }
    @Volatile var maskParams = FloatArray(16)
        set(v) { field = v; view.requestRender() }
    @Volatile private var maskCount = 0
    /** Share of the width (from the left) shown without edits; negative = none. */
    @Volatile var split = -1f
        set(v) { field = v; view.requestRender() }
    @Volatile var clip = false
        set(v) { field = v; view.requestRender() }
    @Volatile var zoom = 1f
        set(v) { field = v; view.requestRender() }
    @Volatile var panX = 0f
        set(v) { field = v; view.requestRender() }
    @Volatile var panY = 0f
        set(v) { field = v; view.requestRender() }

    fun setImage(b: Bitmap) {
        pendingImage = b
        view.requestRender()
    }

    fun setLut(lut: FloatArray) {
        pendingLut = lut
        view.requestRender()
    }

    fun setMasks(masks: List<Bitmap>) {
        pendingMasks = masks
        view.requestRender()
    }

    /** Where the photo sits in the view (left, top, width, height) at zoom 1, in pixels. */
    fun fitRect(vw: Float, vh: Float, iw: Float, ih: Float): FloatArray {
        val ia = iw / ih
        val va = vw / vh
        val w = if (va > ia) vh * ia else vw
        val h = if (va > ia) vh else vw / ia
        return floatArrayOf((vw - w) / 2f, (vh - h) / 2f, w, h)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GlProgram(external = false)
        imageTex = GlUtil.newTexture(GLES20.GL_TEXTURE_2D)
        lutTex = GlUtil.newTexture(GLES30.GL_TEXTURE_3D)
        GlUtil.uploadLut(lutTex, CameraGlRenderer.identityLut)
        for (i in 0..3) maskTex[i] = GlUtil.newTexture(GLES20.GL_TEXTURE_2D)
        // The context is new: anything uploaded before must be sent again.
        hasImage = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingImage?.let { b ->
            if (!b.isRecycled) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTex)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
                imageW = b.width
                imageH = b.height
                hasImage = true
            }
            pendingImage = null
        }
        pendingLut?.let {
            GlUtil.uploadLut(lutTex, it)
            pendingLut = null
        }
        pendingMasks?.let { list ->
            val n = minOf(list.size, 4)
            for (i in 0 until n) {
                if (list[i].isRecycled) continue
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex[i])
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, list[i], 0)
            }
            maskCount = n
            pendingMasks = null
        }

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!hasImage) return

        val r = fitRect(viewW.toFloat(), viewH.toFloat(), imageW.toFloat(), imageH.toFloat())
        val sx = r[2] / viewW * zoom
        val sy = r[3] / viewH * zoom
        val scale = (max(imageW, imageH) / 1600f).coerceIn(0.25f, 8f)
        program?.draw(
            srcTex = imageTex,
            lutTex = lutTex,
            fx = effects,
            texelW = 1f / imageW,
            texelH = 1f / imageH,
            scale = scale,
            aspect = imageW.toFloat() / imageH,
            seed = 0.37f,
            texMatrix = flipY,
            posScaleX = sx,
            posScaleY = sy,
            posOffsetX = 2f * panX / viewW,
            posOffsetY = -2f * panY / viewH,
            masks = maskTex,
            maskParams = maskParams,
            maskCount = maskCount,
            split = split,
            clip = clip,
        )
    }

    /** The photo must be re-sent after the view is recreated (e.g. after rotating). */
    fun needsImage() = !hasImage
}
