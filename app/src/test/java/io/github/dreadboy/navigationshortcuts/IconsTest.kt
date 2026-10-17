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
    fun usersRealExampleIsAGym() {
        val label = "Športno društvo studenci Maribor"
        val destination = "Športno društvo studenci Maribor, Na Poljanah 20, 2000 Maribor"
        assertEquals("gym", IconGuess.concept(label, destination))
        assertEquals(IconRef.niagara("cat_fitness"), IconGuess.guess(label, destination))
        // Also from the address alone (label typed by the user).
        assertEquals("gym", IconGuess.concept("Trening", destination))
    }

    @Test
    fun slovenianWordsIgnoringCaseAndDiacritics() {
        val cases = mapOf(
            "ŠPORTNI CENTER" to "gym", "sportno drustvo" to "gym", "Šport Mladi" to "gym", "Fitnes Hiša" to "gym",
            "Telovadnica OŠ Ledina" to "gym",
            "Stadion Ljudski vrt" to "stadium", "na stadionu" to "stadium",
            "Bazen Kodeljevo" to "pool", "Kopališče Kolezija" to "pool", "Kopalisce Ilirija" to "pool",
            "Osnovna šola Prule" to "school", "Osnovna sola Prule" to "school", "Fakulteta za računalništvo" to "school",
            "Gimnazija Bežigrad" to "school", "Šolski center Celje" to "school",
            "Trgovina Jager" to "shop", "Tuš Planet" to "shop", "TUS Maribor" to "shop", "Mercator Center" to "shop",
            "Spar Vič" to "shop", "Hofer" to "shop", "Lidl Rudnik" to "shop", "Eurospar Šiška" to "shop",
            "Restavracija Strelec" to "restaurant", "Gostilna As" to "restaurant", "Picerija Foculus" to "restaurant",
            "Kavarna Zvezda" to "cafe", "Kafič pri Mostu" to "cafe",
            "Splošna bolnišnica Celje" to "hospital", "Zdravstveni dom Bežigrad" to "hospital",
            "Ambulanta Šentvid" to "hospital",
            "Letališče Jožeta Pučnika" to "airport",
            "Parkirišče Bežigrad" to "parking", "Garažna hiša Kongresni trg" to "parking",
            "Hrib Rožnik" to "mountain", "Koča na Golici" to "mountain", "Velika planina" to "mountain",
            "Planinski dom Kamniška Bistrica" to "mountain",
            "Služba" to "work", "Podjetje Novak" to "work", "Novak d.o.o." to "work", "NOVAK D. O. O." to "work",
        )
        for ((text, concept) in cases) {
            assertEquals(text, concept, IconGuess.concept(text))
            assertEquals(text, IconRef.niagara(NiagaraGlyphs.forConcept.getValue(concept)), IconGuess.guess(text))
        }
    }

    @Test
    fun labelWinsOverDestination_destinationUsedWhenLabelSaysNothing() {
        assertEquals("cafe", IconGuess.concept("Kavarna Rog", "Športni park, Ljubljana"))
        assertEquals("pool", IconGuess.concept("Kolezija", "Kopališče Kolezija, Gerbičeva 53, Ljubljana"))
        assertEquals("shop", IconGuess.concept("Mercator d.o.o.", "")) // first word decides
        assertEquals("pin", IconGuess.concept("Prijatelji", "Celovška 100, Ljubljana"))
    }

    @Test
    fun noFalsePositivesOnSlovenianStreetsAndBrands() {
        val pins = listOf(
            "Šolska ulica 5, Ljubljana", "Stadionska ulica 10, Ljubljana", "Letališka cesta 16, Ljubljana",
            "Bazenska pot 3", "Kopališka ulica 1", "Planinska cesta 2", "Bolniška ulica 4",
            "Sportina Ljubljana", "Sparkasse", "Spartak", "Tušmobil", "Solkan", "Domžale", "Gorenjska",
            "Podgorje", "Hribarjeva ulica", "Kočevje", "Park Zvezda", "Marketing agencija",
            "Mercatorjeva 1", "Parkirna ura", "Dobrova 12, 1356 Dobrova", "Celjska cesta 5, 3220 Štore", "Solo bar",
        )
        for (text in pins) assertEquals(text, "pin", IconGuess.concept(text, text))
    }

    @Test
    fun domIsHomeOnlyAsTheWholeText() {
        for (text in listOf("Dom", "DOMA", "domov", "Home", " dom ")) assertEquals(text, "home", IconGuess.concept(text))
        for (text in listOf(
            "Cankarjev dom", "Kulturni dom Franca Bernika", "Gasilski dom Vič", "Narodni dom Maribor",
            "Študentski dom Rožna dolina", "Dom starejših občanov Bežigrad", "Triglavski dom na Kredarici",
            "Home Center Ljubljana",
        )) {
            assertEquals(text, "pin", IconGuess.concept(text, text))
        }
        assertEquals("mountain", IconGuess.concept("Planinski dom Kamniška Bistrica"))
        assertEquals("hospital", IconGuess.concept("Zdravstveni dom Bežigrad"))
        // Not home from the address either.
        assertEquals("pin", IconGuess.concept("Koncert", "Cankarjev dom, Prešernova cesta 10, Ljubljana"))
    }

    @Test
    fun streetNamesDoNotTriggerKeywords() {
        val pins = listOf(
            "Gimnazijska cesta 3, Trbovlje", "Športna ulica 22, Ljubljana", "Gostilniška ulica 1",
            "Bolnišnična ulica 5", "Fakultetna ulica 4", "Trgovinska ulica 7", "Pot na Goro 9, Ljubljana",
            "Gimnazijska 12", "Fakultetna 1", "Bolnišnična 3", "Gostilniška 2", "Trgovinska 8",
            "Športni park 4", "Stadionska c. 2", "Kopališki trg 1", "Bazenska pot 3", "Cesta na Hrib 5",
            "Hribarjeva ul. 3", "Letališka cesta 16", "Šolska ulica 5",
        )
        for (text in pins) assertEquals(text, "pin", IconGuess.concept(text, text))
        // The place itself still matches, also with a street in the same address.
        assertEquals("gym", IconGuess.concept("Športno društvo studenci Maribor", "Na Poljanah 20, 2000 Maribor"))
        assertEquals("school", IconGuess.concept("Gimnazija Bežigrad", "Peričeva ulica 4, Ljubljana"))
        assertEquals("school", IconGuess.concept("Fakulteta za šport", "Gortanova ulica 22"))
        assertEquals("hospital", IconGuess.concept("x", "Splošna bolnišnica, Ljubljanska ulica 5, Maribor"))
        assertEquals("mountain", IconGuess.concept("Na goro"))
    }

    @Test
    fun noFalsePositivesOnCommonNames() {
        assertEquals("pin", guess("Domžale")) // "dom" only as a whole word
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
        assertEquals(IconRef.niagara("cat_tower_defense_game"), IconGuess.guess("Ljubljana Castle"))
        assertEquals(IconRef.niagara("act_location"), IconGuess.guess("Prijatelji"))
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

    /** Decisions 5b: hospital guesses the stethoscope; entries stored with the old lotus keep it and its fallback. */
    @Test
    fun hospitalIsTheStethoscope_oldLotusEntriesStillRender() {
        assertEquals(IconRef.niagara("cat_doctor_game"), IconGuess.guess("Splošna bolnišnica Celje"))
        assertEquals(listOf("cat_health", "cat_doctor_game"), NiagaraGlyphs.glyphsFor(IconRef.niagara("cat_health")))
        assertEquals("hospital", NiagaraGlyphs.fallbackFor("cat_health"))
        assertEquals(BuiltInIcons.drawable("hospital"), IconResolver.drawableRes(IconRef.niagara("cat_health")))
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
        // App glyphs (5b.4): their own glyph, then Niagara's pin; the Material pin when Niagara is missing.
        assertEquals(
            listOf("app_com_spotify_music", "act_location"),
            NiagaraGlyphs.glyphsFor(IconRef.niagara("app_com_spotify_music")),
        )
        assertEquals(R.drawable.ic_place_pin, IconResolver.drawableRes(IconRef.niagara("app_com_spotify_music")))
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
        assertTrue(NiagaraGlyphs.isPickable("app_com_google_android_apps_maps"))
        assertTrue(NiagaraGlyphs.isApp("app_com_spotify_music"))
        assertFalse(NiagaraGlyphs.isApp("cat_home"))
        assertFalse(NiagaraGlyphs.isPickable("act_weather_rain_1_light"))
        assertFalse(NiagaraGlyphs.isPickable("ic_launcher"))
    }

    @Test
    fun builtInSetHasDistinctDrawables() {
        assertEquals(15, BuiltInIcons.all.size)
        assertEquals(BuiltInIcons.all.size, BuiltInIcons.all.map { it.second }.toSet().size)
    }

    /** E22: accessibility labels and picker labels are readable names, never raw glyph names. */
    @Test
    fun readableNames() {
        val cases = mapOf(
            "cat_air_travel" to "Air travel", "cat_home" to "Home", "act_location" to "Location",
            "cat_soccer_game" to "Soccer", "cat_chess_game" to "Chess", "act_anycons_koi" to "Koi",
            "act_cup" to "Coffee", "act_jellyfish" to "Swimming", "cat_tower_defense_game" to "Castle",
            "act_ic_fluent_gift" to "Gift", "act_pause_circled_48" to "Pause", "cat_game" to "Game",
            "cat_vpn" to "VPN", "app_com_spotify_music" to "Spotify", "app_com_slack" to "Slack",
            "app_com_google_android_youtube" to "YouTube", "app_org_mozilla_firefox" to "Firefox",
            "app_com_zhiliaoapp_musically" to "TikTok",
        )
        for ((glyph, name) in cases) {
            assertEquals(glyph, name, NiagaraGlyphs.displayName(glyph))
            assertEquals(glyph, name, IconNames.readable(IconRef.niagara(glyph)))
        }
        assertEquals("Location", IconNames.readable(IconRef.builtin("pin")))
        assertEquals("Stadium", IconNames.readable(IconRef.builtin("stadium")))
        // A glyph added by a Niagara update: a readable guess, never the raw name.
        assertEquals("Example", NiagaraGlyphs.displayName("app_com_example_notes"))
        assertEquals("Newthing", NiagaraGlyphs.displayName("cat_newthing"))
        for (glyph in cases.keys) assertFalse(glyph, '_' in NiagaraGlyphs.displayName(glyph))
    }

    /** E12: more keywords, with the false-positive rules of decisions 5.8. */
    @Test
    fun moreKeywords() {
        val cases = mapOf(
            "Doma" to "home", "Moj dom" to "home", "My home" to "home", "Home sweet home" to "home",
            "Sports complex Tivoli" to "gym", "Športno društvo Partizan" to "gym", "Športni kompleks Ljudski vrt" to "gym",
            "Shopping center Citypark" to "shop", "Nakupovalni center Supernova" to "shop", "BTC City Ljubljana" to "shop",
            "Mall of Split" to "shop", "Qlandia Kranj" to "shop",
            "Ljubljanski grad" to "castle", "Ljubljana Castle" to "castle", "Grad Kodeljevo" to "castle",
            "Blejski grad" to "castle", "Predjamski grad" to "castle", "Dvorec Zemono" to "castle",
            "Na gradu" to "castle", "Schloss Schönbrunn" to "castle",
        )
        for ((text, concept) in cases) assertEquals(text, concept, IconGuess.concept(text, text))
        assertEquals(IconRef.niagara("cat_tower_defense_game"), IconGuess.guess("Ljubljanski grad"))
    }

    /** A keyword glued to digits is the keyword ("GYM24" in Maps, Dunajska c. 154), without new false positives. */
    @Test
    fun keywordGluedToDigits() {
        assertEquals("gym", IconGuess.concept("GYM24", "GYM24, Dunajska cesta 154, 1000 Ljubljana"))
        assertEquals("gym", IconGuess.concept("x", "GYM24, Dunajska cesta 154, 1000 Ljubljana"))
        for (text in listOf("Fitnes24", "Gym 24", "fitness365", "SPORT2000")) assertEquals(text, "gym", IconGuess.concept(text))
        assertEquals("shop", IconGuess.concept("Spar2"))
        // Digits before letters, digits inside words, and the street/village rules are unchanged.
        val pins = listOf(
            "24gym", "A1 Slovenija", "Domžale2", "Gradec12", "Sparkasse24", "Grad12 ulica", "Stadionska 2a",
            "Šolska ulica 5", "Planina 5, 6232 Planina",
        )
        for (text in pins) assertEquals(text, "pin", IconGuess.concept("Prijatelji", text))
    }

    @Test
    fun castleFalsePositives() {
        val pins = listOf(
            "Slovenj Gradec", "Gradec", "Gradišče nad Pijavo Gorico", "Gradnja d.d. Ljubljana", "Gradbeni center",
            "Grajska cesta 3", "Pot na grad 5", "Grajski trg 1", "Hamburg", "Gradiška", "Gradnikova ulica 7",
            "Centralna postaja", "Home Center Ljubljana", "Cankarjev dom",
        )
        for (text in pins) assertEquals(text, "pin", IconGuess.concept(text, text))
        // A village of that name in the address (keyword after a postcode or before a house number) is no castle.
        assertEquals("pin", IconGuess.concept("Kmetija Novak", "Grad 12, 9264 Grad"))
        assertEquals("pin", IconGuess.concept("Hiša", "Planina 5, 6232 Planina"))
        // Maps' label for a shared town carries the postcode: a village name, also in the label (V2 on the AVD).
        assertEquals("pin", IconGuess.concept("9264 Grad", "9264 Grad, Slovenia"))
        assertEquals("pin", IconGuess.concept("6232 Planina", "6232 Planina, Slovenia"))
        assertEquals("pin", IconGuess.concept("2000 Maribor", "2000 Maribor, Slovenia"))
        // Labels still match before a number ("Gym 24"), addresses still match a place name.
        assertEquals("gym", IconGuess.concept("Gym 24"))
        assertEquals("castle", IconGuess.concept("Izlet", "Ljubljanski grad, Grajska planota 1, 1000 Ljubljana"))
    }
}
