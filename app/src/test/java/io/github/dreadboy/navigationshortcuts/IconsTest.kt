package io.github.dreadboy.navigationshortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class IconsTest {
    /** The guessed concept (built-in name); also checks the stored reference for it. */
    private fun guess(text: String): String {
        val concept = IconGuess.concept(text)
        assertNotNull("guessed concept '$concept' exists in the built-in set", BuiltInIcons.drawable(concept))
        val ref = IconGuess.guess(text)
        val glyph = NiagaraGlyphs.forConcept[concept]
        assertNotNull("every concept has a Niagara glyph (decisions 5.6): $concept", glyph)
        assertEquals(IconRef.niagara(glyph!!), ref)
        assertEquals("Niagara glyph falls back to the concept's built-in icon", concept, NiagaraGlyphs.fallbackFor(glyph))
        return concept
    }

    @Test
    fun guessesFromWordsInTheName() {
        assertEquals("gym", guess("Fitnes Center Ljubljana"))
        assertEquals("gym", guess("Gym 24"))
        assertEquals("gym", guess("Fitness First"))
        assertEquals("stadium", guess("Stožice Stadium"))
        assertEquals("stadium", guess("Stadion Ljudski vrt"))
        assertEquals("school", guess("Osnovna šola Prule"))
        assertEquals("school", guess("Gimnazija in Srednja šola"))
        assertEquals("school", guess("International School"))
        assertEquals("pool", guess("Bazen Tivoli"))
        assertEquals("pool", guess("Kopališče Kolezija"))
        assertEquals("pool", guess("Swimming pool"))
        assertEquals("shop", guess("Trgovina Mercator"))
        assertEquals("shop", guess("Hofer market"))
        assertEquals("restaurant", guess("Restavracija Strelec"))
        assertEquals("restaurant", guess("Gostilna As"))
        assertEquals("cafe", guess("Kavarna Zvezda"))
        assertEquals("cafe", guess("Cafe Central"))
        assertEquals("hospital", guess("Splošna bolnišnica Celje"))
        assertEquals("hospital", guess("University Hospital"))
        assertEquals("airport", guess("Letališče Jožeta Pučnika Ljubljana"))
        assertEquals("airport", guess("Ljubljana Airport"))
        assertEquals("parking", guess("Parking Kongresni trg"))
        assertEquals("parking", guess("Parkirišče Bežigrad"))
        assertEquals("home", guess("Home"))
        assertEquals("home", guess("Dom"))
        assertEquals("work", guess("Služba"))
        assertEquals("work", guess("Work"))
        assertEquals("mountain", guess("Koča na Golici"))
        assertEquals("mountain", guess("Mountain hut"))
    }

    @Test
    fun noFalsePositivesOnCommonNames() {
        assertEquals("pin", guess("Domžale")) // "dom" only as a whole word
        assertEquals("pin", guess("Ljubljana Castle"))
        assertEquals("pin", guess("BTC City Ljubljana"))
        assertEquals("pin", guess("Park Zvezda")) // a park is not a car park
        assertEquals("pin", guess("Gorenjska")) // "gora" only as a whole word
        assertEquals("pin", guess("46.071506,14.527154"))
        assertEquals("pin", guess(""))
    }

    @Test
    fun resolver() {
        assertEquals(R.drawable.ic_icon_gym, IconResolver.drawableRes(IconRef.builtin("gym")))
        assertEquals(R.drawable.ic_icon_cafe, IconResolver.drawableRes(IconRef.builtin("cafe")))
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef.builtin("pin")))
        // Unknown names / sources fall back to the built-in pin.
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef("own", "gym")))
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef.builtin("nope")))
        // Niagara glyphs fall back to the built-in icon of the same concept, unknown glyphs to the pin.
        assertEquals(R.drawable.ic_icon_home, IconResolver.drawableRes(IconRef.niagara("cat_home")))
        assertEquals(R.drawable.ic_icon_work, IconResolver.drawableRes(IconRef.niagara("cat_office")))
        assertEquals(R.drawable.ic_icon_cafe, IconResolver.drawableRes(IconRef.niagara("act_cup")))
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef.niagara("cat_chess_game")))
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef("niagara", "home")))
    }

    @Test
    fun guessPrefersNiagaraGlyphs() {
        assertEquals(IconRef.niagara("cat_home"), IconGuess.guess("Home"))
        assertEquals(IconRef.niagara("cat_fitness"), IconGuess.guess("Gym 24"))
        assertEquals(IconRef.niagara("act_location"), IconGuess.guess("Ljubljana Castle"))
        // Niagara has no stadium, pool, mountain or parking glyph: the nearest one, never a built-in ref.
        assertEquals(IconRef.niagara("cat_soccer_game"), IconGuess.guess("Stožice Stadium"))
        assertEquals(IconRef.niagara(NiagaraGlyphs.forConcept.getValue("pool")), IconGuess.guess("Bazen Tivoli"))
        assertEquals(IconRef.niagara(NiagaraGlyphs.forConcept.getValue("mountain")), IconGuess.guess("Koča na Golici"))
        assertEquals(IconRef.niagara("cat_cars"), IconGuess.guess("Parking Kongresni trg"))
        assertEquals(IconRef.niagara("act_location"), IconGuess.refFor(BuiltInIcons.PIN))
    }

    @Test
    fun everyBuiltInIconHasADistinctPickableNiagaraGlyph() {
        val names = BuiltInIcons.all.map { it.first }
        assertEquals(names.toSet(), NiagaraGlyphs.forConcept.keys)
        val glyphs = names.map { NiagaraGlyphs.forConcept.getValue(it) }
        assertEquals("distinct glyphs, so the reverse fallback is unambiguous", glyphs.size, glyphs.toSet().size)
        for (name in names) {
            val glyph = NiagaraGlyphs.forConcept.getValue(name)
            assertTrue("$glyph pickable", NiagaraGlyphs.isPickable(glyph))
            assertFalse("$glyph not hidden in Sticker", glyph in NiagaraGlyphs.brokenInSticker)
            // Reverse mapping (Niagara missing): the glyph falls back to the same built-in icon.
            assertEquals(name, NiagaraGlyphs.fallbackFor(glyph))
            assertEquals(BuiltInIcons.drawable(name), IconResolver.drawableRes(IconRef.niagara(glyph)))
        }
    }

    @Test
    fun builtInRefsAreDrawnAsTheirNiagaraGlyph() {
        for ((name, _) in BuiltInIcons.all) {
            assertEquals(
                listOf(NiagaraGlyphs.forConcept.getValue(name), "act_location").distinct(),
                NiagaraGlyphs.glyphsFor(IconRef.builtin(name)),
            )
            // Fallback when the glyph can't be drawn: the built-in icon itself.
            assertEquals(BuiltInIcons.drawable(name), IconResolver.drawableRes(IconRef.builtin(name)))
        }
        assertEquals(listOf("cat_soccer_game", "act_location"), NiagaraGlyphs.glyphsFor(IconRef.builtin("stadium")))
        // Unknown built-in name / unknown source: Niagara's pin.
        assertEquals(listOf("act_location"), NiagaraGlyphs.glyphsFor(IconRef.builtin("nope")))
        assertEquals(listOf("act_location"), NiagaraGlyphs.glyphsFor(IconRef("own", "gym")))
    }

    @Test
    fun niagaraRefsTryTheirGlyphThenTheConceptGlyph() {
        assertEquals(listOf("cat_home"), NiagaraGlyphs.glyphsFor(IconRef.niagara("cat_home")))
        assertEquals(listOf("act_home", "cat_home"), NiagaraGlyphs.glyphsFor(IconRef.niagara("act_home")))
        assertEquals(listOf("cat_chess_game", "act_location"), NiagaraGlyphs.glyphsFor(IconRef.niagara("cat_chess_game")))
    }

    /**
     * Every mapped glyph exists in the real Niagara 1.16.33 (decoded from the git-ignored vendor/niagara.apk by the
     * :anycons tests into anycons/build/niagara-decoded). Skipped when that isn't there (e.g. CI).
     */
    @Test
    fun mappedGlyphsExistInRealNiagara() {
        val drawables = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(3)
            .map { File(it, "anycons/build/niagara-decoded/res/drawable") }
            .firstOrNull { it.isDirectory }
        assumeTrue("decoded Niagara not found (run :anycons:testDebugUnitTest with vendor/niagara.apk)", drawables != null)
        val glyphs = NiagaraGlyphs.forConcept.values + listOf("act_home", "act_briefcase", "cat_jobs", "cat_children")
        for (glyph in glyphs) {
            assertTrue("ic_ip_$glyph exists in Niagara", File(drawables, "ic_ip_$glyph.xml").isFile)
        }
    }

    @Test
    fun pickableGlyphs() {
        assertTrue(NiagaraGlyphs.isPickable("cat_home"))
        assertTrue(NiagaraGlyphs.isPickable("act_cup"))
        assertFalse(NiagaraGlyphs.isPickable("app_com_google_android_apps_maps"))
        assertFalse(NiagaraGlyphs.isPickable("act_weather_rain_1_light"))
        assertEquals("air travel", NiagaraGlyphs.displayName("cat_air_travel"))
    }

    @Test
    fun builtInSetHasDistinctDrawables() {
        assertEquals(14, BuiltInIcons.all.size)
        assertEquals(BuiltInIcons.all.size, BuiltInIcons.all.map { it.second }.toSet().size)
    }
}
