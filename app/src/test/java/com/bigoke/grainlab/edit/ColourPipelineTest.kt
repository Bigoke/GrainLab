package com.bigoke.grainlab.edit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Guards the colour pipeline so changes never silently break a look. */
class ColourPipelineTest {
    private val n = LutBuilder.N

    private fun at(lut: FloatArray, r: Int, g: Int, b: Int, k: Int) = lut[((b * n + g) * n + r) * 3 + k]

    @Test
    fun identityLutLeavesColoursUnchanged() {
        val lut = LutBuilder.build(EditParams())
        for (b in 0 until n step 4) for (g in 0 until n step 4) for (r in 0 until n step 4) {
            val expected = floatArrayOf(r / (n - 1f), g / (n - 1f), b / (n - 1f))
            for (k in 0..2) assertEquals("($r,$g,$b)[$k]", expected[k], at(lut, r, g, b, k), 0.004f)
        }
    }

    @Test
    fun everyFilmLookStaysInRange() {
        for (look in FilmLooks.all) {
            val lut = LutBuilder.build(EditParams().withFilm(look))
            for (v in lut) assertTrue("${look.id} produced $v", !v.isNaN() && v in 0f..1f)
        }
    }

    @Test
    fun filmCurvesNeverReverse() {
        for (look in FilmLooks.all) for (curve in listOf(look.r, look.g, look.b)) {
            var last = -1f
            for (i in 0..100) {
                val v = curve.eval(i / 100f)
                assertTrue("${look.id} curve dips at ${i / 100f}", v >= last - 1e-4f)
                last = v
            }
        }
    }

    @Test
    fun filmIdsAreUnique() {
        val ids = FilmLooks.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun exposureBrightensMidGrey() {
        val mid = n / 2
        val base = at(LutBuilder.build(EditParams()), mid, mid, mid, 1)
        val brighter = at(LutBuilder.build(EditParams().with("exposure", 1f)), mid, mid, mid, 1)
        val darker = at(LutBuilder.build(EditParams().with("exposure", -1f)), mid, mid, mid, 1)
        assertTrue(brighter > base + 0.1f)
        assertTrue(darker < base - 0.1f)
    }

    @Test
    fun brightenedHighlightsRollOffInsteadOfClipping() {
        val lut = LutBuilder.build(EditParams().with("exposure", 1.5f))
        // Bright greys must stay distinct (no flat clipped plateau) and keep their order.
        var last = -1f
        for (i in n / 2 until n) {
            val v = at(lut, i, i, i, 1)
            assertTrue("grey $i not above previous ($v <= $last)", v > last + 0.0005f)
            last = v
        }
        assertTrue(last <= 1f)
    }

    @Test
    fun brandFreeNamesCoverEveryBrandedLook() {
        val branded = setOf(FilmLooks.KODAK, FilmLooks.FUJI, FilmLooks.AGFA, FilmLooks.ILFORD, FilmLooks.POLAROID, FilmLooks.HB)
        for (look in FilmLooks.all.filter { it.pack in branded }) {
            assertTrue("no brand-free name for ${look.id}", FilmLooks.safeName(look.id) != null)
        }
    }

    @Test
    fun blackAndWhiteLooksAreNeutral() {
        for (look in FilmLooks.all.filter { it.bw != null && abs(it.shadowTint.sum()) + abs(it.highlightTint.sum()) == 0f }) {
            val lut = LutBuilder.build(EditParams().withFilm(look))
            val r = at(lut, 30, 10, 5, 0); val g = at(lut, 30, 10, 5, 1); val b = at(lut, 30, 10, 5, 2)
            assertEquals(look.id, r, g, 0.01f)
            assertEquals(look.id, g, b, 0.01f)
        }
    }

    @Test
    fun paramsSurviveJsonRoundTrip() {
        val p = EditParams(filmId = "portrait400").with("exposure", 0.4f).with("hsl_3_s", -0.2f)
        val back = EditParams.fromJson(JSONObject(p.toJson().toString()))
        assertEquals(p.filmId, back.filmId)
        assertEquals(0.4f, back["exposure"], 1e-5f)
        assertEquals(-0.2f, back["hsl_3_s"], 1e-5f)
    }

    @Test
    fun zeroStrengthRemovesTheLook() {
        val p = EditParams().withFilm(FilmLooks.byId("toylens")).with("contrast", 0.5f).scaled(0f)
        assertEquals(0f, p["filmStrength"], 1e-5f)
        assertEquals(0f, p["contrast"], 1e-5f)
        assertEquals(0f, p["vignette"], 1e-5f)
    }
}
