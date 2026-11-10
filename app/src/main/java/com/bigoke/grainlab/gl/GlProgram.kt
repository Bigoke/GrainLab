package com.bigoke.grainlab.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.bigoke.grainlab.edit.EditParams
import com.bigoke.grainlab.edit.LutBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Per-pixel effects that cannot live in the colour lookup table. */
data class Effects(
    val sharpen: Float = 0f,
    val clarity: Float = 0f,
    val noiseReduction: Float = 0f,
    val grain: Float = 0f,
    val grainSize: Float = 1.5f,
    val vignette: Float = 0f,
    val halation: Float = 0f,
    val lightLeak: Float = 0f,
) {
    companion object {
        fun from(p: EditParams) = Effects(
            sharpen = p["sharpen"], clarity = p["clarity"], noiseReduction = p["noiseReduction"],
            grain = p["grain"], grainSize = p["grainSize"], vignette = p["vignette"],
            halation = p["halation"], lightLeak = p["lightLeak"],
        )
    }
}

object Shaders {
    const val VERTEX = """#version 300 es
layout(location = 0) in vec4 aPos;
layout(location = 1) in vec4 aTex;
uniform mat4 uTexMatrix;
uniform vec2 uPosScale;
uniform float uRot;
out vec2 vTex;
void main() {
    vec2 p = aPos.xy;
    float c = cos(uRot);
    float s = sin(uRot);
    p = vec2(p.x * c + p.y * s, -p.x * s + p.y * c);
    gl_Position = vec4(p * uPosScale, 0.0, 1.0);
    vTex = (uTexMatrix * aTex).xy;
}
"""

    fun fragment(external: Boolean): String {
        val header = if (external)
            "#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\nprecision highp float;\nuniform samplerExternalOES uTex;\n"
        else
            "#version 300 es\nprecision highp float;\nuniform sampler2D uTex;\n"
        return header + FRAGMENT_BODY
    }

