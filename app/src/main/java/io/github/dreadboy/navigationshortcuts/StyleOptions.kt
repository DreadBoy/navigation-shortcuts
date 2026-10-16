package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import io.github.dreadboy.navigationshortcuts.anycons.settings.IconShape
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness

/**
 * What the config screen offers for a widget's icon style (decisions 4.x/5.5): the 9 packs, each with the variant
 * parameters [AnyconStyle] exposes, plus icon shape and seed colour from [AnyconSettings]. Pure, unit-tested.
 */
object StyleOptions {
    data class Choice(val id: String, val label: String)
    data class Dimension(val key: String, val label: String, val choices: List<Choice>)

    const val MATERIAL = "material"
    const val STICKER = "sticker"
    const val DOTS = "dots"
    const val OUTLINE = "outline"
    const val OUTLINE_CHROMA = "outline_chroma"
    const val WATERCOLOR = "watercolor"
    const val MARBLE = "marble"
    const val BRIGHT = "bright"
    const val STELLAR = "stellar"

    val packs = listOf(
        Choice(STICKER, "Sticker"),
        Choice(MATERIAL, "Material"),
        Choice(DOTS, "Dots"),
        Choice(OUTLINE, "Outline"),
        Choice(OUTLINE_CHROMA, "Outline Chroma"),
        Choice(WATERCOLOR, "Watercolor"),
        Choice(MARBLE, "Marble"),
        Choice(BRIGHT, "Bright"),
        Choice(STELLAR, "Stellar"),
    )

    val shapes = listOf(
        Choice(IconShape.SQUIRCLE.name, "Squircle"),
        Choice(IconShape.CIRCLE.name, "Circle"),
        Choice(IconShape.ROUNDED_SQUARE.name, "Rounded square"),
    )

    /** Seed colours (ARGB) for theme-colour palettes; the first is Niagara's default `niagara_river`. */
    val seedColors = listOf(
        AnyconSettings.DEFAULT_SEED_COLOR,
        0xFFE5533D.toInt(),
        0xFFFB8C00.toInt(),
        0xFF2E9D57.toInt(),
        0xFF00897B.toInt(),
        0xFF7E57C2.toInt(),
        0xFFE91E63.toInt(),
        0xFF607D8B.toInt(),
    )

    private const val PALETTE = "palette"
    private const val SHADE = "shade"
    private const val THICKNESS = "thickness"
    private const val FILL = "fill"
    private const val TEXT = "text"
    private const val DOT_SHAPE = "dot_shape"

    private val thickness = Dimension(THICKNESS, "Line", listOf(Choice(Thickness.BOLD.name, "Bold"), Choice(Thickness.THIN.name, "Thin")))
    private val appOrTheme = Dimension(
        PALETTE,
        "Palette",
        listOf(Choice(AppOrThemePalette.THEME_COLORS.name, "Theme colours"), Choice(AppOrThemePalette.APP_COLORS.name, "App colours")),
    )

