package io.github.dreadboy.navigationshortcuts.anycons.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Background/clip shape of shape-based packs (Material, Bright, Marble, Stellar). Paths live in a 100×100 box. */
@Serializable
enum class IconShape(val pathData100: String) {
    @SerialName("circle")
    CIRCLE("M50 0A50 50,0,1,1,50 100A50 50,0,1,1,50 0"),

    @SerialName("squircle")
    SQUIRCLE("M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z"),

    @SerialName("rounded_square")
    ROUNDED_SQUARE(
        "M50,0L80,0 A20,20,0,0 1 100,20 L100,80 A20,20,0,0 1 80,100 L20,100 A20,20,0,0 1 0,80 L 0,20 A20,20,0,0 1 20,0z",
    ),
}

/** Palette choice shared by packs that offer only "app colours" vs "theme colours". */
@Serializable
enum class AppOrThemePalette {
    /** Per-destination colour (falls back to #109dff, Dots: #757575); there is no app behind a destination. */
    @SerialName("app_colors")
    APP_COLORS,

    @SerialName("theme_colors")
    THEME_COLORS,
}

@Serializable
enum class Thickness {
    @SerialName("thin")
    THIN,

    @SerialName("thick")
    BOLD,
}

/** One Anycons pack plus its variant parameters. Serial names follow Niagara's `~nld.<style>.<variant>` words. */
@Serializable
sealed interface AnyconStyle {
    /** Whether Niagara's launcher drop shadow is applied by default for this pack/variant. */
    val defaultLauncherShadow: Boolean

    /** Whether the pack draws the glyph at all (Dots does not). */
    val usesGlyph: Boolean get() = true

    @Serializable
    @SerialName("material")
    data class Material(
        val shade: Shade = Shade.VIVID,
        val thickness: Thickness = Thickness.BOLD,
    ) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true

        @Serializable
        enum class Shade {
            @SerialName("pastel")
            LIGHT,

            @SerialName("vibrant")
            VIVID,

            @SerialName("dark")
            DARK,
        }
    }

    @Serializable
    @SerialName("sticker")
    data class Sticker(val palette: Palette = Palette.THEME_COLORS) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true

        @Serializable
        enum class Palette {
            @SerialName("app_colors")
            APP_COLORS,

            @SerialName("coral")
            CORAL,

            @SerialName("opal")
            OPAL,

            @SerialName("theme_colors")
            THEME_COLORS,
        }
    }

    @Serializable
    @SerialName("dots")
    data class Dots(
        val shape: Shape = Shape.DOT,
        val palette: AppOrThemePalette = AppOrThemePalette.THEME_COLORS,
    ) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true
        override val usesGlyph: Boolean get() = false

        @Serializable
        enum class Shape {
            @SerialName("dot")
            DOT,

            @SerialName("diamond")
            DIAMOND,
        }
    }

    @Serializable
    @SerialName("outline")
    data class Outline(
        val fillType: FillType = FillType.NO_FILL,
        val thickness: Thickness = Thickness.BOLD,
        val textMode: TextMode = TextMode.LIGHT_TEXT,
    ) : AnyconStyle {
        /** Niagara only shadows Outline with light text (dark wallpaper). */
        override val defaultLauncherShadow: Boolean get() = textMode == TextMode.LIGHT_TEXT

        @Serializable
        enum class FillType {
            @SerialName("nofill")
            NO_FILL,

            @SerialName("fill")
            FILL,
        }

        /** Wallpaper text colour mode: light text over a dark wallpaper, or dark text over a light one. */
        @Serializable
        enum class TextMode {
            @SerialName("light")
            LIGHT_TEXT,

            @SerialName("dark")
            DARK_TEXT,
        }
    }

    @Serializable
    @SerialName("outline_chroma")
    data class OutlineChroma(
        val thickness: Thickness = Thickness.BOLD,
        val palette: AppOrThemePalette = AppOrThemePalette.THEME_COLORS,
    ) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true
    }

    @Serializable
    @SerialName("watercolor")
    data class Watercolor(val palette: AppOrThemePalette = AppOrThemePalette.THEME_COLORS) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = false
    }

    @Serializable
    @SerialName("marble")
    data class Marble(val palette: Palette = Palette.THEME_COLORS) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true

        @Serializable
        enum class Palette {
            @SerialName("white")
            WHITE,

            @SerialName("black")
            BLACK,

            @SerialName("theme_colors")
            THEME_COLORS,
        }
    }

    @Serializable
    @SerialName("bright")
    data class Bright(val palette: AppOrThemePalette = AppOrThemePalette.THEME_COLORS) : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true
    }

    @Serializable
    @SerialName("stellar")
    data object Stellar : AnyconStyle {
        override val defaultLauncherShadow: Boolean get() = true
    }
}

/**
 * Per-widget icon settings. [launcherShadow] `null` means "the pack's default" ([AnyconStyle.defaultLauncherShadow]).
 */
@Serializable
data class AnyconSettings(
    val style: AnyconStyle = AnyconStyle.Material(),
    val shape: IconShape = IconShape.SQUIRCLE,
    val seedColor: Int = DEFAULT_SEED_COLOR,
    val launcherShadow: Boolean? = null,
) {
    val effectiveLauncherShadow: Boolean get() = launcherShadow ?: style.defaultLauncherShadow

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /** Niagara's `niagara_river`. */
        const val DEFAULT_SEED_COLOR: Int = 0xFF109DFF.toInt()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(text: String): AnyconSettings = json.decodeFromString(serializer(), text)
    }
}
