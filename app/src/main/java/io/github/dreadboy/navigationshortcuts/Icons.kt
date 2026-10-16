package io.github.dreadboy.navigationshortcuts

import androidx.annotation.DrawableRes
import java.text.Normalizer
import java.util.Locale

/**
 * Built-in Material-style icon set (PLAN 3.2): used only when Niagara's glyphs can't be read (decisions 5.6) — the
 * picker's only set when Niagara is missing and the fallback whenever a Niagara glyph can't be drawn. While Niagara is
 * readable, `builtin` refs are drawn as their Niagara equivalent ([NiagaraGlyphs.forConcept]). Direct `R.drawable`
 * references (never getIdentifier), so release resource shrinking keeps every icon. Order = picker order.
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
 * Niagara Anycon glyph names (resource `ic_ip_<name>` in `bitpit.launcher`, decisions 5.2/5.5/5.6) that the name guess
 * uses, the glyph each built-in icon is drawn as while Niagara is readable, and the built-in icon each glyph falls back
 * to when it can't be loaded.
 */
object NiagaraGlyphs {
    /**
     * Built-in concept → Niagara glyph, for every built-in icon (decisions 5.6): the name guess stores these, and
     * `builtin` refs are drawn as them while Niagara is readable. Niagara has no stadium, pool, parking or mountain
     * glyph; those use the nearest one. Values are distinct, so the reverse ([fallbackFor]) is unambiguous.
     */
    val forConcept: Map<String, String> = mapOf(
        BuiltInIcons.PIN to "act_location",
        "home" to "cat_home",
        "work" to "cat_office",
        "gym" to "cat_fitness",
        "school" to "cat_education",
        "shop" to "cat_shopping",
        "restaurant" to "cat_food",
        "cafe" to "act_cup",
        "hospital" to "cat_health",
        "airport" to "cat_air_travel",
        "stadium" to "cat_soccer_game",
        "pool" to "act_jellyfish",
        "parking" to "cat_cars",
        "mountain" to "act_sun_horizon",
    )

    /**
     * Niagara glyphs to try for [icon], best first: a `niagara` ref's own glyph, then its concept's glyph (a renamed
     * glyph still shows a Niagara icon); a `builtin` (or unknown) ref's equivalent glyph, else the location pin.
     */
    fun glyphsFor(icon: IconRef): List<String> = when (icon.source) {
        IconRef.SOURCE_NIAGARA -> listOfNotNull(icon.name, forConcept[fallbackFor(icon.name)])
        IconRef.SOURCE_BUILTIN -> listOfNotNull(forConcept[icon.name], forConcept[BuiltInIcons.PIN])
        else -> listOfNotNull(forConcept[BuiltInIcons.PIN])
    }.distinct()

    private val fallback: Map<String, String> = forConcept.entries.associate { (concept, glyph) -> glyph to concept } +
        mapOf(
            "act_home" to "home",
            "act_briefcase" to "work",
            "cat_jobs" to "work",
            "cat_educational_game" to "school",
            "cat_children" to "school",
            "cat_doctor_game" to "hospital",
            "cat_flight_simulator_game" to "airport",
            "cat_maps_and_navigation" to BuiltInIcons.PIN,
        )

    /** Built-in icon used when Niagara glyph [glyphName] can't be drawn. */
    fun fallbackFor(glyphName: String): String = fallback[glyphName] ?: BuiltInIcons.PIN

    /** Glyphs never offered in the picker: weather glyphs use colour attributes the packs don't define (decisions 5.2). */
    fun isPickable(glyphName: String): Boolean =
        (glyphName.startsWith("cat_") || glyphName.startsWith("act_")) && !glyphName.startsWith("act_weather_")

    /** Glyphs that render broken in a pack (48×48 viewport with a 12,12 pivot, PLAN backlog): hidden for Sticker. */
    val brokenInSticker = setOf("act_pause_circled_48", "act_play_circled_48")

    /** "cat_air_travel" → "air travel" (picker label and search text). */
    fun displayName(glyphName: String): String = glyphName.substringAfter('_').replace('_', ' ')
}

/**
 * Resolves a stored [IconRef] to a built-in drawable: the icon itself for `builtin`, the fallback for `niagara`
 * (used whenever the Niagara glyph can't be drawn). Unknown source or name → built-in pin.
 */
object IconResolver {
    @DrawableRes
    fun drawableRes(icon: IconRef): Int = when (icon.source) {
        IconRef.SOURCE_BUILTIN -> BuiltInIcons.drawable(icon.name)
        IconRef.SOURCE_NIAGARA -> BuiltInIcons.drawable(NiagaraGlyphs.fallbackFor(icon.name))
        else -> null
    } ?: R.drawable.ic_place_pin
}