    fun dimensions(pack: String): List<Dimension> = when (pack) {
        MATERIAL -> listOf(
            Dimension(
                SHADE,
                "Shade",
                listOf(
                    Choice(AnyconStyle.Material.Shade.VIVID.name, "Vivid"),
                    Choice(AnyconStyle.Material.Shade.LIGHT.name, "Light"),
                    Choice(AnyconStyle.Material.Shade.DARK.name, "Dark"),
                ),
            ),
            thickness,
        )
        STICKER -> listOf(
            Dimension(
                PALETTE,
                "Palette",
                listOf(
                    Choice(AnyconStyle.Sticker.Palette.CORAL.name, "Coral"),
                    Choice(AnyconStyle.Sticker.Palette.OPAL.name, "Opal"),
                    Choice(AnyconStyle.Sticker.Palette.THEME_COLORS.name, "Theme colours"),
                    Choice(AnyconStyle.Sticker.Palette.APP_COLORS.name, "App colours"),
                ),
            ),
        )
        DOTS -> listOf(
            Dimension(
                DOT_SHAPE,
                "Dot",
                listOf(Choice(AnyconStyle.Dots.Shape.DOT.name, "Dot"), Choice(AnyconStyle.Dots.Shape.DIAMOND.name, "Diamond")),
            ),
            appOrTheme,
        )
        OUTLINE -> listOf(
            Dimension(
                FILL,
                "Fill",
                listOf(
                    Choice(AnyconStyle.Outline.FillType.NO_FILL.name, "No fill"),
                    Choice(AnyconStyle.Outline.FillType.FILL.name, "Fill"),
                ),
            ),
            thickness,
            Dimension(
                TEXT,
                "Text",
                listOf(
                    Choice(AnyconStyle.Outline.TextMode.LIGHT_TEXT.name, "Light (dark wallpaper)"),
                    Choice(AnyconStyle.Outline.TextMode.DARK_TEXT.name, "Dark (light wallpaper)"),
                ),
            ),
        )
        OUTLINE_CHROMA -> listOf(thickness, appOrTheme)
        WATERCOLOR, BRIGHT -> listOf(appOrTheme)
        MARBLE -> listOf(
            Dimension(
                PALETTE,
                "Palette",
                listOf(
                    Choice(AnyconStyle.Marble.Palette.THEME_COLORS.name, "Theme colours"),
                    Choice(AnyconStyle.Marble.Palette.WHITE.name, "White"),
                    Choice(AnyconStyle.Marble.Palette.BLACK.name, "Black"),
                ),
            ),
        )
        else -> emptyList()
    }

    /**
     * Whether [style] uses the widget's seed colour ("Icon colour" swatches, decisions 5b U/E8): theme-colour palettes,
     * Material, Stellar and Outline with fill (its faint tint). Fixed palettes (Coral, Opal, Marble white/black),
     * app colours and Outline without fill ignore it, so the swatches are hidden there.
     */
    fun usesSeedColor(style: AnyconStyle): Boolean = when (style) {
        is AnyconStyle.Material -> true
        is AnyconStyle.Sticker -> style.palette == AnyconStyle.Sticker.Palette.THEME_COLORS
        is AnyconStyle.Dots -> style.palette == AppOrThemePalette.THEME_COLORS
        is AnyconStyle.Outline -> style.fillType == AnyconStyle.Outline.FillType.FILL
        is AnyconStyle.OutlineChroma -> style.palette == AppOrThemePalette.THEME_COLORS
        is AnyconStyle.Watercolor -> style.palette == AppOrThemePalette.THEME_COLORS
        is AnyconStyle.Marble -> style.palette == AnyconStyle.Marble.Palette.THEME_COLORS
        is AnyconStyle.Bright -> style.palette == AppOrThemePalette.THEME_COLORS
        AnyconStyle.Stellar -> true
    }

    private val SAMPLE_GLYPHS = listOf("cat_home", "cat_office", "cat_fitness", "cat_shopping")

    /**
     * Style preview icons (decisions 5b E17): the real destinations first (up to [count]); samples only fill the rest,
     * never repeating a glyph a real destination already shows. Icon ref + seed key.
     */
    fun previewIcons(entries: List<Entry>, count: Int): List<Pair<IconRef, String>> {
        val real = entries.take(count).map { it.icon to it.id }
        val used = real.flatMap { (icon, _) -> NiagaraGlyphs.glyphsFor(icon).take(1) }.toSet()
        val samples = SAMPLE_GLYPHS.filterNot { it in used }.take(count - real.size)
        return real + samples.map { IconRef.niagara(it) to "preview-$it" }
    }

    /** Icon sizes offered in the config screen (decisions 5b U). */
    val iconSizes: List<IconSize> = IconSize.entries

    /** Packs drawn on a shape background (the shape choice only matters for these). */
    fun usesShape(pack: String): Boolean = pack in setOf(MATERIAL, MARBLE, BRIGHT, STELLAR)

