package io.github.dreadboy.navigationshortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IconsTest {
    private fun guess(text: String): String {
        val ref = IconGuess.guess(text)
        assertEquals("guesses are always built-in icons", IconRef.SOURCE_BUILTIN, ref.source)
        assertNotNull("guessed name '${ref.name}' exists in the built-in set", BuiltInIcons.drawable(ref.name))
        return ref.name
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
        // Unknown names / sources (e.g. Phase 5 "niagara" before it exists) fall back to the built-in pin.
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef("own", "gym")))
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef.builtin("nope")))
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef("niagara", "home")))
    }

    @Test
    fun builtInSetHasDistinctDrawables() {
        assertEquals(14, BuiltInIcons.all.size)
        assertEquals(BuiltInIcons.all.size, BuiltInIcons.all.map { it.second }.toSet().size)
    }
}
