package com.bigoke.grainlab.data

import android.content.Context
import android.content.SharedPreferences

/** Remembers camera and editor choices between sessions. */
object Settings {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) sp = context.applicationContext.getSharedPreferences("grainlab_settings", Context.MODE_PRIVATE)
    }

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun f(key: String, def: Float) = sp.getFloat(key, def)
    private fun s(key: String): String? = sp.getString(key, null)
    private fun put(block: SharedPreferences.Editor.() -> Unit) = sp.edit().apply(block).apply()

    // ---- Camera ----
    var lensBack: Boolean
        get() = b("lensBack", true)
        set(v) = put { putBoolean("lensBack", v) }
    var lensId: String?
        get() = s("lensId")
        set(v) = put { putString("lensId", v) }
    var videoMode: Boolean
        get() = b("videoMode", false)
        set(v) = put { putBoolean("videoMode", v) }
    var wide: Boolean
        get() = b("wide", false)
        set(v) = put { putBoolean("wide", v) }
    var flash: Int
        get() = i("flash", 2) // ImageCapture.FLASH_MODE_OFF
        set(v) = put { putInt("flash", v) }
    var timer: Int
        get() = i("timer", 0)
        set(v) = put { putInt("timer", v) }
    var grid: Boolean
        get() = b("grid", false)
        set(v) = put { putBoolean("grid", v) }
    var level: Boolean
        get() = b("level", false)
        set(v) = put { putBoolean("level", v) }
    var saveOriginal: Boolean
        get() = b("saveOriginal", false)
        set(v) = put { putBoolean("saveOriginal", v) }
    var videoQuality: Int
        get() = i("videoQuality", 1)
        set(v) = put { putInt("videoQuality", v) }
    var lookKey: String
        get() = s("lookKey") ?: "none"
        set(v) = put { putString("lookKey", v) }
    var lookStrength: Float
        get() = f("lookStrength", 1f)
        set(v) = put { putFloat("lookStrength", v) }
    var packFilter: String?
        get() = s("packFilter")
        set(v) = put { putString("packFilter", v) }

    var keepLocation: Boolean
        get() = b("keepLocation", false)
        set(v) = put { putBoolean("keepLocation", v) }
    var storeSafeNames: Boolean
        get() = b("storeSafeNames", false)
        set(v) = put { putBoolean("storeSafeNames", v) }
    var shutterSound: Boolean
        get() = b("shutterSound", true)
        set(v) = put { putBoolean("shutterSound", v) }
    var histogram: Boolean
        get() = b("histogram", false)
        set(v) = put { putBoolean("histogram", v) }
    var peaking: Boolean
        get() = b("peaking", false)
        set(v) = put { putBoolean("peaking", v) }
    var zebra: Boolean
        get() = b("zebra", false)
        set(v) = put { putBoolean("zebra", v) }
    var cine24: Boolean
        get() = b("cine24", false)
        set(v) = put { putBoolean("cine24", v) }
    var stabilise: Boolean
        get() = b("stabilise", true)
        set(v) = put { putBoolean("stabilise", v) }
    var ai: Boolean
        get() = b("ai", true)
        set(v) = put { putBoolean("ai", v) }
    var night: Boolean
        get() = b("night", false)
        set(v) = put { putBoolean("night", v) }
    var proMode: Boolean
        get() = b("proMode", false)
        set(v) = put { putBoolean("proMode", v) }
    var onboarded: Boolean
        get() = b("onboarded", false)
        set(v) = put { putBoolean("onboarded", v) }

    // ---- Editor ----
    var editorTool: Int
        get() = i("editorTool", 1)
        set(v) = put { putInt("editorTool", v) }
    var exportFormat: Int
        get() = i("exportFormat", 0)
        set(v) = put { putInt("exportFormat", v) }
    var exportSize: Int
        get() = i("exportSize", 0)
        set(v) = put { putInt("exportSize", v) }
    var watermark: Boolean
        get() = b("watermark", false)
        set(v) = put { putBoolean("watermark", v) }
    var watermarkText: String
        get() = s("watermarkText") ?: "Shot on GrainLab"
        set(v) = put { putString("watermarkText", v) }
    var clipWarning: Boolean
        get() = b("clipWarning", false)
        set(v) = put { putBoolean("clipWarning", v) }
    var exportQuality: Int
        get() = i("exportQuality", 92)
        set(v) = put { putInt("exportQuality", v) }
}
