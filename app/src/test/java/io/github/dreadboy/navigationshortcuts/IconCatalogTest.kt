package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.IconCatalog.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class IconCatalogTest {
    /** A small Niagara-like glyph list in enumeration (alphabetical) order. */
    private val glyphs = listOf(
        "act_add", "act_calendar", "act_cup", "act_home", "act_jellyfish", "act_location", "act_pause_circled_48",
        "act_weather_snow", "app_com_slack", "app_com_spotify_music", "app_org_mozilla_firefox", "cat_air_travel",
        "cat_calendar", "cat_cars", "cat_chess_game", "cat_fitness", "cat_food", "cat_home", "cat_office",
        "cat_shopping", "cat_soccer_game", "cat_tower_defense_game",
    )

    private fun names(entries: List<IconCatalog.Entry>) = entries.map { it.ref.name }

    @Test
    fun placesFirstThenMoreThenApps() {
        val entries = IconCatalog.niagara(glyphs)
        val sections = entries.map { it.section }
        assertEquals("sections in order", sections.sortedBy { it.ordinal }, sections)
        assertEquals(
            listOf(
                "cat_home", "cat_office", "cat_fitness", "cat_shopping", "cat_food", "act_cup", "cat_air_travel",
                "cat_cars", "act_location", "cat_tower_defense_game", "cat_soccer_game", "act_jellyfish",
            ),
            names(entries.filter { it.section == Section.PLACES }),
        )
        // Other icons by label, apps by app name.
        assertEquals(
            listOf("act_add", "cat_calendar", "cat_chess_game", "act_pause_circled_48"),
            names(entries.filter { it.section == Section.MORE }),
        )
        assertEquals(listOf("Firefox", "Slack", "Spotify"), entries.filter { it.section == Section.APPS }.map { it.label })
        assertTrue(entries.all { it.ref.source == IconRef.SOURCE_NIAGARA })
    }

    @Test
    fun noDuplicatesNoWeather() {
        val entries = IconCatalog.niagara(glyphs)
        val names = names(entries)
        assertEquals(names.size, names.toSet().size)
        assertFalse("act_home duplicates cat_home", "act_home" in names)
        assertFalse("act_calendar duplicates cat_calendar", "act_calendar" in names)
        assertFalse("act_weather_snow" in names)
        assertEquals(1, entries.count { it.label == "Home" })
        // Without the category glyph, the action glyph is offered.
        assertTrue("act_home" in names(IconCatalog.niagara(listOf("act_home"))))
        // Style-specific hidden glyphs.
        assertFalse("act_pause_circled_48" in names(IconCatalog.niagara(glyphs, NiagaraGlyphs.brokenInSticker)))
    }

    private fun search(query: String) = names(IconCatalog.filter(IconCatalog.niagara(glyphs), query))

    @Test
    fun synonymsFindTheGlyph() {
        val cases = mapOf(
            "gym" to "cat_fitness", "fitness" to "cat_fitness", "swim" to "act_jellyfish", "pool" to "act_jellyfish",
            "bazen" to "act_jellyfish", "shop" to "cat_shopping", "store" to "cat_shopping", "mall" to "cat_shopping",
            "trgovina" to "cat_shopping", "shopping center" to "cat_shopping", "eat" to "cat_food",
            "restaurant" to "cat_food", "gostilna" to "cat_food", "coffee" to "act_cup", "cafe" to "act_cup",
            "kavarna" to "act_cup", "plane" to "cat_air_travel", "airport" to "cat_air_travel",
            "letališče" to "cat_air_travel", "car" to "cat_cars", "parking" to "cat_cars", "stadium" to "cat_soccer_game",
            "work" to "cat_office", "služba" to "cat_office", "doma" to "cat_home", "castle" to "cat_tower_defense_game",
            "grad" to "cat_tower_defense_game", "pin" to "act_location", "air travel" to "cat_air_travel",
            "AIR" to "cat_air_travel", "spotify" to "app_com_spotify_music", "mozilla" to "app_org_mozilla_firefox",
            "firefox" to "app_org_mozilla_firefox", "chess" to "cat_chess_game",
        )
        for ((query, glyph) in cases) assertTrue("'$query' finds $glyph: ${search(query)}", glyph in search(query))
    }

    @Test
    fun searchMatchesWordStartsOnly() {
        assertFalse("'ar' is inside 'cars' and 'travel': ${search("ar")}", "cat_cars" in search("ar"))
        assertTrue(search("qqqzzz").isEmpty())
        assertEquals(IconCatalog.niagara(glyphs), IconCatalog.filter(IconCatalog.niagara(glyphs), "  "))
        // Results keep the section order: places before apps.
        assertEquals(listOf("cat_home"), search("home"))
    }

    @Test
    fun currentIconOnTop() {
        val all = IconCatalog.niagara(glyphs)
        val withCurrent = IconCatalog.withCurrent(all, IconRef.niagara("cat_chess_game"))
        assertEquals(IconRef.niagara("cat_chess_game"), withCurrent.first().ref)
        assertEquals(Section.CURRENT, withCurrent.first().section)
        assertEquals("Chess", withCurrent.first().label)
        assertEquals(all, withCurrent.drop(1))
        assertEquals(all, IconCatalog.withCurrent(all, null))
        // A current glyph that isn't offered (e.g. a hidden duplicate) is still shown on top.
        assertEquals("act_home", IconCatalog.withCurrent(all, IconRef.niagara("act_home")).first().ref.name)
        // Search hides the current-icon section.
        assertTrue(IconCatalog.filter(withCurrent, "chess").none { it.section == Section.CURRENT })
    }

    @Test
    fun builtInSetWhenNiagaraIsMissing() {
        val entries = IconCatalog.builtin()
        assertEquals(BuiltInIcons.all.map { it.first }, names(entries))
        assertTrue(entries.all { it.ref.source == IconRef.SOURCE_BUILTIN })
        assertEquals("Location", entries.first().label)
        assertFalse(entries.any { it.label.startsWith("Built-in") })
        val pool = IconCatalog.filter(entries, "swim")
        assertEquals(listOf("pool"), names(pool))
        assertEquals(listOf("castle"), names(IconCatalog.filter(entries, "grad")))
    }

    @Test
    fun everyPlaceGlyphIsPickableAndUnique() {
        val places = IconCatalog.places.map { it.first }
        assertEquals(places.size, places.toSet().size)
        for (glyph in places) assertTrue(glyph, NiagaraGlyphs.isPickable(glyph) && !NiagaraGlyphs.isApp(glyph))
        // Every built-in concept's glyph is a place.
        for (glyph in NiagaraGlyphs.forConcept.values) assertTrue(glyph, glyph in places)
    }

    /**
     * Against the real Niagara 1.16.33 (decoded from the git-ignored vendor/niagara.apk by the :anycons tests):
     * every place glyph exists, every app glyph has a curated name, and the picker has ~880 distinct entries.
     */
    @Test
    fun realNiagaraGlyphs() {
        val drawables = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(3)
            .map { File(it, "anycons/build/niagara-decoded/res/drawable") }
            .firstOrNull { it.isDirectory }
        assumeTrue("decoded Niagara not found (run :anycons:testDebugUnitTest with vendor/niagara.apk)", drawables != null)
        val all = drawables!!.list()!!.filter { it.startsWith("ic_ip_") && it.endsWith(".xml") }
            .map { it.removePrefix("ic_ip_").removeSuffix(".xml") }.sorted()
        for ((glyph, _) in IconCatalog.places) assertTrue("ic_ip_$glyph exists", glyph in all)
        val apps = all.filter(NiagaraGlyphs::isApp)
        assertEquals(549, apps.size)
        for (app in apps) assertTrue("curated name for $app", AppGlyphNames.isListed(app))
        val labels = apps.map(NiagaraGlyphs::displayName)
        assertEquals("app names are distinct: ${labels.groupBy { it }.filter { it.value.size > 1 }.keys}", labels.size, labels.toSet().size)
        val entries = IconCatalog.niagara(all)
        assertEquals(entries.size, entries.map { it.ref }.toSet().size)
        assertTrue("about 880 entries: ${entries.size}", entries.size in 850..900)
        assertEquals(apps.size, entries.count { it.section == Section.APPS })
        println("picker entries: ${entries.size} (places ${entries.count { it.section == Section.PLACES }}, " +
            "more ${entries.count { it.section == Section.MORE }}, apps ${apps.size})")
    }
}
