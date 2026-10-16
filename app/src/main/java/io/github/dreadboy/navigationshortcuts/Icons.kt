package io.github.dreadboy.navigationshortcuts

import androidx.annotation.DrawableRes
import java.util.Locale

/**
 * Built-in Material-style icon set (PLAN 3.2): the default for new entries, the picker and the name guess.
 * Direct `R.drawable` references (never getIdentifier), so release resource shrinking keeps every icon.
 * Order = picker order.
 */
object BuiltInIcons {
    const val PIN = "pin"

    val all: List<Pair<String, Int>> = listOf(
        PIN to R.drawable.ic_place_pin,
        "home" to R.drawable.ic_icon_home,
        "work" to R.drawable.ic_icon_work,
        "gym" to R.drawable.ic_icon_gym,
        "stadium" to R.drawable.ic_icon_stadium,
        "school" to R.drawable.ic_icon_school,
        "pool" to R.drawable.ic_icon_pool,
        "shop" to R.drawable.ic_icon_shop,
        "restaurant" to R.drawable.ic_icon_restaurant,
        "cafe" to R.drawable.ic_icon_cafe,
        "hospital" to R.drawable.ic_icon_hospital,
        "airport" to R.drawable.ic_icon_airport,
        "parking" to R.drawable.ic_icon_parking,
        "mountain" to R.drawable.ic_icon_mountain,
    )

    private val byName = all.toMap()

    fun drawable(name: String): Int? = byName[name]
}

/**
 * Resolves a stored [IconRef] to a drawable for RemoteViews and the share sheet. Only `builtin` exists today.
 * Phase 5 adds `niagara` (icons loaded from Niagara at runtime) as another branch here, with the built-in icon of
 * the same name as its fallback. Unknown source or name → built-in pin.
 */
object IconResolver {
    @DrawableRes
    fun drawableRes(icon: IconRef): Int = when (icon.source) {
        IconRef.SOURCE_BUILTIN -> BuiltInIcons.drawable(icon.name)
        else -> null
    } ?: R.drawable.ic_place_pin
}

/**
 * Cheap guess of an icon from words in the place name (Maps shares no category when signed out,
 * decisions 1.5). Slovenian and English words; anything else → pin.
 */
object IconGuess {
    /** Token must equal one of these (short words that would otherwise match too much as prefixes). */
    private val exact = mapOf(
        "home" to "home", "dom" to "home",
        "work" to "work", "služba" to "work", "sluzba" to "work", "office" to "work", "pisarna" to "work",
        "shop" to "shop", "store" to "shop", "market" to "shop", "supermarket" to "shop",
        "cafe" to "cafe", "café" to "cafe", "coffee" to "cafe",
        "gora" to "mountain", "mountain" to "mountain", "koča" to "mountain", "hut" to "mountain",
    )

    /** Token must start with one of these (covers Slovenian declension: šola/šole/šoli, bazena, …). */
    private val prefixes = listOf(
        "fitnes" to "gym", "gym" to "gym",
        "stadion" to "stadium", "stadium" to "stadium",
        "šol" to "school", "school" to "school",
        "bazen" to "pool", "pool" to "pool", "kopališč" to "pool",
        "trgovin" to "shop",
        "restavracij" to "restaurant", "restaurant" to "restaurant", "gostiln" to "restaurant", "pizzeri" to "restaurant",
        "kavarn" to "cafe",
        "bolnišnic" to "hospital", "bolnica" to "hospital", "hospital" to "hospital",
        "letališč" to "airport", "airport" to "airport",
        "parkirišč" to "parking", "parking" to "parking",
    )

    fun guess(text: String): IconRef {
        val tokens = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        for (token in tokens) {
            exact[token]?.let { return IconRef.builtin(it) }
            prefixes.firstOrNull { token.startsWith(it.first) }?.let { return IconRef.builtin(it.second) }
        }
        return IconRef.builtin(BuiltInIcons.PIN)
    }
}
