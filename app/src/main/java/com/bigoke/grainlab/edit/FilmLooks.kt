package com.bigoke.grainlab.edit

/** Monotone tone curve through 5 points at x = 0, 0.25, 0.5, 0.75, 1. */
class Curve(private val ys: FloatArray) {
    private val table: FloatArray by lazy { buildTable() }

    fun eval(x: Float): Float {
        val t = x.coerceIn(0f, 1f) * SIZE
        val i = t.toInt().coerceAtMost(SIZE - 1)
        val f = t - i
        return table[i] + (table[i + 1] - table[i]) * f
    }

    private fun buildTable(): FloatArray {
        val n = ys.size
        val xs = FloatArray(n) { it / (n - 1f) }
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        // Fritsch-Carlson limiter keeps the curve monotone
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val tau = 3f / kotlin.math.sqrt(s)
                m[i] = tau * a * d[i]; m[i + 1] = tau * b * d[i]
            }
        }
        val out = FloatArray(SIZE + 1)
        for (j in 0..SIZE) {
            val x = j / SIZE.toFloat()
            var k = (x * (n - 1)).toInt().coerceAtMost(n - 2)
            if (k < 0) k = 0
            val h = xs[k + 1] - xs[k]
            val t = (x - xs[k]) / h
            val t2 = t * t; val t3 = t2 * t
            out[j] = (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] +
                (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
        }
        return out
    }

    companion object {
        private const val SIZE = 1024
        val IDENTITY = Curve(floatArrayOf(0f, .25f, .5f, .75f, 1f))
    }
}

