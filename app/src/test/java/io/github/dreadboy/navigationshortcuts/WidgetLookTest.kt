package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Widget look (decisions 5b): app colours per destination, swatch visibility, icon size, preview icons. */
class WidgetLookTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun categoriesGetTheirOwnColours() {
        val home = DestinationColors.colorFor("cat_home", "a")
        val work = DestinationColors.colorFor("cat_office", "a")
        val gym = DestinationColors.colorFor("cat_fitness", "a")
        val food = DestinationColors.colorFor("cat_food", "a")
        val shop = DestinationColors.colorFor("cat_shopping", "a")
        val health = DestinationColors.colorFor("cat_health", "a")
        val travel = DestinationColors.colorFor("cat_air_travel", "a")
        assertEquals(7, setOf(home, work, gym, food, shop, health, travel).size)
        // Same category = same colour, whatever the entry; synonyms share it.
        assertEquals(home, DestinationColors.colorFor("cat_home", "other-id"))
        assertEquals(home, DestinationColors.colorFor("act_home", "x"))
        assertEquals(gym, DestinationColors.colorFor("cat_soccer_game", "x"))
        assertEquals(work, DestinationColors.colorFor("act_briefcase", "x"))
        // The castle/landmark glyph has a category colour, not a pin's fallback (round 3).
        assertEquals(travel, DestinationColors.colorFor("cat_tower_defense_game", "x"))
    }

    @Test
    fun otherGlyphsGetAStableColourPerEntry() {
        val ids = (1..40).map { "00000000-0000-0000-0000-%012d".format(it) }
        val colours = ids.map { DestinationColors.colorFor("act_location", it) }
        // Stable: same id → same colour, every time (String.hashCode is specified).
        assertEquals(colours, ids.map { DestinationColors.colorFor("act_location", it) })
        // Spread: pins at different places don't all look alike.
        assertTrue(colours.toSet().size >= 5)
        // Never a category's colour (a pin must not look like "work").
        assertTrue(DestinationColors.fallback.none { it in DestinationColors.byGlyph.values })
        assertTrue(colours.all { it in DestinationColors.fallback })
        // Pinned values, so a refactor can't silently recolour the user's widgets.
        assertEquals(DestinationColors.fallback[Math.floorMod("abc".hashCode(), DestinationColors.fallback.size)],
            DestinationColors.colorFor("act_location", "abc"))
        assertEquals(DestinationColors.Family.PERSIMMON, DestinationColors.colorFor("cat_home", "abc"))
    }

    @Test
    fun allColoursAreOpaqueAndDistinctInTheFallbackList() {
        assertEquals(DestinationColors.fallback.size, DestinationColors.fallback.toSet().size)
        (DestinationColors.fallback + DestinationColors.byGlyph.values).forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test
    fun appColourOnlyForAppColourPalettes() {
        fun s(style: AnyconStyle) = AnyconSettings(style = style)
        assertTrue(DestinationColors.usesAppColor(s(AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.APP_COLORS))))
        assertTrue(DestinationColors.usesAppColor(s(AnyconStyle.Watercolor(AppOrThemePalette.APP_COLORS))))
        assertTrue(DestinationColors.usesAppColor(s(AnyconStyle.Bright(AppOrThemePalette.APP_COLORS))))
        assertTrue(DestinationColors.usesAppColor(s(AnyconStyle.Dots(palette = AppOrThemePalette.APP_COLORS))))
        assertTrue(DestinationColors.usesAppColor(s(AnyconStyle.OutlineChroma(palette = AppOrThemePalette.APP_COLORS))))
        assertFalse(DestinationColors.usesAppColor(WidgetConfig.DEFAULT_STYLE))
        assertFalse(DestinationColors.usesAppColor(s(AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.THEME_COLORS))))
        assertFalse(DestinationColors.usesAppColor(s(AnyconStyle.Material())))
    }

    @Test
    fun swatchesOnlyWhereTheSeedColourIsUsed() {
        val uses = mutableListOf<String>()
        val ignores = mutableListOf<String>()
        for (pack in StyleOptions.packs.map { it.id }) {
            var selections = listOf(emptyMap<String, String>())
            for (d in StyleOptions.dimensions(pack)) selections = selections.flatMap { s -> d.choices.map { s + (d.key to it.id) } }
            for (sel in selections) {
                val style = StyleOptions.build(pack, sel)
                (if (StyleOptions.usesSeedColor(style)) uses else ignores) += StyleOptions.describe(style)
            }
        }
        assertTrue(uses.toString(), "Sticker · Coral" in ignores && "Sticker · Opal" in ignores && "Sticker · App colours" in ignores)
        assertTrue("Sticker · Theme colours" in uses)
        assertTrue("Marble · White" in ignores && "Marble · Black" in ignores && "Marble · Theme colours" in uses)
        assertTrue("Watercolor · App colours" in ignores && "Watercolor · Theme colours" in uses)
        assertTrue(uses.any { it.startsWith("Material") } && "Stellar" in uses)
        // Outline's fill tint is too faint for the swatches to show (round 2): hidden for every Outline variant.
        assertTrue(ignores.count { it.startsWith("Outline ·") } == 8 && uses.none { it.startsWith("Outline ·") })
        // Every app-colour palette ignores the seed (the swatches would do nothing there).
        assertTrue(ignores.containsAll(uses.filter { "App colours" in it }) && uses.none { "App colours" in it })
    }

    @Test
    fun iconSizeDefaultsAndOldConfigsParse() {
        // A 0.3.x config (no iconSize) loads with Niagara's size, entries and style unchanged.
        val v031 = json.decodeFromString(
            WidgetConfig.serializer(),
            """{"rows":1,"cols":4,"entries":[{"label":"Doma","destination":"46.05,14.5","icon":{"source":"niagara","name":"cat_home"},"id":"d"}],""" +
                """"style":{"style":{"type":"watercolor","palette":"app_colors"},"shape":"circle","seedColor":-15688193,"launcherShadow":null}}""",
        )
        assertEquals(IconSize.DEFAULT, v031.iconSize)
        assertEquals(AnyconStyle.Watercolor(AppOrThemePalette.APP_COLORS), v031.style.style)
        assertEquals("Doma", v031.entries.single().label)
        // Round trip with each size; the stored name is stable.
        for (size in IconSize.entries) {
            val c = v031.copy(iconSize = size)
            assertEquals(c, json.decodeFromString(WidgetConfig.serializer(), json.encodeToString(WidgetConfig.serializer(), c)))
        }
        assertTrue(json.encodeToString(WidgetConfig.serializer(), v031.copy(iconSize = IconSize.LARGE)).endsWith(""""iconSize":"large"}"""))
        assertEquals(44, IconSize.DEFAULT.dp)
        // Large falls back to Default when a column is too narrow for it (315 dp slot); never larger than chosen.
        assertEquals(IconSize.LARGE, IconSize.LARGE.fitting(4))
        assertEquals(IconSize.LARGE, IconSize.LARGE.fitting(5))
        assertEquals(IconSize.DEFAULT, IconSize.LARGE.fitting(6))
        assertEquals(IconSize.DEFAULT, IconSize.DEFAULT.fitting(6))
        assertEquals(IconSize.SMALL, IconSize.SMALL.fitting(1))
        for (cols in 1..6) for (size in IconSize.entries) assertTrue(size.fitting(cols).dp <= size.dp)
        assertTrue(IconSize.SMALL.dp < IconSize.DEFAULT.dp && IconSize.DEFAULT.dp < IconSize.LARGE.dp)
    }

    @Test
    fun bitmapsAreNoLargerThanShownAndKeepTheBudget() {
        // 44 dp at 420 dpi = 116 px: 1–7 icons at 116 px; the 400 KiB budget still rules beyond.
        assertEquals(116, IconRendering.cellSizePx(1, 116))
        assertEquals(116, IconRendering.cellSizePx(7, 116))
        assertEquals(92, IconRendering.cellSizePx(12, 116))
        assertEquals(53, IconRendering.cellSizePx(36, 116))
        // Large (147 px) is capped at 128.
        assertEquals(IconRendering.MAX_CELL_PX, IconRendering.cellSizePx(1, 147))
        for (n in 1..36) for (target in listOf(95, 116, 147)) {
            val px = IconRendering.cellSizePx(n, target)
            assertTrue(px <= target && 4L * px * px * n <= IconRendering.BITMAP_BUDGET_BYTES)
        }
    }

    @Test
    fun previewUsesRealDestinationsFirst() {
        fun e(id: String, glyph: String) = Entry(id, id, IconRef.niagara(glyph), id = id)
        assertEquals(
            listOf("cat_home", "cat_office", "cat_fitness", "cat_shopping"),
            StyleOptions.previewIcons(emptyList(), 4).map { it.first.name },
        )
        // A real gym: it comes first, and the gym sample isn't repeated.
        val one = StyleOptions.previewIcons(listOf(e("g", "cat_fitness")), 4)
        assertEquals(listOf("cat_fitness", "cat_home", "cat_office", "cat_shopping"), one.map { it.first.name })
        assertEquals("g", one.first().second)
        // Four or more real destinations: no samples at all.
        val real = listOf(e("a", "act_location"), e("b", "act_location"), e("c", "cat_food"), e("d", "act_cup"), e("e", "cat_home"))
        assertEquals(listOf("a", "b", "c", "d"), StyleOptions.previewIcons(real, 4).map { it.second })
        // A built-in ref counts as its mapped glyph.
        val builtin = StyleOptions.previewIcons(listOf(Entry("H", "x", IconRef.builtin("home"), id = "h")), 4)
        assertNotEquals("cat_home", builtin[1].first.name)
        assertEquals(4, builtin.size)
    }

    /** Every glyph with a category colour exists in the real Niagara 1.16.33 (skipped without the decoded APK). */
    @Test
    fun colouredGlyphsExistInRealNiagara() {
        val drawables = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .take(3)
            .map { File(it, "anycons/build/niagara-decoded/res/drawable") }
            .firstOrNull { it.isDirectory }
        assumeTrue("decoded Niagara not found", drawables != null)
        for (glyph in DestinationColors.byGlyph.keys) {
            assertTrue("ic_ip_$glyph exists in Niagara", File(drawables, "ic_ip_$glyph.xml").isFile)
        }
    }

    @Test
    fun shapePacksGetNiagarasAdaptiveIconSize() {
        fun s(style: AnyconStyle) = AnyconSettings(style = style)
        // 116 px view -> 98 px visible shape, 9 px each side (Niagara's Maps icon: view at 121, shape at 130).
        assertEquals(9, IconLayout.paddingPx(s(AnyconStyle.Material()), 116))
        assertEquals(9, IconLayout.paddingPx(s(AnyconStyle.Bright()), 116))
        assertEquals(9, IconLayout.paddingPx(s(AnyconStyle.Marble()), 116))
        assertEquals(9, IconLayout.paddingPx(s(AnyconStyle.Stellar), 116))
        assertEquals(98, 116 - 2 * IconLayout.paddingPx(s(AnyconStyle.Material()), 116))
        // Glyph packs fill the view, as in Niagara (their composition has its own margin).
        assertEquals(0, IconLayout.paddingPx(WidgetConfig.DEFAULT_STYLE, 116))
        assertEquals(0, IconLayout.paddingPx(s(AnyconStyle.Outline()), 116))
        assertEquals(0, IconLayout.paddingPx(s(AnyconStyle.Watercolor()), 116))
        assertEquals(0, IconLayout.paddingPx(s(AnyconStyle.Dots()), 116))
    }

    @Test
    fun labelStartsAtTheIconsVisibleEdge() {
        // 4x3 bitmap with the first opaque column at x=2 (a half-transparent shadow at x=1 doesn't count).
        val w = 4
        val px = IntArray(w * 3)
        px[1 * w + 1] = 0x40000000
        px[2 * w + 2] = 0xFF000000.toInt()
        assertEquals(2, IconLayout.leftEdge(px, w, 3))
        assertEquals(null, IconLayout.leftEdge(IntArray(9), 3, 3))
        // Median edge, scaled from the bitmap to the drawn size, plus the view padding.
        assertEquals(9 + 10, IconLayout.labelStartPx(9, listOf(10, 12, 8), 98, 98))
        assertEquals(20, IconLayout.labelStartPx(0, listOf(19, 20, 18, 30), 116, 116))
        assertEquals(20, IconLayout.labelStartPx(0, listOf(10), 58, 116))
        assertEquals(9, IconLayout.labelStartPx(9, emptyList(), 0, 98))
        // Capped (round 3): a Dots dot at 0.375 of the view must not push the label far right.
        assertEquals(21, IconLayout.labelStartPx(0, listOf(43), 116, 116, 21))
        assertEquals(9, IconLayout.labelStartPx(9, listOf(43), 98, 98, 5))
        assertTrue(IconLayout.alignsLabel(WidgetConfig.DEFAULT_STYLE))
        assertFalse(IconLayout.alignsLabel(AnyconSettings(style = AnyconStyle.Dots())))
        // Never makes a label ellipsize that fits without the inset: 6 columns, 138 px cell, 21 px reserved,
        // "Doma" 117 px wide -> no room for an inset; a short label keeps it; one 10 px short gets 10 px.
        assertEquals(0, IconLayout.fitLabelStartPx(21, 0, 138, 21, 117))
        assertEquals(21, IconLayout.fitLabelStartPx(21, 0, 138, 21, 60))
        assertEquals(10, IconLayout.fitLabelStartPx(21, 0, 138, 21, 107))
        // Too long for the cell anyway: keeps the inset (ellipsizes either way); the padding is never undercut.
        assertEquals(21, IconLayout.fitLabelStartPx(21, 0, 138, 21, 200))
        assertEquals(9, IconLayout.fitLabelStartPx(19, 9, 138, 21, 108))
        assertEquals(12, IconLayout.fitLabelStartPx(19, 9, 138, 21, 105))
    }
}
