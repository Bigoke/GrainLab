package com.bigoke.grainlab.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.roundToInt

/**
 * What photo sizes a camera offers to apps.
 *
 * Phones like the OnePlus 15 have 50 MP "quad-Bayer" sensors that normally combine 4 pixels into
 * one (12.5 MP) for cleaner, faster shots. The full 50 MP is listed separately, either as a
 * "high resolution" size or in the sensor's "maximum resolution" mode.
 */
data class SensorInfo(
    /** Largest normal photo size (usually the binned 12.5 MP). */
    val standard: Size?,
    /** Largest size the phone offers apps at all, including high-resolution modes. */
    val full: Size?,
    /** The sensor's own pixel count in maximum-resolution mode, even if apps cannot use it. */
    val sensorMax: Size?,
) {
    val standardMp get() = mp(standard)
    val fullMp get() = mp(full)
    val sensorMp get() = mp(sensorMax)

    /** True when a clearly larger photo than the standard one is available to apps. */
    val fullAvailable get() = fullMp >= standardMp * 1.4f && fullMp > 0f

    companion object {
        fun mp(s: Size?): Float = if (s == null) 0f else s.width * s.height / 1_000_000f
        fun label(mp: Float): String = "${mp.roundToInt()} MP"

        private fun largest(sizes: Array<Size>?): Size? = sizes?.maxByOrNull { it.width.toLong() * it.height }

        @OptIn(ExperimentalCamera2Interop::class)
        fun read(info: CameraInfo): SensorInfo {
            val c2 = Camera2CameraInfo.from(info)
            val map = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val std = largest(map?.getOutputSizes(ImageFormat.JPEG))
            val high = largest(map?.getHighResolutionOutputSizes(ImageFormat.JPEG))
            var maxMode: Size? = null
            var sensor: Size? = null
            if (Build.VERSION.SDK_INT >= 31) {
                val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)) {
                    val maxMap = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                    maxMode = largest(maxMap?.getOutputSizes(ImageFormat.JPEG))
                        ?: largest(maxMap?.getOutputSizes(ImageFormat.YUV_420_888))
                }
                sensor = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION)
            }
            if (sensor == null) sensor = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val full = listOfNotNull(std, high, maxMode).maxByOrNull { it.width.toLong() * it.height }
            return SensorInfo(std, full, sensor)
        }
    }
}