    private const val FRAGMENT_BODY = """
uniform highp sampler3D uLut;
uniform vec2 uTexel;
uniform float uScale;
uniform float uLutSize;
uniform float uSharpen;
uniform float uClarity;
uniform float uNR;
uniform float uGrain;
uniform float uGrainSize;
uniform float uVignette;
uniform float uHalation;
uniform float uLightLeak;
uniform float uSeed;
uniform float uAspect;
in vec2 vTex;
out vec4 fragColor;

vec3 src(vec2 uv) { return texture(uTex, uv).rgb; }
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32 + uSeed);
    return fract(p.x * p.y);
}

void main() {
    vec2 uv = vTex;
    vec2 tx = uTexel * uScale;
    vec3 c = src(uv);

    if (uNR > 0.001) {
        float sig = mix(0.03, 0.16, uNR);
        vec3 sum = c;
        float ws = 1.0;
        for (int j = -2; j <= 2; j++) {
            for (int i = -2; i <= 2; i++) {
                if (i == 0 && j == 0) continue;
                vec3 s = src(uv + vec2(float(i), float(j)) * tx);
                vec3 d = s - c;
                float w = exp(-dot(d, d) / (2.0 * sig * sig) - float(i * i + j * j) / 8.0);
                sum += s * w;
                ws += w;
            }
        }
        c = mix(c, sum / ws, clamp(uNR * 1.4, 0.0, 1.0));
    }

    if (uSharpen > 0.001) {
        vec3 b = (src(uv + vec2(tx.x, 0.0)) + src(uv - vec2(tx.x, 0.0)) +
                  src(uv + vec2(0.0, tx.y)) + src(uv - vec2(0.0, tx.y))) * 0.25;
        vec3 detail = src(uv) - b;
        float edge = smoothstep(0.01, 0.05, length(detail));
        c += detail * uSharpen * 2.0 * edge * (1.0 - 0.6 * uNR);
    }

    if (abs(uClarity) > 0.001) {
        vec2 r = tx * 8.0;
        vec3 b = src(uv + vec2(r.x, 0.0)) + src(uv - vec2(r.x, 0.0)) +
                 src(uv + vec2(0.0, r.y)) + src(uv - vec2(0.0, r.y)) +
                 src(uv + r * 0.7) + src(uv - r * 0.7) +
                 src(uv + vec2(r.x, -r.y) * 0.7) + src(uv + vec2(-r.x, r.y) * 0.7);
        b /= 8.0;
        float l = luma(c);
        float mid = 1.0 - pow(abs(l - 0.5) * 2.0, 2.0);
        c += (l - luma(b)) * uClarity * 0.8 * mid;
    }

    c = clamp(c, 0.0, 1.0);
    c = texture(uLut, c * ((uLutSize - 1.0) / uLutSize) + 0.5 / uLutSize).rgb;

    if (uHalation > 0.001) {
        vec2 r = tx * 10.0;
        float glow = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            glow += smoothstep(0.7, 1.0, luma(src(uv + vec2(cos(a), sin(a)) * r)));
        }
        glow /= 8.0;
        c += vec3(1.0, 0.35, 0.15) * glow * uHalation * 0.45;
    }

    if (uLightLeak > 0.001) {
        vec2 p = vec2(uv.x * uAspect, uv.y);
        float m = exp(-distance(p, vec2(-0.05 * uAspect, 0.25)) * 2.5) +
                  0.6 * exp(-distance(p, vec2(1.1 * uAspect, 0.9)) * 3.5);
        vec3 leak = clamp(vec3(1.0, 0.45, 0.15) * m * uLightLeak, 0.0, 1.0);
        c = 1.0 - (1.0 - c) * (1.0 - leak);
    }

    if (abs(uVignette) > 0.001) {
        float rr = length(uv - 0.5) * 1.414;
        float v = smoothstep(0.35, 1.0, rr);
        if (uVignette > 0.0) c *= 1.0 - uVignette * 0.75 * v;
        else c = mix(c, vec3(1.0), -uVignette * 0.5 * v);
    }

    if (uGrain > 0.001) {
        vec2 gp = floor(uv / uTexel / max(uGrainSize * uScale, 1.0));
        float g = hash(gp) + hash(gp + 17.0) - 1.0;
        float l = luma(c);
        float mask = 1.0 - pow(abs(l - 0.5) * 2.0, 2.0) * 0.7;
        c += g * uGrain * 0.18 * mask;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}

class GlProgram(private val external: Boolean) {
    private val id: Int
    private val uTexMatrix: Int
    private val uPosScale: Int
    private val uRot: Int
    private val uTex: Int
    private val uLut: Int
    private val uTexel: Int
    private val uScale: Int
    private val uLutSize: Int
    private val uSharpen: Int
    private val uClarity: Int
    private val uNR: Int
    private val uGrain: Int
    private val uGrainSize: Int
    private val uVignette: Int
    private val uHalation: Int
    private val uLightLeak: Int
    private val uSeed: Int
    private val uAspect: Int

    private val pos: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val tex: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    init {
        id = GlUtil.program(Shaders.VERTEX, Shaders.fragment(external))
        uTexMatrix = GLES20.glGetUniformLocation(id, "uTexMatrix")
        uPosScale = GLES20.glGetUniformLocation(id, "uPosScale")
        uRot = GLES20.glGetUniformLocation(id, "uRot")
        uTex = GLES20.glGetUniformLocation(id, "uTex")
        uLut = GLES20.glGetUniformLocation(id, "uLut")
        uTexel = GLES20.glGetUniformLocation(id, "uTexel")
        uScale = GLES20.glGetUniformLocation(id, "uScale")
        uLutSize = GLES20.glGetUniformLocation(id, "uLutSize")
        uSharpen = GLES20.glGetUniformLocation(id, "uSharpen")
        uClarity = GLES20.glGetUniformLocation(id, "uClarity")
        uNR = GLES20.glGetUniformLocation(id, "uNR")
        uGrain = GLES20.glGetUniformLocation(id, "uGrain")
        uGrainSize = GLES20.glGetUniformLocation(id, "uGrainSize")
        uVignette = GLES20.glGetUniformLocation(id, "uVignette")
        uHalation = GLES20.glGetUniformLocation(id, "uHalation")
        uLightLeak = GLES20.glGetUniformLocation(id, "uLightLeak")
        uSeed = GLES20.glGetUniformLocation(id, "uSeed")
        uAspect = GLES20.glGetUniformLocation(id, "uAspect")
    }

    fun draw(
        srcTex: Int,
        lutTex: Int,
        fx: Effects,
        texelW: Float,
        texelH: Float,
        scale: Float,
        aspect: Float,
        seed: Float,
        texMatrix: FloatArray = IDENTITY,
        posScaleX: Float = 1f,
        posScaleY: Float = 1f,
        rotation: Float = 0f,
    ) {
        GLES20.glUseProgram(id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, srcTex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        GLES20.glUniform1i(uLut, 1)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glUniform2f(uPosScale, posScaleX, posScaleY)
        GLES20.glUniform1f(uRot, rotation)
        GLES20.glUniform2f(uTexel, texelW, texelH)
        GLES20.glUniform1f(uScale, scale)
        GLES20.glUniform1f(uLutSize, LutBuilder.N.toFloat())
        GLES20.glUniform1f(uSharpen, fx.sharpen)
        GLES20.glUniform1f(uClarity, fx.clarity)
        GLES20.glUniform1f(uNR, fx.noiseReduction)
        GLES20.glUniform1f(uGrain, fx.grain)
        GLES20.glUniform1f(uGrainSize, fx.grainSize)
        GLES20.glUniform1f(uVignette, fx.vignette)
        GLES20.glUniform1f(uHalation, fx.halation)
        GLES20.glUniform1f(uLightLeak, fx.lightLeak)
        GLES20.glUniform1f(uSeed, seed)
        GLES20.glUniform1f(uAspect, aspect)

        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, pos)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
        GLES20.glDisableVertexAttribArray(1)
    }

    companion object {
        val IDENTITY: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

        private fun floatBuffer(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(a); position(0)
            }
    }
}

object GlUtil {
    private const val TAG = "GrainLabGL"

    fun program(vs: String, fs: String): Int {
        val v = shader(GLES20.GL_VERTEX_SHADER, vs)
        val f = shader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Program link failed: $log")
        }
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            Log.e(TAG, "Shader compile failed: $log")
            GLES20.glDeleteShader(s)
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }

    fun newTexture(target: Int): Int {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(target, t[0])
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        if (target == GLES30.GL_TEXTURE_3D) {
            GLES20.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_R, GLES20.GL_CLAMP_TO_EDGE)
        }
        return t[0]
    }

    /** Uploads a lookup table built by [LutBuilder] into a 3D texture. */
    fun uploadLut(texId: Int, lut: ByteArray) {
        val n = LutBuilder.N
        val buf = ByteBuffer.allocateDirect(lut.size).order(ByteOrder.nativeOrder())
        buf.put(lut); buf.position(0)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8, n, n, n, 0,
            GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, buf,
        )
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
    }
}
