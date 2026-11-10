package com.bigoke.grainlab.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Manual camera settings. Null means automatic. */
data class Manual(
    val iso: Int? = null,
    val shutterNs: Long? = null,
    /** Focus distance in dioptres (0 = infinity); null = autofocus. */
    val focus: Float? = null,
    val wb: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO,
) {
    val isAuto get() = iso == null && shutterNs == null && focus == null && wb == CameraMetadata.CONTROL_AWB_MODE_AUTO
}

/** What this camera allows to be set by hand. */
data class ManualRanges(
    val manualSensor: Boolean,
    val isoMin: Int, val isoMax: Int,
    val shutterMinNs: Long, val shutterMaxNs: Long,
    val minFocusDioptre: Float,
) {
    companion object {
        @OptIn(ExperimentalCamera2Interop::class)
        fun read(camera: Camera): ManualRanges {
            val info = Camera2CameraInfo.from(camera.cameraInfo)
            val caps = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            val manual = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val iso = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exp = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val focus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            return ManualRanges(
                manualSensor = manual && iso != null && exp != null,
                isoMin = iso?.lower ?: 100, isoMax = iso?.upper ?: 3200,
                shutterMinNs = exp?.lower ?: 100_000L,
                // Cap at half a second so the viewfinder stays usable.
                shutterMaxNs = minOf(exp?.upper ?: 250_000_000L, 500_000_000L),
                minFocusDioptre = focus,
            )
        }
    }
}

object ProCamera {
    val wbModes = listOf(
        CameraMetadata.CONTROL_AWB_MODE_AUTO to "Auto",
        CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to "Daylight",
        CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Cloudy",
        CameraMetadata.CONTROL_AWB_MODE_SHADE to "Shade",
        CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to "Tungsten",
        CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to "Fluorescent",
    )

    /** Sends the manual settings to the camera (or clears them when everything is automatic). */
    @OptIn(ExperimentalCamera2Interop::class)
    fun apply(camera: Camera, m: Manual, ranges: ManualRanges) {
        val b = CaptureRequestOptions.Builder()
        if (ranges.manualSensor && (m.iso != null || m.shutterNs != null)) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, m.iso ?: 200)
            val t = m.shutterNs ?: 16_666_667L
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, t)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(t, 33_333_333L))
        }
        if (m.focus != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, m.focus)
        }
        b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, m.wb)
        Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(b.build())
    }

    // Log-scale slider helpers.
    fun toSlider(v: Double, min: Double, max: Double) = ((ln(v) - ln(min)) / (ln(max) - ln(min))).toFloat().coerceIn(0f, 1f)
    fun fromSlider(t: Float, min: Double, max: Double) = exp(ln(min) + (ln(max) - ln(min)) * t)

    fun isoFromSlider(t: Float, r: ManualRanges) = fromSlider(t, r.isoMin.toDouble(), r.isoMax.toDouble()).roundToInt()
    fun shutterFromSlider(t: Float, r: ManualRanges) = fromSlider(t, r.shutterMinNs.toDouble(), r.shutterMaxNs.toDouble()).roundToLong()

    fun shutterLabel(ns: Long): String {
        val s = ns / 1e9
        return if (s >= 0.3) String.format(Locale.US, "%.1fs", s) else "1/${(1 / s).roundToInt()}"
    }

    fun focusLabel(d: Float): String = when {
        d <= 0.01f -> "∞"
        1f / d >= 1f -> String.format(Locale.US, "%.1fm", 1f / d)
        else -> "${(100f / d).roundToInt()}cm"
    }
}

/** Volume buttons act as a shutter while the camera is on screen. */
object CameraKeys {
    @Volatile var active = false
    val presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/** Live brightness histogram from the camera's preview frames (64 bins, about 6 per second). */
class HistogramAnalyzer : ImageAnalysis.Analyzer {
    private val _bins = MutableStateFlow(IntArray(64))
    val bins: StateFlow<IntArray> = _bins.asStateFlow()
    private var frame = 0

    override fun analyze(image: ImageProxy) {
        try {
            if (frame++ % 5 != 0) return
            val plane = image.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            val w = image.width
            val h = image.height
            val out = IntArray(64)
            var y = 0
            while (y < h) {
                var x = 0
                val row = y * rowStride
                while (x < w) {
                    val v = buf.get(row + x * pixStride).toInt() and 0xFF
                    out[v shr 2]++
                    x += 8
                }
                y += 8
            }
            _bins.value = out
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }
}
