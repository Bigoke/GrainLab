package com.bigoke.grainlab.edit

import org.json.JSONObject

enum class Group(val label: String) {
    LIGHT("Light"),
    COLOUR("Colour"),
    GRADING("Grading"),
    DETAIL("Detail"),
    EFFECTS("Effects"),
    GEOMETRY("Crop"),
    HIDDEN("")
}

/** One adjustable value. [step] > 0 means it is a discrete choice, not a smooth slider. */
data class Adjust(
    val key: String,
    val label: String,
    val group: Group,
    val min: Float,
    val max: Float,
    val default: Float = 0f,
    val step: Float = 0f,
)

object Adjustments {
    val all: List<Adjust> = listOf(
        Adjust("exposure", "Exposure", Group.LIGHT, -2f, 2f),
        Adjust("contrast", "Contrast", Group.LIGHT, -1f, 1f),
        Adjust("highlights", "Highlights", Group.LIGHT, -1f, 1f),
        Adjust("shadows", "Shadows", Group.LIGHT, -1f, 1f),
        Adjust("whites", "Whites", Group.LIGHT, -1f, 1f),
        Adjust("blacks", "Blacks", Group.LIGHT, -1f, 1f),
        Adjust("fade", "Fade", Group.LIGHT, 0f, 1f),

        Adjust("temperature", "Temperature", Group.COLOUR, -1f, 1f),
        Adjust("tint", "Tint", Group.COLOUR, -1f, 1f),
        Adjust("vibrance", "Vibrance", Group.COLOUR, -1f, 1f),
        Adjust("saturation", "Saturation", Group.COLOUR, -1f, 1f),
        Adjust("filmStrength", "Film strength", Group.COLOUR, 0f, 1f, 1f),

        Adjust("shadowHue", "Shadow hue", Group.GRADING, 0f, 360f, 210f),
        Adjust("shadowSat", "Shadow tint", Group.GRADING, 0f, 1f),
        Adjust("highlightHue", "Highlight hue", Group.GRADING, 0f, 360f, 40f),
        Adjust("highlightSat", "Highlight tint", Group.GRADING, 0f, 1f),
        Adjust("gradeBalance", "Balance", Group.GRADING, -1f, 1f),

        Adjust("sharpen", "Sharpening", Group.DETAIL, 0f, 1f),
        Adjust("clarity", "Clarity", Group.DETAIL, -1f, 1f),
        Adjust("noiseReduction", "Noise reduction", Group.DETAIL, 0f, 1f),

        Adjust("grain", "Grain", Group.EFFECTS, 0f, 1f),
        Adjust("grainSize", "Grain size", Group.EFFECTS, 1f, 4f, 1.5f),
        Adjust("vignette", "Vignette", Group.EFFECTS, -1f, 1f),
        Adjust("halation", "Halation", Group.EFFECTS, 0f, 1f),
        Adjust("lightLeak", "Light leak", Group.EFFECTS, 0f, 1f),
        Adjust("border", "Border", Group.EFFECTS, 0f, 3f, 0f, 1f),
        Adjust("dateStamp", "Date stamp", Group.EFFECTS, 0f, 1f, 0f, 1f),

        Adjust("straighten", "Straighten", Group.GEOMETRY, -15f, 15f),
        Adjust("perspV", "Vertical perspective", Group.GEOMETRY, -1f, 1f),
        Adjust("perspH", "Horizontal perspective", Group.GEOMETRY, -1f, 1f),
        Adjust("rotation", "Rotation", Group.HIDDEN, 0f, 3f, 0f, 1f),
        Adjust("flipH", "Flip", Group.HIDDEN, 0f, 1f, 0f, 1f),
        Adjust("crop", "Crop", Group.HIDDEN, 0f, 5f, 0f, 1f),
    )

    val borderNames = listOf("None", "Thin white", "Instant", "Black frame")
    val cropNames = listOf("Original", "1:1", "4:5", "3:2", "16:9", "2:3")
    val cropRatios = listOf(0f, 1f, 0.8f, 1.5f, 16f / 9f, 2f / 3f)

    /** HSL bands: name, centre hue in degrees. */
    val bands = listOf(
        "Red" to 0f, "Orange" to 30f, "Yellow" to 60f, "Green" to 120f,
        "Aqua" to 180f, "Blue" to 225f, "Purple" to 275f, "Magenta" to 320f,
    )

    fun hslKey(band: Int, part: Char) = "hsl_${band}_$part"

    private val byKey = all.associateBy { it.key }
    fun spec(key: String): Adjust? = byKey[key]
    fun default(key: String): Float = byKey[key]?.default ?: 0f

    val geometryKeys = setOf("straighten", "perspV", "perspH", "rotation", "flipH", "crop")
    private val lutGroups = setOf(Group.LIGHT, Group.COLOUR, Group.GRADING)
    fun affectsLut(key: String): Boolean =
        key.startsWith("hsl_") || (byKey[key]?.group in lutGroups)

    /** Effect keys that a film look sets when chosen. */
    val lookEffectKeys = listOf("grain", "grainSize", "vignette", "halation", "lightLeak", "border", "dateStamp")
}

/** The complete, non-destructive edit: a film look plus every adjustment value. */
data class EditParams(
    val filmId: String? = null,
    val values: Map<String, Float> = emptyMap(),
) {
    operator fun get(key: String): Float = values[key] ?: Adjustments.default(key)

    fun with(key: String, v: Float): EditParams = copy(values = values + (key to v))
    fun withAll(m: Map<String, Float>): EditParams = copy(values = values + m)

    fun withFilm(look: FilmLook?): EditParams {
        val effects = Adjustments.lookEffectKeys.associateWith { k ->
            look?.effects?.get(k) ?: Adjustments.default(k)
        }
        return copy(filmId = look?.id).withAll(effects)
    }

    fun geometryOnly(): EditParams =
        EditParams(values = values.filterKeys { it in Adjustments.geometryKeys })

    fun withoutGeometry(): EditParams =
        copy(values = values.filterKeys { it !in Adjustments.geometryKeys })

    /** Blend every adjustment toward its default; used for the look strength slider. */
    fun scaled(strength: Float): EditParams {
        if (strength >= 0.999f) return this
        val skip = Adjustments.geometryKeys + setOf("border", "dateStamp", "grainSize", "shadowHue", "highlightHue")
        val m = values.mapValues { (k, v) ->
            if (k in skip) v else {
                val d = Adjustments.default(k)
                d + (v - d) * strength
            }
        }
        val fs = this["filmStrength"] * strength
        return copy(values = m + ("filmStrength" to fs))
    }

    fun isIdentity(): Boolean =
        filmId == null && values.all { (k, v) -> kotlin.math.abs(v - Adjustments.default(k)) < 1e-4f }

    /** Key that changes only when the colour lookup table needs rebuilding. */
    fun lutSignature(): String = buildString {
        append(filmId ?: "-")
        values.entries.filter { Adjustments.affectsLut(it.key) }.sortedBy { it.key }.forEach {
            append('|').append(it.key).append('=').append(it.value)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("filmId", filmId ?: JSONObject.NULL)
        val v = JSONObject()
        values.forEach { (k, x) -> v.put(k, x.toDouble()) }
        put("values", v)
    }

    companion object {
        fun fromJson(o: JSONObject): EditParams {
            val film = if (o.isNull("filmId")) null else o.optString("filmId", "").ifEmpty { null }
            val v = o.optJSONObject("values")
            val map = HashMap<String, Float>()
            if (v != null) {
                val it = v.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    map[k] = v.optDouble(k, 0.0).toFloat()
                }
            }
            return EditParams(film, map)
        }
    }
}
