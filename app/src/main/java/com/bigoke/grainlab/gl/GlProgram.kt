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
    val texture: Float = 0f,
    val dehaze: Float = 0f,
    val noiseReduction: Float = 0f,
    val grain: Float = 0f,
    val grainSize: Float = 1.5f,
    val vignette: Float = 0f,
    val halation: Float = 0f,
    val lightLeak: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    /** Viewfinder only: highlight in-focus edges in red. */
    val peaking: Float = 0f,
    /** Viewfinder only: stripes over blown-out highlights. */
    val zebra: Float = 0f,
) {
    companion object {
        fun from(p: EditParams) = Effects(
            sharpen = p["sharpen"], clarity = p["clarity"], texture = p["texture"], dehaze = p["dehaze"],
            noiseReduction = p["noiseReduction"],
            grain = p["grain"], grainSize = p["grainSize"], vignette = p["vignette"],
            halation = p["halation"], lightLeak = p["lightLeak"],
            shadows = p["shadows"], highlights = p["highlights"],
        )

        /** Exposure, contrast, saturation, temperature for each mask (4 floats each). */
        fun maskParams(p: EditParams): FloatArray {
            val out = FloatArray(16)
            p.masks.take(4).forEachIndexed { i, m ->
                out[i * 4] = m.exposure; out[i * 4 + 1] = m.contrast
                out[i * 4 + 2] = m.saturation; out[i * 4 + 3] = m.temperature
            }
            return out
        }
    }
}

