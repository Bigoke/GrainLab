package com.bigoke.grainlab.edit

import org.json.JSONObject

enum class Group(val label: String) {
    LIGHT("Light"),
    COLOUR("Colour"),
    PRESENCE("Presence"),
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

        Adjust("texture", "Texture", Group.PRESENCE, -1f, 1f),
        Adjust("clarity", "Clarity", Group.PRESENCE, -1f, 1f),
        Adjust("dehaze", "Dehaze", Group.PRESENCE, -1f, 1f),

        Adjust("sharpen", "Sharpening", Group.DETAIL, 0f, 1f),
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
        // Free crop rectangle (0..1 of the straightened photo).
        Adjust("cropL", "Crop left", Group.HIDDEN, 0f, 1f, 0f),
        Adjust("cropT", "Crop top", Group.HIDDEN, 0f, 1f, 0f),
        Adjust("cropR", "Crop right", Group.HIDDEN, 0f, 1f, 1f),
        Adjust("cropB", "Crop bottom", Group.HIDDEN, 0f, 1f, 1f),
    )

    /** Tone curve channels: m (all), r, g, b; 5 points each at x = 0, .25, .5, .75, 1. */
    val curveChannels = listOf('m', 'r', 'g', 'b')
    fun curveKey(ch: Char, i: Int) = "curve_${ch}_$i"

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
    fun default(key: String): Float {
        byKey[key]?.let { return it.default }
        if (key.startsWith("curve_")) return (key.last() - '0') / 4f
        return 0f
    }

    val geometryKeys = setOf("straighten", "perspV", "perspH", "rotation", "flipH", "crop", "cropL", "cropT", "cropR", "cropB")
    private val lutGroups = setOf(Group.LIGHT, Group.COLOUR, Group.GRADING)
    fun affectsLut(key: String): Boolean =
        key.startsWith("hsl_") || key.startsWith("curve_") || (byKey[key]?.group in lutGroups)

    /** Effect keys that a film look sets when chosen. */
    val lookEffectKeys = listOf("grain", "grainSize", "vignette", "halation", "lightLeak", "border", "dateStamp")
}

/** A healing spot, in 0..1 of the final (cropped) photo; r is relative to the shorter side. */
data class Spot(val x: Float, val y: Float, val r: Float)

/** A brush stroke for a brush mask: x,y pairs (0..1), radius relative to the shorter side. */
data class Stroke(val points: List<Float>, val radius: Float, val erase: Boolean)

/**
 * A local adjustment. The area is a radial or linear gradient, brush strokes, or found
 * automatically (sky or subject). Coordinates are 0..1 of the final photo.
 */
data class Mask(
    val id: Long,
    val type: Int,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    val size: Float = 0.35f,
    val angle: Float = 0f,
    val feather: Float = 0.5f,
    val invert: Boolean = false,
    val strokes: List<Stroke> = emptyList(),
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
) {
    companion object {
        const val RADIAL = 0
        const val LINEAR = 1
        const val BRUSH = 2
        const val SKY = 3
        const val SUBJECT = 4
        val typeNames = listOf("Radial", "Gradient", "Brush", "Sky", "Subject")
        const val MAX = 4
    }
}

/** The complete, non-destructive edit: a film look plus every adjustment value. */
data class EditParams(
    val filmId: String? = null,
    val values: Map<String, Float> = emptyMap(),
    val spots: List<Spot> = emptyList(),
    val masks: List<Mask> = emptyList(),
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
        EditParams(values = values.filterKeys { it in Adjustments.geometryKeys }, spots = spots)

    /** The look only: no crop, rotation, healing spots or masks (used for templates and paste). */
    fun withoutGeometry(): EditParams =
        copy(values = values.filterKeys { it !in Adjustments.geometryKeys }, spots = emptyList(), masks = emptyList())

    val hasCropRect: Boolean
        get() = this["cropL"] > 0.001f || this["cropT"] > 0.001f || this["cropR"] < 0.999f || this["cropB"] < 0.999f

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
        filmId == null && spots.isEmpty() && masks.isEmpty() && values.all { (k, v) -> kotlin.math.abs(v - Adjustments.default(k)) < 1e-4f }

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
        if (spots.isNotEmpty()) put("spots", org.json.JSONArray().apply {
            spots.forEach { put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("r", it.r.toDouble())) }
        })
        if (masks.isNotEmpty()) put("masks", org.json.JSONArray().apply {
            masks.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("type", m.type)
                    put("cx", m.cx.toDouble()); put("cy", m.cy.toDouble()); put("size", m.size.toDouble())
                    put("angle", m.angle.toDouble()); put("feather", m.feather.toDouble()); put("invert", m.invert)
                    put("exposure", m.exposure.toDouble()); put("contrast", m.contrast.toDouble())
                    put("saturation", m.saturation.toDouble()); put("temperature", m.temperature.toDouble())
                    put("strokes", org.json.JSONArray().apply {
                        m.strokes.forEach { st ->
                            put(JSONObject().apply {
                                put("radius", st.radius.toDouble()); put("erase", st.erase)
                                put("p", org.json.JSONArray().apply { st.points.forEach { put(it.toDouble()) } })
                            })
                        }
                    })
                })
            }
        })
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
            val spots = ArrayList<Spot>()
            o.optJSONArray("spots")?.let { a ->
                for (i in 0 until a.length()) {
                    val s = a.getJSONObject(i)
                    spots.add(Spot(s.optDouble("x").toFloat(), s.optDouble("y").toFloat(), s.optDouble("r").toFloat()))
                }
            }
            val masks = ArrayList<Mask>()
            o.optJSONArray("masks")?.let { a ->
                for (i in 0 until a.length()) {
                    val m = a.getJSONObject(i)
                    val strokes = ArrayList<Stroke>()
                    m.optJSONArray("strokes")?.let { sa ->
                        for (j in 0 until sa.length()) {
                            val st = sa.getJSONObject(j)
                            val pa = st.optJSONArray("p")
                            val pts = if (pa == null) emptyList() else List(pa.length()) { pa.optDouble(it).toFloat() }
                            strokes.add(Stroke(pts, st.optDouble("radius", 0.05).toFloat(), st.optBoolean("erase")))
                        }
                    }
                    masks.add(
                        Mask(
                            id = m.optLong("id", i.toLong()), type = m.optInt("type"),
                            cx = m.optDouble("cx", 0.5).toFloat(), cy = m.optDouble("cy", 0.5).toFloat(),
                            size = m.optDouble("size", 0.35).toFloat(), angle = m.optDouble("angle", 0.0).toFloat(),
                            feather = m.optDouble("feather", 0.5).toFloat(), invert = m.optBoolean("invert"),
                            strokes = strokes,
                            exposure = m.optDouble("exposure", 0.0).toFloat(), contrast = m.optDouble("contrast", 0.0).toFloat(),
                            saturation = m.optDouble("saturation", 0.0).toFloat(), temperature = m.optDouble("temperature", 0.0).toFloat(),
                        ),
                    )
                }
            }
            return EditParams(film, map, spots, masks)
        }
    }
}