/**
 * Cheap guess of an icon from words in the place's label and destination address (Maps shares no category when
 * signed out, decisions 1.5, 5.8). Slovenian and English words, ignoring case and diacritics ("Športno" = "sportno").
 * Matches whole words, listed declined forms, or long-enough stems — never short words inside other words ("spar" ≠
 * "Sparkasse", "dom" ≠ "Domžale"), and no stems that street names share ("Šolska ulica", "Stadionska ulica",
 * "Letališka cesta" are not a school, stadium or airport). The label wins over the address. The concept maps to a
 * Niagara glyph (decisions 5.6), drawn as the built-in Material icon only while Niagara is missing.
 */
object IconGuess {
    /** Whole words (after [normalize]) → concept. */
    private val words: Map<String, String> = buildMap {
        fun add(concept: String, vararg ws: String) = ws.forEach { put(it, concept) }
        add("home", "home", "dom", "doma", "domov")
        add("work", "work", "office", "sluzba", "pisarna", "podjetje", "doo")
        add("gym", "gym", "fitness", "fitnes", "fitnesa", "sport", "sporta", "sportu")
        add("stadium", "stadium", "stadion", "stadiona", "stadionu", "stadionom")
        add("pool", "pool", "bazen", "bazena", "bazenu", "bazeni", "bazenom")
        add("school", "school", "sola", "sole", "soli", "solah", "solam", "solama", "solami")
        add(
            "shop", "shop", "market", "supermarket", "hipermarket", "tus", "mercator", "spar", "eurospar",
            "interspar", "hofer", "lidl",
        )
        add("restaurant", "pizza")
        add("cafe", "cafe", "coffee")
        add("hospital", "ambulanta", "ambulante", "ambulanti")
        add(
            "mountain", "mountain", "hut", "gora", "gore", "gori", "goro", "hrib", "hriba", "hribu", "koca", "koce",
            "koci", "koco", "planina", "planine", "planini", "planino",
        )
    }

    /** Word stems (word must start with one; long or distinctive enough not to hit street names) → concept. */
    private val stems: List<Pair<String, String>> = listOf(
        "sportn" to "gym", "fitnes" to "gym", "telovadnic" to "gym",
        "kopalisc" to "pool", "swimming" to "pool",
        "fakultet" to "school", "gimnazij" to "school",
        "trgovin" to "shop",
        "restavracij" to "restaurant", "restaurant" to "restaurant", "gostiln" to "restaurant",
        "picerij" to "restaurant", "pizzeri" to "restaurant",
        "kavarn" to "cafe", "kafic" to "cafe",
        "bolnisnic" to "hospital", "bolnic" to "hospital", "hospital" to "hospital",
        "letalisc" to "airport", "airport" to "airport",
        "parkirisc" to "parking", "parking" to "parking",
    )

    /** Two-word phrases (first stem, second stem) → concept; checked before single words ("zdravstveni dom" ≠ home). */
    private val phrases: List<Triple<String, String, String>> = listOf(
        Triple("zdravstven", "dom", "hospital"),
        Triple("garazn", "his", "parking"),
        Triple("planinsk", "dom", "mountain"),
        Triple("solsk", "center", "school"),
    )

    /** Lowercase, diacritics removed ("Športno društvo" → "sportno drustvo"). */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** The concept (built-in icon name) for [label], else for [destination], else [BuiltInIcons.PIN]. */
    fun concept(label: String, destination: String = ""): String =
        conceptIn(label) ?: conceptIn(destination) ?: BuiltInIcons.PIN

    private fun conceptIn(text: String): String? {
        // "d.o.o." splits into d, o, o: join it back into one word first.
        val tokens = normalize(text).replace(Regex("\\bd\\.\\s*o\\.\\s*o\\b\\.?"), " doo ")
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        for ((i, token) in tokens.withIndex()) {
            val next = tokens.getOrNull(i + 1)
            if (next != null) {
                phrases.firstOrNull { (a, b) -> token.startsWith(a) && next.startsWith(b) }?.let { return it.third }
            }
            words[token]?.let { return it }
            stems.firstOrNull { token.startsWith(it.first) }?.let { return it.second }
        }
        return null
    }

    fun guess(label: String, destination: String = ""): IconRef = refFor(concept(label, destination))

    /** Niagara glyph for [concept] (every concept has one, decisions 5.6); drawn as Material while Niagara is missing. */
    fun refFor(concept: String): IconRef =
        NiagaraGlyphs.forConcept[concept]?.let { IconRef.niagara(it) } ?: IconRef.builtin(concept)
}
