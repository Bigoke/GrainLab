package com.bigoke.grainlab.data

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A lens button. Either a zoom preset on the main (logical) camera, which lets the phone switch
 * to its ultra-wide or telephoto module itself, or a separate camera id the phone exposes.
 */
data class Lens(
    val id: String,
    val label: String,
    /** Camera id to open, or null for the main back camera. */
    val cameraId: String?,
    /** Zoom ratio to set after opening. */
    val zoom: Float,
    /** Field of view relative to the main lens (0.6 = ultra-wide, 3 = telephoto). */
    val ratio: Float,
    /** 35mm-equivalent focal length when known. */
    val equivalentMm: Int? = null,
)

object LensCatalog {

    fun label(r: Float): String {
        val rounded = (r * 10f).roundToInt() / 10f
        return if (abs(rounded - rounded.roundToInt()) < 0.05f) "${rounded.roundToInt()}x"
        else String.format(Locale.US, "%.1fx", rounded)
    }

    private fun equivalent(ch: CameraCharacteristics): Float? {
        val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val diag = hypot(size.width, size.height)
        if (diag <= 0f) return null
        return focal * 43.27f / diag
    }

    /** The id CameraX opens for the default back camera (the first back-facing id). */
    fun mainBackId(context: Context): String? {
        val cm = context.getSystemService(CameraManager::class.java) ?: return null
        return runCatching {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    /**
     * The main back camera's full zoom range as the phone reports it. Read from the camera's
     * description, not the running session, because full-sensor mode narrows the live range.
     */
    fun zoomRange(context: Context): Pair<Float, Float>? {
        if (Build.VERSION.SDK_INT < 30) return null
        val cm = context.getSystemService(CameraManager::class.java) ?: return null
        val id = mainBackId(context) ?: return null
        val r = runCatching { cm.getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) }.getOrNull() ?: return null
        return r.lower to r.upper
    }

    /**
     * Builds the lens buttons for the back camera. [minZoom]/[maxZoom] come from the main camera's
     * zoom range, which on most phones already spans the ultra-wide and telephoto modules.
     */
    fun backLenses(context: Context, minZoom: Float, maxZoom: Float): List<Lens> {
        val cm = context.getSystemService(CameraManager::class.java)
        val lenses = ArrayList<Lens>()
        val mainId = mainBackId(context)
        var mainEq: Float? = null
        val physicalIds = HashSet<String>()

        if (cm != null && mainId != null) {
            runCatching {
                val mainCh = cm.getCameraCharacteristics(mainId)
                mainEq = equivalent(mainCh)
                lenses.add(Lens("z1", "1x", null, 1f, 1f, mainEq?.roundToInt()))

                // Physical modules behind the logical main camera, reached by zoom ratio.
                if (Build.VERSION.SDK_INT >= 28) {
                    physicalIds.addAll(mainCh.physicalCameraIds)
                    val base = mainEq
                    if (base != null) {
                        for (pid in mainCh.physicalCameraIds) {
                            val e = runCatching { equivalent(cm.getCameraCharacteristics(pid)) }.getOrNull() ?: continue
                            val r = e / base
                            if (abs(r - 1f) < 0.15f) continue
                            val zoom = r.coerceIn(minZoom, maxZoom)
                            if (abs(zoom - r) > 0.1f) continue
                            if (lenses.none { abs(it.ratio - r) < 0.15f }) {
                                lenses.add(Lens("z" + label(r), label(r), null, r, r, e.roundToInt()))
                            }
                        }
                    }
                }

                // Other back cameras the phone exposes as separate ids.
                val base = mainEq
                for (id in cm.cameraIdList) {
                    if (id == mainId || id in physicalIds) continue
                    val ch = cm.getCameraCharacteristics(id)
                    if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
                    val e = equivalent(ch) ?: continue
                    val r = if (base != null) e / base else 1f
                    if (lenses.none { abs(it.ratio - r) < 0.15f }) {
                        lenses.add(Lens("c$id", label(r), id, 1f, r, e.roundToInt()))
                    }
                }
            }
        }
        if (lenses.isEmpty()) lenses.add(Lens("z1", "1x", null, 1f, 1f))

        // Ultra-wide reachable through the zoom range even if the module is hidden.
        if (minZoom < 0.95f && lenses.none { it.ratio < 0.95f }) {
            lenses.add(Lens("z" + label(minZoom), label(minZoom), null, minZoom, minZoom))
        }
        // A 2x crop is the most-used preset; add it when no lens sits near 2x.
        if (maxZoom >= 2f && lenses.none { abs(it.ratio - 2f) < 0.4f }) {
            lenses.add(Lens("z2", "2x", null, 2f, 2f))
        }
        // Long telephoto through the zoom range when no module was reported.
        if (maxZoom >= 3f && lenses.none { it.ratio >= 2.8f }) {
            val t = if (maxZoom >= 5f) 5f else 3f
            lenses.add(Lens("z" + label(t), label(t), null, t, t))
        }
        return lenses.sortedBy { it.ratio }
    }
}