    fun packOf(style: AnyconStyle): String = when (style) {
        is AnyconStyle.Material -> MATERIAL
        is AnyconStyle.Sticker -> STICKER
        is AnyconStyle.Dots -> DOTS
        is AnyconStyle.Outline -> OUTLINE
        is AnyconStyle.OutlineChroma -> OUTLINE_CHROMA
        is AnyconStyle.Watercolor -> WATERCOLOR
        is AnyconStyle.Marble -> MARBLE
        is AnyconStyle.Bright -> BRIGHT
        AnyconStyle.Stellar -> STELLAR
    }

    /** Current choice per dimension key. */
    fun selections(style: AnyconStyle): Map<String, String> = when (style) {
        is AnyconStyle.Material -> mapOf(SHADE to style.shade.name, THICKNESS to style.thickness.name)
        is AnyconStyle.Sticker -> mapOf(PALETTE to style.palette.name)
        is AnyconStyle.Dots -> mapOf(DOT_SHAPE to style.shape.name, PALETTE to style.palette.name)
        is AnyconStyle.Outline -> mapOf(FILL to style.fillType.name, THICKNESS to style.thickness.name, TEXT to style.textMode.name)
        is AnyconStyle.OutlineChroma -> mapOf(THICKNESS to style.thickness.name, PALETTE to style.palette.name)
        is AnyconStyle.Watercolor -> mapOf(PALETTE to style.palette.name)
        is AnyconStyle.Marble -> mapOf(PALETTE to style.palette.name)
        is AnyconStyle.Bright -> mapOf(PALETTE to style.palette.name)
        AnyconStyle.Stellar -> emptyMap()
    }

    /** Builds the style for [pack] from [selections]; missing or unknown choices take the pack's first choice. */
    fun build(pack: String, selections: Map<String, String>): AnyconStyle {
        val pick = { key: String ->
            val dimension = dimensions(pack).first { it.key == key }
            selections[key]?.takeIf { id -> dimension.choices.any { it.id == id } } ?: dimension.choices.first().id
        }
        return when (pack) {
            MATERIAL -> AnyconStyle.Material(AnyconStyle.Material.Shade.valueOf(pick(SHADE)), Thickness.valueOf(pick(THICKNESS)))
            STICKER -> AnyconStyle.Sticker(AnyconStyle.Sticker.Palette.valueOf(pick(PALETTE)))
            DOTS -> AnyconStyle.Dots(AnyconStyle.Dots.Shape.valueOf(pick(DOT_SHAPE)), AppOrThemePalette.valueOf(pick(PALETTE)))
            OUTLINE -> AnyconStyle.Outline(
                AnyconStyle.Outline.FillType.valueOf(pick(FILL)),
                Thickness.valueOf(pick(THICKNESS)),
                AnyconStyle.Outline.TextMode.valueOf(pick(TEXT)),
            )
            OUTLINE_CHROMA -> AnyconStyle.OutlineChroma(Thickness.valueOf(pick(THICKNESS)), AppOrThemePalette.valueOf(pick(PALETTE)))
            WATERCOLOR -> AnyconStyle.Watercolor(AppOrThemePalette.valueOf(pick(PALETTE)))
            MARBLE -> AnyconStyle.Marble(AnyconStyle.Marble.Palette.valueOf(pick(PALETTE)))
            BRIGHT -> AnyconStyle.Bright(AppOrThemePalette.valueOf(pick(PALETTE)))
            STELLAR -> AnyconStyle.Stellar
            else -> throw IllegalArgumentException("unknown pack $pack")
        }
    }

    /** Short human description, e.g. "Sticker · Coral". */
    fun describe(style: AnyconStyle): String {
        val pack = packOf(style)
        val sel = selections(style)
        val parts = dimensions(pack).map { d -> d.choices.first { it.id == sel[d.key] }.label }
        return (listOf(packs.first { it.id == pack }.label) + parts).joinToString(" · ")
    }
}
