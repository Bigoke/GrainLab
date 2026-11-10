package com.bigoke.grainlab.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.LutBuilder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Live camera viewfinder: camera frames -> the same film shader -> screen. */
class CameraGlRenderer(private val view: GLSurfaceView) : GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener {

    /** Called on the main thread each time a new camera SurfaceTexture is ready. */
    var onSurfaceReady: ((SurfaceTexture) -> Unit)? = null

    @Volatile var surfaceTexture: SurfaceTexture? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private var program: GlProgram? = null
    private var oesTex = 0
    private var lutTex = 0
    private val texMatrix = FloatArray(16)
    private var viewW = 1
    private var viewH = 1
    private var frame = 0

    @Volatile private var effects = Effects()
    @Volatile private var pendingLut: ByteArray? = null
    @Volatile var bufferWidth = 1440
    @Volatile var bufferHeight = 1080
    @Volatile var rotationDegrees = 90
    @Volatile var mirror = false

    fun setLook(params: EditParams, lut: ByteArray) {
        // Heavy per-pixel filters are applied after capture, not live, to keep the viewfinder smooth.
        effects = Effects.from(params).copy(noiseReduction = 0f, clarity = params["clarity"] * 0.5f)
        pendingLut = lut
        view.requestRender()
    }

    /** Width / height of the picture as seen on screen (after rotation). */
    fun contentAspect(): Float {
        val rotated = rotationDegrees % 180 != 0
        val w = if (rotated) bufferHeight else bufferWidth
        val h = if (rotated) bufferWidth else bufferHeight
        return w.toFloat() / h.toFloat()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GlProgram(external = true)
        oesTex = GlUtil.newTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        lutTex = GlUtil.newTexture(GLES30.GL_TEXTURE_3D)
        GlUtil.uploadLut(lutTex, pendingLut ?: identityLut)
        pendingLut = null
        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        main.post { onSurfaceReady?.invoke(st) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingLut?.let {
            GlUtil.uploadLut(lutTex, it)
            pendingLut = null
        }
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
        } catch (e: Exception) {
            return
        }
        st.getTransformMatrix(texMatrix)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val content = contentAspect()
        val viewAspect = viewW.toFloat() / viewH
        var sx = 1f
        var sy = 1f
        if (viewAspect > content) sx = content / viewAspect else sy = viewAspect / content
        // The quad is rotated first; swap the scale axes so the scaling applies on screen.
        val rotated = rotationDegrees % 180 != 0
        val posX = if (mirror) -sx else sx
        frame++
        program?.draw(
            srcTex = oesTex,
            lutTex = lutTex,
            fx = effects,
            texelW = 1f / bufferWidth,
            texelH = 1f / bufferHeight,
            scale = (maxOf(bufferWidth, bufferHeight) / 1600f).coerceIn(0.5f, 2f),
            aspect = if (rotated) bufferWidth.toFloat() / bufferHeight else content,
            seed = (frame % 97) * 0.013f,
            texMatrix = texMatrix,
            posScaleX = posX,
            posScaleY = sy,
            rotation = Math.toRadians(rotationDegrees.toDouble()).toFloat(),
        )
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        view.requestRender()
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }

    companion object {
        val identityLut: ByteArray by lazy { LutBuilder.build(EditParams()) }
    }
}
