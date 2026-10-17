package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette

/**
 * "App colours" for destinations (decisions 5b, U/E9). Niagara's APP_COLORS palettes colour each icon by its app's
 * brand colour; a destination has no app, so it gets a colour by category (home, work, sport, food, …) from its
 * glyph, and every other glyph a stable colour from its entry id. All colours are the key tones (`accent1_0400_*`)
 * of Niagara's own theme palette families, so they sit well next to Niagara's icons. Pure, unit-tested.
 */
object DestinationColors {
    /** Niagara 1.16.33 `accent1_0400_<family>`. */
    object Family {
        const val AMBER = 0xFFC88200.toInt()
        const val AZURE = 0xFF4394EA.toInt()
        const val CERULEAN = 0xFF009ADA.toInt()
        const val FLAMINGO = 0xFFF55299.toInt()
        const val GREEN = 0xFF48A357.toInt()
        const val JADE = 0xFF25A575.toInt()
        const val LAVENDER = 0xFFA677F7.toInt()
        const val LIME = 0xFF699F2E.toInt()
        const val MAGENTA = 0xFFDB5DDB.toInt()
        const val MOSS = 0xFF8A990E.toInt()
        const val NIAGARA_BLUE = 0xFF6B8CF0.toInt()
        const val ORANGE = 0xFFDC7606.toInt()
        const val PEACOCK = 0xFF00A0B0.toInt()
        const val PERIWINKLE = 0xFF8983F5.toInt()
        const val PEAR = 0xFFA29200.toInt()
        const val PERSIMMON = 0xFFEE672A.toInt()
        const val PURPLE = 0xFFC36AF0.toInt()
        const val RASPBERRY = 0xFFFA5275.toInt()
        const val RED = 0xFFFA584F.toInt()
        const val SHOCKING_PINK = 0xFFEB56BB.toInt()
        const val SKY = 0xFF009EC3.toInt()
        const val TEAL = 0xFF00A29F.toInt()
        const val TURQUOISE = 0xFF01A48C.toInt()
        const val YELLOW = 0xFFB58B00.toInt()
    }

    /**
     * Fallback colours for glyphs without a category, picked by the entry id's hash: the 7 families no category uses,
     * so a pin never takes a category's exact colour (round 2).
     */
    val fallback = listOf(
        Family.NIAGARA_BLUE, Family.ORANGE, Family.PEACOCK, Family.FLAMINGO, Family.MOSS, Family.CERULEAN, Family.PEAR,
    )

    /** Category colour per glyph name (without `ic_ip_`); see decisions 5b (U/E9) for the table. */
    val byGlyph: Map<String, Int> = buildMap {
        fun put(color: Int, vararg glyphs: String) = glyphs.forEach { put(it, color) }
        put(Family.PERSIMMON, "cat_home", "act_home") // home
        put(Family.AZURE, "cat_office", "act_briefcase", "cat_jobs", "act_organization") // work
        put(Family.GREEN, "cat_fitness", "cat_soccer_game", "cat_basketball_game", "cat_baseball_game",
            "cat_american_football_game", "cat_golf_game", "cat_hockey_game", "cat_racquet_sports_game",
            "cat_bowling_game", "cat_boxing_game", "cat_martial_arts_game", "cat_winter_sports_game",
            "cat_cricket_game", "cat_sports_news", "cat_horses_game") // sport
        put(Family.RED, "cat_food") // food
        put(Family.AMBER, "act_cup") // café
        put(Family.SHOCKING_PINK, "cat_shopping", "act_credit_card") // shopping
        put(Family.RASPBERRY, "cat_health", "cat_doctor_game", "cat_dentist_game", "act_heart") // health
        put(Family.SKY, "cat_air_travel", "cat_travel", "act_travel_explore", "act_globe", "act_sun_horizon") // travel
        put(Family.SKY, "cat_tower_defense_game") // castle / landmark (decisions 5b E12): sightseeing, like travel
        put(Family.PURPLE, "cat_education", "cat_educational_game", "cat_language_education", "cat_books") // school
        put(Family.TEAL, "cat_cars", "cat_public_transport", "cat_taxi_and_rideshare", "cat_maps_and_navigation") // transport
        put(Family.TURQUOISE, "act_jellyfish", "act_anycons_koi") // water / pool
        put(Family.YELLOW, "cat_children", "cat_baby_care_game") // children
        put(Family.JADE, "cat_banking", "cat_insurance", "cat_trading", "act_coin") // money
        put(Family.PERIWINKLE, "cat_movies", "cat_music", "cat_video_player", "act_popcorn") // entertainment
        put(Family.LAVENDER, "cat_religious_text") // religious
        put(Family.LIME, "act_sapling", "act_tulip", "act_lotus", "act_butterfly") // nature
        put(Family.MAGENTA, "cat_contacts", "cat_social_media", "act_user_circled", "act_hand_waving") // people
    }

    /** The destination's colour for APP_COLORS palettes: its glyph's category, else stable per [entryId]. */
    fun colorFor(glyphName: String, entryId: String): Int =
        byGlyph[glyphName] ?: fallback[Math.floorMod(entryId.hashCode(), fallback.size)]

    /** Whether [style] colours icons per destination ("App colours" palettes). */
    fun usesAppColor(style: AnyconSettings): Boolean = when (val s = style.style) {
        is AnyconStyle.Sticker -> s.palette == AnyconStyle.Sticker.Palette.APP_COLORS
        is AnyconStyle.Dots -> s.palette == AppOrThemePalette.APP_COLORS
        is AnyconStyle.OutlineChroma -> s.palette == AppOrThemePalette.APP_COLORS
        is AnyconStyle.Watercolor -> s.palette == AppOrThemePalette.APP_COLORS
        is AnyconStyle.Bright -> s.palette == AppOrThemePalette.APP_COLORS
        else -> false
    }
}
