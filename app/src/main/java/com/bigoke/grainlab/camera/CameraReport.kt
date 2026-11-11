package com.bigoke.grainlab.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size
import java.util.Locale
import kotlin.math.hypot

/** A plain-text description of every camera the phone offers to apps, for troubleshooting. */
object CameraReport {
    private fun largest(sizes: Array<Size>?): Size? = sizes?.maxByOrNull { it.width.toLong() * it.height }
    private fun fmt(s: Size?): String =
        if (s == null) "none" else String.format(Locale.US, "%dx%d (%.1f MP)", s.width, s.height, s.width * s.height / 1e6)

    private fun describe(sb: StringBuilder, cm: CameraManager, id: String, indent: String) {
        runCatching {
            val ch = cm.getCameraCharacteristics(id)
            val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                else -> "external"
            }
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val phys = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val eq = if (focal != null && phys != null) focal * 43.27f / hypot(phys.width, phys.height) else null
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            sb.append(indent).append("Camera ").append(id).append(" (").append(facing).append(")")
            if (eq != null) sb.append(String.format(Locale.US, ", %.0f mm equivalent", eq))
            sb.append('\n')
            sb.append(indent).append("  Standard photo: ").append(fmt(largest(map?.getOutputSizes(ImageFormat.JPEG)))).append('\n')
            sb.append(indent).append("  High-res photo: ").append(fmt(largest(map?.getHighResolutionOutputSizes(ImageFormat.JPEG)))).append('\n')
            sb.append(indent).append("  Sensor pixels: ").append(fmt(ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))).append('\n')
            if (Build.VERSION.SDK_INT >= 31) {
                val uhr = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)
                sb.append(indent).append("  Full-sensor mode for apps: ").append(if (uhr) "yes" else "no").append('\n')
                val maxMap = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                sb.append(indent).append("  Full-sensor photo: ").append(fmt(largest(maxMap?.getOutputSizes(ImageFormat.JPEG)))).append('\n')
                sb.append(indent).append("  Full-sensor pixels: ")
                    .append(fmt(ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION))).append('\n')
            }
            if (Build.VERSION.SDK_INT >= 30) {
                ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                    sb.append(indent).append(String.format(Locale.US, "  Zoom range: %.2fx to %.1fx\n", it.lower, it.upper))
                }
            }
            sb.append(indent).append("  Manual controls: ")
                .append(if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) "yes" else "no")
                .append(", RAW: ")
                .append(if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) "yes" else "no").append('\n')
            if (Build.VERSION.SDK_INT >= 28 && indent.isEmpty()) {
                val ids = ch.physicalCameraIds
                if (ids.isNotEmpty()) {
                    sb.append("  Lens modules behind it: ").append(ids.joinToString()).append('\n')
                    ids.forEach { describe(sb, cm, it, "    ") }
                }
            }
        }.onFailure { sb.append(indent).append("Camera ").append(id).append(": not readable (").append(it.message).append(")\n") }
    }

    fun build(context: Context, current: String): String {
        val sb = StringBuilder()
        sb.append("GrainLab camera report\n")
        sb.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE).append('\n')
        sb.append(current).append("\n\n")
        val cm = context.getSystemService(CameraManager::class.java)
        if (cm == null) return sb.append("Camera service not available").toString()
        runCatching { cm.cameraIdList.forEach { describe(sb, cm, it, ""); sb.append('\n') } }
            .onFailure { sb.append("Could not list cameras: ").append(it.message) }
        return sb.toString()
    }
}