object Shaders {
    const val VERTEX = """#version 300 es
layout(location = 0) in vec4 aPos;
layout(location = 1) in vec4 aTex;
uniform mat4 uTexMatrix;
uniform vec2 uPosScale;
uniform vec2 uPosOffset;
uniform float uRot;
out vec2 vTex;
void main() {
    vec2 p = aPos.xy;
    float c = cos(uRot);
    float s = sin(uRot);
    p = vec2(p.x * c + p.y * s, -p.x * s + p.y * c);
    gl_Position = vec4(p * uPosScale + uPosOffset, 0.0, 1.0);
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
uniform float uTexture;
uniform float uDehaze;
uniform float uNR;
uniform float uGrain;
uniform float uGrainSize;
uniform float uVignette;
uniform float uHalation;
uniform float uLightLeak;
uniform float uShadows;
uniform float uHighlights;
uniform float uPeaking;
uniform float uZebra;
uniform sampler2D uMask0;
uniform sampler2D uMask1;
uniform sampler2D uMask2;
uniform sampler2D uMask3;
uniform vec4 uMaskP0;
uniform vec4 uMaskP1;
uniform vec4 uMaskP2;
uniform vec4 uMaskP3;
uniform int uMaskCount;
uniform float uSplit;
uniform float uClip;
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

// Local adjustment: exposure, contrast, saturation, temperature, weighted by the mask.
vec3 local(vec3 c, float w, vec4 p) {
    if (w <= 0.002) return c;
    c *= pow(2.0, p.x * w);
    c = (c - 0.5) * (1.0 + p.y * w * 0.8) + 0.5;
    float l = luma(c);
    c = l + (c - l) * (1.0 + p.z * w);
    c.r *= 1.0 + 0.15 * p.w * w;
    c.b *= 1.0 - 0.15 * p.w * w;
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec2 uv = vTex;
    if (uv.x < uSplit) {
        // Before/after: the left part shows the untouched photo.
        fragColor = vec4(src(uv), 1.0);
        return;
    }
    vec2 tx = uTexel * uScale;
    vec3 c = src(uv);

    if (uNR > 0.001) {
        float sig = mix(0.03, 0.16, uNR);
        vec3 sum = c;
        float ws = 1.0;
        for (int j = -2; j <= 2; j++) {
            for (int i = -2; i <= 2; i++) {
                if (i == 0 && j == 0) continue;
                vec3 s = src(uv + vec2(float(i), float(j)) * tx * 1.5);
                vec3 d = s - c;
                float w = exp(-dot(d, d) / (2.0 * sig * sig) - float(i * i + j * j) / 8.0);
                sum += s * w;
                ws += w;
            }
        }
        // Colour speckle is removed harder than brightness detail, which keeps photos crisp.
        vec3 d = sum / ws;
        float ly = mix(luma(c), luma(d), clamp(uNR * 1.2, 0.0, 1.0));
        vec3 ch = mix(c - luma(c), d - luma(d), clamp(uNR * 2.5, 0.0, 1.0));
        c = ly + ch;
    }

    if (abs(uShadows) > 0.001 || abs(uHighlights) > 0.001) {
        // Local shadows / highlights: the mask comes from the blurred surroundings, so lifted
        // shadows keep their own contrast instead of going flat.
        vec2 r = tx * 24.0;
        float bl = luma(c) * 0.2;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            bl += luma(src(uv + vec2(cos(a), sin(a)) * r)) * 0.1;
        }
        float sm = (1.0 - bl) * (1.0 - bl) * smoothstep(0.0, 0.15, bl + 0.1);
        float hm = bl * bl;
        c += 0.32 * uShadows * sm + 0.3 * uHighlights * hm;
    }

    if (abs(uTexture) > 0.001) {
        vec2 r = tx * 3.0;
        vec3 b = (src(uv + vec2(r.x, 0.0)) + src(uv - vec2(r.x, 0.0)) +
                  src(uv + vec2(0.0, r.y)) + src(uv - vec2(0.0, r.y)) +
                  src(uv + r * 0.7) + src(uv - r * 0.7) +
                  src(uv + vec2(r.x, -r.y) * 0.7) + src(uv + vec2(-r.x, r.y) * 0.7)) / 8.0;
        vec3 d = c - b;
        float fine = 1.0 - smoothstep(0.06, 0.22, length(d));
        if (uTexture > 0.0) c += d * uTexture * 1.6 * fine;
        else c = mix(c, b, -uTexture * 0.85 * fine);
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

    if (abs(uDehaze) > 0.001) {
        if (uDehaze > 0.0) {
            vec2 r = tx * 14.0;
            float dc = min(min(c.r, c.g), c.b);
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398;
                vec3 s = src(uv + vec2(cos(a), sin(a)) * r);
                dc = min(dc, min(min(s.r, s.g), s.b));
            }
            float A = 0.95;
            float t = max(1.0 - uDehaze * 0.9 * dc / A, 0.3);
            c = (c - A * (1.0 - t)) / t;
        } else {
            c = mix(c, vec3(0.82), -uDehaze * 0.35);
        }
    }

    c = clamp(c, 0.0, 1.0);
    c = texture(uLut, c * ((uLutSize - 1.0) / uLutSize) + 0.5 / uLutSize).rgb;

    if (uMaskCount > 0) c = local(c, texture(uMask0, uv).r, uMaskP0);
    if (uMaskCount > 1) c = local(c, texture(uMask1, uv).r, uMaskP1);
    if (uMaskCount > 2) c = local(c, texture(uMask2, uv).r, uMaskP2);
    if (uMaskCount > 3) c = local(c, texture(uMask3, uv).r, uMaskP3);

    if (uHalation > 0.001) {
        // Two rings of samples approximate the soft red glow film gets around bright lights.
        float glow = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            vec2 d = vec2(cos(a), sin(a));
            glow += smoothstep(0.72, 1.0, luma(src(uv + d * tx * 6.0))) * 0.6;
            glow += smoothstep(0.72, 1.0, luma(src(uv + d.yx * vec2(1.0, -1.0) * tx * 16.0))) * 0.4;
        }
        glow /= 8.0;
        c = 1.0 - (1.0 - c) * (1.0 - vec3(1.0, 0.32, 0.12) * glow * uHalation * 0.7);
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
        // Multi-scale grain: fine and coarse clumps, strongest in the mid-tones and shadows,
        // with a little colour variation like real dye clouds.
        float gs = max(uGrainSize * uScale, 1.0);
        vec2 p1 = floor(uv / uTexel / gs);
        vec2 p2 = floor(uv / uTexel / (gs * 2.3));
        float fine = hash(p1) + hash(p1 + 17.0) - 1.0;
        float coarse = hash(p2 + 31.0) + hash(p2 + 53.0) - 1.0;
        float g = fine * 0.65 + coarse * 0.45;
        vec3 chroma = vec3(hash(p1 + 7.0), hash(p1 + 11.0), hash(p1 + 13.0)) - 0.5;
        float l = luma(c);
        float mask = smoothstep(0.0, 0.18, l) * (1.0 - smoothstep(0.55, 1.0, l) * 0.75);
        c += (vec3(g) + chroma * 0.25) * uGrain * 0.2 * mask;
    }

    if (uPeaking > 0.5) {
        float ex = luma(src(uv + vec2(tx.x, 0.0))) - luma(src(uv - vec2(tx.x, 0.0)));
        float ey = luma(src(uv + vec2(0.0, tx.y))) - luma(src(uv - vec2(0.0, tx.y)));
        if (length(vec2(ex, ey)) > 0.16) c = mix(c, vec3(1.0, 0.12, 0.08), 0.85);
    }
    if (uZebra > 0.5 && luma(c) > 0.95 && mod(gl_FragCoord.x + gl_FragCoord.y, 16.0) < 7.0) {
        c = vec3(0.05);
    }

    if (uClip > 0.5) {
        // Clipping warning: blown highlights red, crushed shadows blue.
        if (max(c.r, max(c.g, c.b)) >= 0.995) c = vec3(1.0, 0.1, 0.1);
        else if (min(c.r, min(c.g, c.b)) <= 0.004) c = vec3(0.1, 0.3, 1.0);
    }

    // Dither before 8-bit output so smooth gradients never show steps.
    c += (hash(gl_FragCoord.xy + 91.7) - 0.5) / 255.0;

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}

class GlProgram(private val external: Boolean) {
    private val id: Int
    private val uTexMatrix: Int
    private val uPosScale: Int
    private val uPosOffset: Int
    private val uRot: Int
    private val uTex: Int
    private val uLut: Int
    private val uTexel: Int
    private val uScale: Int
    private val uLutSize: Int
    private val uSharpen: Int
    private val uClarity: Int
    private val uTextureAmt: Int
    private val uDehaze: Int
    private val uNR: Int
    private val uGrain: Int
    private val uGrainSize: Int
    private val uVignette: Int
    private val uHalation: Int
    private val uLightLeak: Int
    private val uShadows: Int
    private val uHighlights: Int
    private val uPeaking: Int
    private val uMaskTex = IntArray(4)
    private val uMaskP = IntArray(4)
    private val uMaskCount: Int
    private val uSplit: Int
    private val uClip: Int
    private val uZebra: Int
    private val uSeed: Int
    private val uAspect: Int

    private val pos: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val tex: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    init {
        id = GlUtil.program(Shaders.VERTEX, Shaders.fragment(external))
        uTexMatrix = GLES20.glGetUniformLocation(id, "uTexMatrix")
        uPosScale = GLES20.glGetUniformLocation(id, "uPosScale")
        uPosOffset = GLES20.glGetUniformLocation(id, "uPosOffset")
        uRot = GLES20.glGetUniformLocation(id, "uRot")
        uTex = GLES20.glGetUniformLocation(id, "uTex")
        uLut = GLES20.glGetUniformLocation(id, "uLut")
        uTexel = GLES20.glGetUniformLocation(id, "uTexel")
        uScale = GLES20.glGetUniformLocation(id, "uScale")
        uLutSize = GLES20.glGetUniformLocation(id, "uLutSize")
        uSharpen = GLES20.glGetUniformLocation(id, "uSharpen")
        uClarity = GLES20.glGetUniformLocation(id, "uClarity")
        uTextureAmt = GLES20.glGetUniformLocation(id, "uTexture")
        uDehaze = GLES20.glGetUniformLocation(id, "uDehaze")
        uNR = GLES20.glGetUniformLocation(id, "uNR")
        uGrain = GLES20.glGetUniformLocation(id, "uGrain")
        uGrainSize = GLES20.glGetUniformLocation(id, "uGrainSize")
        uVignette = GLES20.glGetUniformLocation(id, "uVignette")
        uHalation = GLES20.glGetUniformLocation(id, "uHalation")
        uLightLeak = GLES20.glGetUniformLocation(id, "uLightLeak")
        uShadows = GLES20.glGetUniformLocation(id, "uShadows")
        uHighlights = GLES20.glGetUniformLocation(id, "uHighlights")
        uPeaking = GLES20.glGetUniformLocation(id, "uPeaking")
        for (i in 0..3) {
            uMaskTex[i] = GLES20.glGetUniformLocation(id, "uMask$i")
            uMaskP[i] = GLES20.glGetUniformLocation(id, "uMaskP$i")
        }
        uMaskCount = GLES20.glGetUniformLocation(id, "uMaskCount")
        uSplit = GLES20.glGetUniformLocation(id, "uSplit")
        uClip = GLES20.glGetUniformLocation(id, "uClip")
        uZebra = GLES20.glGetUniformLocation(id, "uZebra")
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
        posOffsetY: Float = 0f,
        posOffsetX: Float = 0f,
        masks: IntArray? = null,
        maskParams: FloatArray? = null,
        maskCount: Int = 0,
        split: Float = -1f,
        clip: Boolean = false,
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
        GLES20.glUniform2f(uPosOffset, posOffsetX, posOffsetY)
        val count = if (masks == null || maskParams == null) 0 else minOf(maskCount, masks.size, 4)
        for (i in 0..3) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (i < count) masks!![i] else 0)
            GLES20.glUniform1i(uMaskTex[i], 2 + i)
            if (i < count) GLES20.glUniform4f(uMaskP[i], maskParams!![i * 4], maskParams[i * 4 + 1], maskParams[i * 4 + 2], maskParams[i * 4 + 3])
        }
        GLES20.glUniform1i(uMaskCount, count)
        GLES20.glUniform1f(uSplit, split)
        GLES20.glUniform1f(uClip, if (clip) 1f else 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1f(uRot, rotation)
        GLES20.glUniform2f(uTexel, texelW, texelH)
        GLES20.glUniform1f(uScale, scale)
        GLES20.glUniform1f(uLutSize, LutBuilder.N.toFloat())
        GLES20.glUniform1f(uSharpen, fx.sharpen)
        GLES20.glUniform1f(uClarity, fx.clarity)
        GLES20.glUniform1f(uTextureAmt, fx.texture)
        GLES20.glUniform1f(uDehaze, fx.dehaze)
        GLES20.glUniform1f(uNR, fx.noiseReduction)
        GLES20.glUniform1f(uGrain, fx.grain)
        GLES20.glUniform1f(uGrainSize, fx.grainSize)
        GLES20.glUniform1f(uVignette, fx.vignette)
        GLES20.glUniform1f(uHalation, fx.halation)
        GLES20.glUniform1f(uLightLeak, fx.lightLeak)
        GLES20.glUniform1f(uShadows, fx.shadows)
        GLES20.glUniform1f(uHighlights, fx.highlights)
        GLES20.glUniform1f(uPeaking, fx.peaking)
        GLES20.glUniform1f(uZebra, fx.zebra)
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
    fun uploadLut(texId: Int, lut: FloatArray) {
        val n = LutBuilder.N
        val buf = ByteBuffer.allocateDirect(lut.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (v in lut) buf.put(android.util.Half.toHalf(v))
        buf.position(0)
        GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F, n, n, n, 0,
            GLES20.GL_RGB, GLES30.GL_HALF_FLOAT, buf,
        )
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
    }
}
