package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleOptionsTest {
    @Test
    fun everyChoiceRoundTrips() {
        var combos = 0
        for (pack in StyleOptions.packs.map { it.id }) {
            val dims = StyleOptions.dimensions(pack)
            // All combinations of this pack's dimensions.
            var selections = listOf(emptyMap<String, String>())
            for (d in dims) selections = selections.flatMap { s -> d.choices.map { s + (d.key to it.id) } }
            for (s in selections) {
                val style = StyleOptions.build(pack, s)
                assertEquals(pack, StyleOptions.packOf(style))
                assertEquals(s, StyleOptions.selections(style))
                assertTrue(StyleOptions.describe(style).isNotEmpty())
                combos++
            }
        }
        // Material 3×2, Sticker 4, Dots 2×2, Outline 2×2×2, Chroma 2×2, Watercolor 2, Marble 3, Bright 2, Stellar 1.
        assertEquals(6 + 4 + 4 + 8 + 4 + 2 + 3 + 2 + 1, combos)
    }

    @Test
    fun packsInNiagarasOrder_andPlainOutlineWording() {
        assertEquals(
            listOf("Material", "Sticker", "Dots", "Outline", "Watercolor", "Marble", "Bright", "Stellar", "Outline Chroma"),
            StyleOptions.packs.map { it.label },
        )
        val outline = StyleOptions.dimensions(StyleOptions.OUTLINE).map { d -> d.label to d.choices.map { it.label } }
        assertEquals(
            listOf(
                "Fill" to listOf("No fill", "Fill"),
                "Line width" to listOf("Bold", "Thin"),
                "Line colour" to listOf("Light (for dark wallpapers)", "Dark (for light wallpapers)"),
            ),
            outline,
        )
    }

    @Test
    fun missingChoicesTakeThePacksFirstChoice() {
        assertEquals(AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.CORAL), StyleOptions.build(StyleOptions.STICKER, emptyMap()))
        assertEquals(
            AnyconStyle.OutlineChroma(Thickness.BOLD, AppOrThemePalette.THEME_COLORS),
            StyleOptions.build(StyleOptions.OUTLINE_CHROMA, mapOf("palette" to "nonsense")),
        )
        assertEquals("Sticker · Coral", StyleOptions.describe(WidgetConfig.DEFAULT_STYLE.style))
    }

    @Test
    fun cellBitmapsStayWithinTheBudget() {
        assertEquals(IconRendering.MAX_CELL_PX, IconRendering.cellSizePx(1))
        assertEquals(IconRendering.MAX_CELL_PX, IconRendering.cellSizePx(6))
        for (cells in 1..36) {
            val px = IconRendering.cellSizePx(cells)
            assertTrue("$cells cells @ $px px", cells * px * px * 4 <= IconRendering.BITMAP_BUDGET_BYTES)
        }
        assertEquals(53, IconRendering.cellSizePx(36))
        // Well under half of the ~1 MB binder transaction buffer.
        assertTrue(IconRendering.BITMAP_BUDGET_BYTES < 512 * 1024)
    }
}