private fun cv(a: Float, b: Float, c: Float, d: Float, e: Float) = Curve(floatArrayOf(a, b, c, d, e))
private fun rgb(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

/**
 * A film look: colour response (baked into the lookup table) plus the film's grain,
 * vignette, halation, border and date-stamp defaults.
 */
class FilmLook(
    val id: String,
    val name: String,
    val pack: String,
    val note: String,
    val r: Curve = Curve.IDENTITY,
    val g: Curve = Curve.IDENTITY,
    val b: Curve = Curve.IDENTITY,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val fade: Float = 0f,
    val warmth: Float = 0f,
    val shadowTint: FloatArray = rgb(0f, 0f, 0f),
    val highlightTint: FloatArray = rgb(0f, 0f, 0f),
    val bw: FloatArray? = null,
    val effects: Map<String, Float> = emptyMap(),
)

object FilmLooks {
    const val NEG = "Colour Negative"
    const val SLIDE = "Slide"
    const val BW = "Black & White"
    const val INSTANT = "Instant"
    const val VINTAGE = "Vintage Cameras"
    val packs = listOf(NEG, SLIDE, BW, INSTANT, VINTAGE)

    val all: List<FilmLook> = listOf(
        // ---- Colour negative ----
        FilmLook("golden200", "Golden 200", NEG, "Warm consumer 35mm, golden highlights",
            r = cv(.03f, .29f, .56f, .80f, .98f), g = cv(.02f, .26f, .52f, .77f, .96f), b = cv(.04f, .22f, .44f, .68f, .88f),
            saturation = 1.08f, fade = .04f, warmth = .3f, highlightTint = rgb(.04f, .02f, -.03f),
            effects = mapOf("grain" to .25f, "vignette" to .12f)),
        FilmLook("portrait400", "Portrait 400", NEG, "Soft professional portrait negative",
            r = cv(.04f, .28f, .53f, .77f, .97f), g = cv(.04f, .27f, .52f, .76f, .96f), b = cv(.05f, .27f, .50f, .73f, .93f),
            contrast = -.12f, saturation = .9f, fade = .05f, warmth = .08f,
            effects = mapOf("grain" to .18f)),
        FilmLook("portrait160", "Portrait 160 Cool", NEG, "Clean, cool portrait stock",
            r = cv(.03f, .26f, .50f, .75f, .96f), g = cv(.03f, .26f, .51f, .76f, .97f), b = cv(.05f, .28f, .52f, .76f, .97f),
            contrast = -.1f, saturation = .88f, fade = .03f,
            effects = mapOf("grain" to .12f)),
        FilmLook("street800", "Street 800", NEG, "Cinema tungsten film, red glow on lights",
            r = cv(.02f, .23f, .48f, .75f, .97f), g = cv(.03f, .26f, .51f, .76f, .97f), b = cv(.08f, .30f, .54f, .77f, .96f),
            contrast = .05f, shadowTint = rgb(-.02f, .01f, .05f),
            effects = mapOf("grain" to .35f, "halation" to .55f, "vignette" to .15f)),
        FilmLook("tokyogreen", "Tokyo Green", NEG, "Green-cyan shadows, punchy greens",
            r = cv(.03f, .25f, .50f, .76f, .97f), g = cv(.05f, .29f, .54f, .78f, .98f), b = cv(.06f, .27f, .50f, .74f, .95f),
            saturation = 1.05f, shadowTint = rgb(-.03f, .03f, .02f),
            effects = mapOf("grain" to .25f)),
        FilmLook("everyday400", "Everyday 400", NEG, "Classic drugstore film",
            r = cv(.02f, .27f, .53f, .78f, .97f), g = cv(.02f, .26f, .52f, .77f, .97f), b = cv(.03f, .24f, .48f, .73f, .94f),
            saturation = 1.05f, warmth = .15f,
            effects = mapOf("grain" to .3f, "vignette" to .1f)),
        FilmLook("ultra100", "Ultra 100", NEG, "Saturated, crisp, fine grain",
            r = cv(.01f, .25f, .52f, .78f, .99f), g = cv(.01f, .24f, .51f, .77f, .99f), b = cv(.02f, .23f, .49f, .75f, .97f),
            contrast = .15f, saturation = 1.25f,
            effects = mapOf("grain" to .12f)),
        FilmLook("faded90s", "Faded '90s", NEG, "Old prints from a shoebox",
            r = cv(.06f, .30f, .55f, .78f, .95f), g = cv(.05f, .28f, .52f, .75f, .93f), b = cv(.08f, .28f, .50f, .72f, .90f),
            saturation = .85f, fade = .12f, warmth = .2f, shadowTint = rgb(.02f, 0f, .04f),
            effects = mapOf("grain" to .3f, "vignette" to .2f)),

        // ---- Slide ----
        FilmLook("velvet50", "Velvet 50", SLIDE, "Vivid slide, deep blacks",
            r = cv(0f, .22f, .50f, .79f, 1f), g = cv(0f, .23f, .51f, .79f, 1f), b = cv(.01f, .24f, .50f, .77f, .99f),
            contrast = .25f, saturation = 1.35f,
            effects = mapOf("vignette" to .15f, "grain" to .08f)),
        FilmLook("chrome64", "Chrome 64", SLIDE, "Rich reds, warm magazine look",
            r = cv(.01f, .25f, .54f, .80f, .99f), g = cv(0f, .22f, .49f, .76f, .97f), b = cv(0f, .19f, .44f, .71f, .94f),
            contrast = .2f, saturation = 1.15f, warmth = .25f,
            effects = mapOf("grain" to .15f, "vignette" to .1f)),
        FilmLook("astia", "Astia Soft", SLIDE, "Gentle contrast, true skin",
            r = cv(.01f, .26f, .52f, .77f, .98f), g = cv(.01f, .25f, .51f, .76f, .98f), b = cv(.02f, .25f, .50f, .75f, .97f),
            contrast = .05f, saturation = 1.05f,
            effects = mapOf("grain" to .08f)),
        FilmLook("vivid100", "Vivid 100", SLIDE, "Bold colour, cool blue skies",
            r = cv(0f, .23f, .50f, .78f, 1f), g = cv(0f, .24f, .51f, .78f, 1f), b = cv(.02f, .27f, .54f, .80f, 1f),
            contrast = .2f, saturation = 1.3f,
            effects = mapOf("grain" to .1f)),
        FilmLook("clear100", "Clear 100", SLIDE, "Clean, neutral-cool slide",
            r = cv(0f, .24f, .50f, .76f, .99f), g = cv(0f, .25f, .51f, .77f, 1f), b = cv(.02f, .27f, .53f, .79f, 1f),
            contrast = .12f, saturation = 1.1f,
            effects = mapOf("grain" to .08f)),
        FilmLook("crossproc", "Cross Process", SLIDE, "Slide film in negative chemistry",
            r = cv(0f, .20f, .52f, .82f, 1f), g = cv(.02f, .26f, .56f, .84f, 1f), b = cv(.15f, .30f, .46f, .62f, .80f),
            contrast = .15f, saturation = 1.2f,
            effects = mapOf("grain" to .2f, "vignette" to .25f)),

        // ---- Black & white ----
        FilmLook("tri400", "Tri 400", BW, "Gritty press black & white",
            contrast = .25f, bw = rgb(.30f, .59f, .11f),
            effects = mapOf("grain" to .45f, "vignette" to .15f)),
        FilmLook("fine100", "Fine 100", BW, "Smooth, fine-grain silver",
            contrast = .05f, fade = .02f, bw = rgb(.25f, .65f, .10f),
            effects = mapOf("grain" to .1f)),
        FilmLook("delta3200", "Delta 3200", BW, "Heavy grain, moody low light",
            contrast = .3f, bw = rgb(.30f, .60f, .10f),
            effects = mapOf("grain" to .8f, "grainSize" to 2.2f, "vignette" to .25f)),
        FilmLook("pan50", "Pan 50", BW, "Hard contrast, dark skies",
            contrast = .4f, bw = rgb(.45f, .5f, .05f),
            effects = mapOf("grain" to .15f)),
        FilmLook("sepia", "Sepia Print", BW, "Toned darkroom print",
            contrast = .1f, fade = .05f, bw = rgb(.30f, .59f, .11f),
            shadowTint = rgb(.04f, .01f, -.03f), highlightTint = rgb(.08f, .03f, -.06f),
            effects = mapOf("grain" to .2f, "vignette" to .3f)),
        FilmLook("infra", "Infrared Mono", BW, "Glowing foliage, dark sky",
            contrast = .3f, bw = rgb(.2f, 1f, -.2f),
            effects = mapOf("grain" to .3f, "halation" to .4f)),

        // ---- Instant ----
        FilmLook("squareinstant", "Square Instant", INSTANT, "Creamy instant square, cyan shadows",
            r = cv(.06f, .29f, .53f, .76f, .95f), g = cv(.07f, .30f, .54f, .77f, .95f), b = cv(.10f, .31f, .53f, .74f, .92f),
            contrast = -.05f, saturation = .85f, fade = .1f,
            shadowTint = rgb(-.02f, .02f, .05f), highlightTint = rgb(.03f, .03f, 0f),
            effects = mapOf("border" to 2f, "grain" to .15f, "vignette" to .15f)),
        FilmLook("miniinstant", "Mini Instant", INSTANT, "Bright, soft credit-card instant",
            r = cv(.05f, .31f, .57f, .80f, .97f), g = cv(.05f, .30f, .56f, .79f, .97f), b = cv(.06f, .29f, .54f, .77f, .95f),
            contrast = -.1f, saturation = .95f, fade = .06f, warmth = .1f,
            effects = mapOf("border" to 2f, "grain" to .12f)),
        FilmLook("fadedinstant", "Faded Instant", INSTANT, "Sun-faded instant print",
            r = cv(.10f, .33f, .56f, .77f, .93f), g = cv(.09f, .31f, .53f, .74f, .91f), b = cv(.10f, .30f, .50f, .70f, .87f),
            saturation = .7f, fade = .2f, warmth = .15f,
            effects = mapOf("border" to 2f, "vignette" to .25f, "grain" to .2f)),
        FilmLook("expired", "Expired Instant", INSTANT, "Out-of-date chemistry, magenta shifts",
            r = cv(.06f, .32f, .57f, .80f, .96f), g = cv(.03f, .25f, .48f, .70f, .88f), b = cv(.08f, .30f, .52f, .74f, .92f),
            saturation = .8f, fade = .12f,
            effects = mapOf("border" to 2f, "lightLeak" to .3f, "grain" to .3f, "vignette" to .2f)),

        // ---- Vintage cameras ----
        FilmLook("toylens", "Toy Lens", VINTAGE, "Plastic camera: vignette, leaks, saturation",
            r = cv(0f, .22f, .52f, .80f, 1f), g = cv(0f, .22f, .50f, .78f, 1f), b = cv(.03f, .24f, .48f, .74f, .96f),
            contrast = .3f, saturation = 1.3f,
            effects = mapOf("vignette" to .7f, "lightLeak" to .45f, "grain" to .3f)),
        FilmLook("halfframe", "Half Frame", VINTAGE, "Warm half-frame compact",
            r = cv(.03f, .28f, .55f, .79f, .97f), g = cv(.02f, .26f, .52f, .76f, .95f), b = cv(.03f, .23f, .46f, .70f, .90f),
            saturation = 1.05f, fade = .05f, warmth = .25f,
            effects = mapOf("grain" to .4f, "vignette" to .2f)),
        FilmLook("disposable", "Disposable", VINTAGE, "Single-use flash camera with date stamp",
            r = cv(.02f, .25f, .51f, .77f, .97f), g = cv(.03f, .27f, .53f, .78f, .98f), b = cv(.03f, .25f, .49f, .74f, .95f),
            contrast = .15f, saturation = 1.1f, shadowTint = rgb(-.01f, .02f, 0f),
            effects = mapOf("dateStamp" to 1f, "vignette" to .3f, "grain" to .35f)),
        FilmLook("super8", "Super 8", VINTAGE, "Home-movie warmth and frame",
            r = cv(.05f, .30f, .56f, .80f, .97f), g = cv(.04f, .27f, .52f, .76f, .94f), b = cv(.04f, .23f, .45f, .68f, .88f),
            contrast = .1f, saturation = 1.1f, fade = .08f, warmth = .35f,
            effects = mapOf("grain" to .5f, "grainSize" to 2f, "vignette" to .4f, "border" to 3f, "halation" to .2f)),
        FilmLook("print70s", "1970s Print", VINTAGE, "Yellowed family-album print",
            r = cv(.06f, .30f, .56f, .79f, .95f), g = cv(.05f, .28f, .52f, .75f, .92f), b = cv(.04f, .22f, .44f, .66f, .86f),
            saturation = .9f, fade = .1f, warmth = .3f,
            effects = mapOf("grain" to .3f, "vignette" to .2f)),
        FilmLook("bleach", "Bleach Bypass", VINTAGE, "Silver-retained cinema look",
            contrast = .35f, saturation = .45f,
            effects = mapOf("grain" to .2f, "vignette" to .2f)),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String?): FilmLook? = if (id == null) null else byId[id]
}
