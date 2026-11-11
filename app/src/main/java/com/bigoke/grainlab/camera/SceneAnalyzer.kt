package com.bigoke.grainlab.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.bigoke.grainlab.edit.Adjustments
import com.bigoke.grainlab.edit.EditParams
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/** What the camera thinks it is looking at, and the gentle tuning that goes with it. */
enum class Scene(val label: String, val adds: Map<String, Float> = emptyMap()) {
    NONE(""),
    NIGHT("Night", mapOf("noiseReduction" to 0.25f, "shadows" to 0.1f)),
    MOTION("Action", mapOf("sharpen" to 0.1f)),
    TEXT("Text", mapOf("contrast" to 0.2f, "clarity" to 0.25f, "sharpen" to 0.3f, "whites" to 0.15f)),
    FOOD("Food", mapOf("saturation" to 0.12f, "vibrance" to 0.15f, "temperature" to 0.08f, "clarity" to 0.1f)),
    PORTRAIT("Portrait", mapOf("texture" to -0.15f, "shadows" to 0.1f, "highlights" to -0.1f)),
    PET("Pet", mapOf("texture" to 0.15f, "sharpen" to 0.15f)),
    PLANT("Plants", mapOf("vibrance" to 0.2f, "clarity" to 0.08f)),
    LANDSCAPE("Landscape", mapOf("vibrance" to 0.2f, "dehaze" to 0.12f, "clarity" to 0.1f));

    /** Adds this scene's tuning on top of the chosen look, staying inside each slider's range. */
    fun tune(p: EditParams): EditParams {
        var out = p
        for ((k, v) in adds) {
            val spec = Adjustments.spec(k) ?: continue
            out = out.with(k, (p[k] + v).coerceIn(spec.min, spec.max))
        }
        return out
    }
}

/**
 * Watches the viewfinder frames, all on the phone:
 * brightness (is night mode needed?), movement between frames (moving subject), and about once
 * a second Google's on-device ML Kit models for what is in the picture and whether it is text.
 * Also produces the live histogram.
 */
class SceneAnalyzer : ImageAnalysis.Analyzer {
    private val _bins = MutableStateFlow(IntArray(64))
    val bins: StateFlow<IntArray> = _bins.asStateFlow()
    private val _scene = MutableStateFlow(Scene.NONE)
    val scene: StateFlow<Scene> = _scene.asStateFlow()

    @Volatile var aiEnabled = false

    /** Set while a photo is being taken so recognition never competes with the shutter. */
    @Volatile var paused = false

    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()) }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private var frame = 0
    private var prev: IntArray? = null
    private var motion = 0f
    private var dark = false
    private var lastMl = 0L
    @Volatile private var mlBusy = false
    @Volatile private var content = Scene.NONE
    private var candidate = Scene.NONE
    private var candidateCount = 0

    override fun analyze(image: ImageProxy) {
        try {
            if (frame++ % 3 != 0) return
            val plane = image.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            val w = image.width
            val h = image.height

            // Histogram and average brightness.
            val hist = IntArray(64)
            var sum = 0L
            var n = 0
            var y = 0
            while (y < h) {
                var x = 0
                val row = y * rowStride
                while (x < w) {
                    val v = buf.get(row + x * pixStride).toInt() and 0xFF
                    hist[v shr 2]++
                    sum += v
                    n++
                    x += 8
                }
                y += 8
            }
            _bins.value = hist
            if (!aiEnabled) {
                if (_scene.value != Scene.NONE) _scene.value = Scene.NONE
                return
            }
            val mean = if (n > 0) sum.toFloat() / n / 255f else 0.5f
            dark = if (dark) mean < 0.2f else mean < 0.13f

            // Movement: compare a tiny 16x12 version of this frame with the last one.
            val gw = 16
            val gh = 12
            val grid = IntArray(gw * gh)
            for (gy in 0 until gh) for (gx in 0 until gw) {
                var acc = 0
                for (k in 0 until 4) {
                    val px = ((gx + 0.25f + 0.5f * (k % 2)) * w / gw).toInt().coerceIn(0, w - 1)
                    val py = ((gy + 0.25f + 0.5f * (k / 2)) * h / gh).toInt().coerceIn(0, h - 1)
                    acc += buf.get(py * rowStride + px * pixStride).toInt() and 0xFF
                }
                grid[gy * gw + gx] = acc / 4
            }
            prev?.let { p ->
                var d = 0
                for (i in grid.indices) d += abs(grid[i] - p[i])
                val diff = d.toFloat() / grid.size / max(mean * 255f, 24f)
                motion = motion * 0.6f + diff * 0.4f
            }
            prev = grid

            // What is in the picture: about once a second, on a small copy.
            val now = System.currentTimeMillis()
            if (!mlBusy && !paused && now - lastMl > 1500) {
                lastMl = now
                val rotation = image.imageInfo.rotationDegrees
                val bmp = runCatching { image.toBitmap() }.getOrNull()
                if (bmp != null) classify(bmp, rotation)
            }

            val next = when {
                dark && motion < 0.12f -> Scene.NIGHT
                motion > 0.22f -> Scene.MOTION
                else -> content
            }
            if (next == candidate) candidateCount++ else { candidate = next; candidateCount = 1 }
            // A scene must hold for about a second before the badge changes.
            if (candidateCount >= 5 && _scene.value != next) _scene.value = next
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun classify(full: Bitmap, rotation: Int) {
        mlBusy = true
        val s = 480f / max(full.width, full.height)
        val bmp = if (s < 1f) Bitmap.createScaledBitmap(full, (full.width * s).toInt(), (full.height * s).toInt(), true) else full
        if (bmp !== full) full.recycle()
        try {
            val input = InputImage.fromBitmap(bmp, rotation)
            val labels = labeler.process(input)
            val text = recognizer.process(input)
            Tasks.whenAllComplete(labels, text).addOnCompleteListener {
                runCatching {
                    val names = if (labels.isSuccessful) labels.result.map { it.text }.toSet() else emptySet()
                    val t = if (text.isSuccessful) text.result else null
                    val chars = t?.text?.count { !it.isWhitespace() } ?: 0
                    val lines = t?.textBlocks?.sumOf { it.lines.size } ?: 0
                    content = when {
                        chars >= 60 && lines >= 3 -> Scene.TEXT
                        names.any { it in FOOD } -> Scene.FOOD
                        names.any { it in PORTRAIT } -> Scene.PORTRAIT
                        names.any { it in PET } -> Scene.PET
                        names.any { it in PLANT } -> Scene.PLANT
                        names.any { it in LANDSCAPE } -> Scene.LANDSCAPE
                        else -> Scene.NONE
                    }
                }
                bmp.recycle()
                mlBusy = false
            }
        } catch (e: Exception) {
            bmp.recycle()
            mlBusy = false
        }
    }

    fun close() {
        runCatching { labeler.close() }
        runCatching { recognizer.close() }
    }

    private companion object {
        val FOOD = setOf(
            "Food", "Cuisine", "Meal", "Dish", "Fast food", "Fruit", "Vegetable", "Baked goods", "Cake", "Bread",
            "Pizza", "Sushi", "Pie", "Cookie", "Dessert", "Hot dog", "Supper", "Lunch", "Brunch", "Cappuccino", "Coffee",
        )
        val PORTRAIT = setOf("Selfie", "Smile", "Hair", "Skin", "Beard", "Moustache", "Baby", "Bride", "Lipstick", "Eyelash", "Laugh")
        val PET = setOf("Dog", "Cat", "Pet", "Bird", "Horse")
        val PLANT = setOf("Plant", "Flower", "Petal", "Garden", "Forest", "Vegetation")
        val LANDSCAPE = setOf(
            "Sky", "Mountain", "Beach", "Sunset", "Cloud", "Sea", "Lake", "River", "Waterfall", "Desert", "Sand",
            "Field", "Prairie", "Glacier", "Canyon", "Rainbow", "Aurora", "Cliff",
        )
    }
}
