package io.github.dreadboy.navigationshortcuts

/**
 * What the icon picker offers, in which order, and how search matches (decisions 5b, E6/5b.4). Pure logic, no
 * Android views: [IconPicker] shows it.
 *
 * Order: the current icon on top, then destination types ([places], curated, with English and Slovenian synonyms),
 * then every other category/action glyph by name, then the per-app glyphs by app name. A glyph is listed once:
 * `act_X` is dropped when `cat_X` exists ("home" twice was confusing).
 */
object IconCatalog {
    enum class Section { CURRENT, PLACES, MORE, APPS }

    /** One picker cell. [search] is the normalised text the query is matched against (label plus synonyms). */
    data class Entry(val ref: IconRef, val label: String, val section: Section, val search: String)

    /**
     * Destination-type glyphs in picker order, each with search synonyms (English, Slovenian without diacritics —
     * the query is normalised the same way, so "šola" finds "sola").
     */
    val places: List<Pair<String, List<String>>> = listOf(
        "cat_home" to listOf("home", "house", "flat", "apartment", "dom", "doma", "domov", "hisa", "stanovanje"),
        "cat_office" to listOf("work", "office", "company", "sluzba", "pisarna", "podjetje", "delo", "firma"),
        "cat_jobs" to listOf("work", "job", "business", "briefcase", "sluzba", "delo", "posel"),
        "cat_fitness" to listOf(
            "gym", "fitness", "workout", "sport", "sports", "sports complex", "training", "running", "telovadnica",
            "fitnes", "sport", "sportno drustvo", "sportni center", "trening", "vadba", "tek",
        ),
        "cat_shopping" to listOf(
            "shop", "store", "mall", "market", "supermarket", "groceries", "shopping center", "trgovina",
            "nakupovalni center", "nakupi", "tus", "mercator", "spar", "hofer", "lidl", "btc",
        ),
        "cat_food" to listOf(
            "eat", "restaurant", "dinner", "lunch", "pizza", "pizzeria", "restavracija", "gostilna", "hrana", "kosilo",
            "vecerja", "picerija", "jesti",
        ),
        "act_cup" to listOf("cafe", "coffee", "tea", "bar", "pub", "cup", "kava", "kavarna", "kafic", "caj", "lokal"),
        "cat_health" to listOf(
            "health", "hospital", "clinic", "doctor", "pharmacy", "bolnisnica", "bolnica", "zdravstveni dom",
            "ambulanta", "lekarna", "zdravnik", "zdravje",
        ),
        "cat_doctor_game" to listOf("doctor", "clinic", "hospital", "stethoscope", "zdravnik", "ambulanta", "bolnisnica"),
        "cat_dentist_game" to listOf("dentist", "tooth", "zobozdravnik", "zobar"),
        "cat_education" to listOf(
            "school", "university", "uni", "college", "faculty", "education", "sola", "fakulteta", "gimnazija",
            "univerza", "faks", "ucenje",
        ),
        "cat_children" to listOf("kids", "children", "kindergarten", "playground", "vrtec", "otroci", "igrisce"),
        "cat_air_travel" to listOf("plane", "airport", "flight", "fly", "letalisce", "letalo", "let"),
        "cat_travel" to listOf(
            "travel", "trip", "holiday", "vacation", "hiking", "mountain", "hut", "backpack", "potovanje", "dopust",
            "izlet", "gora", "hrib", "koca", "planina",
        ),
        "cat_cars" to listOf("car", "parking", "garage", "mechanic", "avto", "parkirisce", "garaza", "servis"),
        "cat_public_transport" to listOf("bus", "tram", "metro", "station", "avtobus", "postaja", "lpp"),
        "cat_train_simulator_game" to listOf("train", "railway", "station", "vlak", "zeleznica", "zelezniska postaja"),
        "cat_taxi_and_rideshare" to listOf("taxi", "cab", "uber", "rideshare", "taksi"),
        "act_location" to listOf("pin", "place", "location", "marker", "lokacija", "kraj", "mesto"),
        "cat_banking" to listOf("bank", "atm", "money", "banka", "bankomat", "denar"),
        "cat_movies" to listOf("cinema", "movie", "film", "theatre", "theater", "kino", "gledalisce"),
        "cat_books" to listOf("library", "bookstore", "books", "knjiznica", "knjigarna", "knjige"),
        "cat_music" to listOf("music", "concert", "club", "koncert", "glasba"),
        "cat_religious_text" to listOf("church", "religion", "mosque", "temple", "chapel", "cerkev", "kapela"),
        "cat_tower_defense_game" to listOf(
            "castle", "landmark", "tower", "fortress", "monument", "grad", "trdnjava", "dvorec", "znamenitost",
            "stolp",
        ),
        "cat_city_building_game" to listOf("city", "town", "downtown", "center", "mesto", "center mesta"),
        "act_organization" to listOf("building", "organization", "company", "hotel", "stavba", "zgradba"),
        "cat_soccer_game" to listOf("stadium", "football", "soccer", "pitch", "stadion", "nogomet", "igrisce"),
        "act_jellyfish" to listOf(
            "pool", "swim", "swimming", "aquapark", "beach", "sea", "jellyfish", "bazen", "kopalisce", "plavanje",
            "morje", "plaza",
        ),
        "cat_racquet_sports_game" to listOf("tennis", "badminton", "squash", "padel", "tenis"),
        "cat_basketball_game" to listOf("basketball", "kosarka"),
        "cat_golf_game" to listOf("golf"),
        "cat_winter_sports_game" to listOf("ski", "skiing", "snow", "winter", "smucanje", "smucisce", "sneg"),
        "cat_horses_game" to listOf("horse", "riding", "stable", "konj", "jahanje"),
        "cat_bowling_game" to listOf("bowling", "kegljisce"),
        "cat_hair_salon_game" to listOf("hairdresser", "barber", "salon", "frizer", "frizerski salon"),
        "cat_nail_salon_game" to listOf("nails", "beauty", "manicure", "kozmetika", "manikura"),
        "act_sun_horizon" to listOf("beach", "sea", "sunset", "sunrise", "view", "plaza", "morje", "razgled"),
        "act_sapling" to listOf("park", "garden", "nature", "forest", "vrt", "narava", "gozd"),
        "act_mail" to listOf("post office", "mail", "posta"),
        "act_heart" to listOf("love", "favourite", "favorite", "family", "partner", "druzina", "ljubezen"),
    )

    private val placeOrder: Map<String, Int> = places.withIndex().associate { (i, p) -> p.first to i }
    private val synonyms: Map<String, List<String>> = places.toMap()

    /**
     * Picker entries while Niagara is readable, from the enumerated [glyphs] (minus [hidden] ones for the style):
     * places, then other glyphs by label, then apps by app name; duplicates removed.
     */
    fun niagara(glyphs: List<String>, hidden: Set<String> = emptySet()): List<Entry> {
        val available = glyphs.filter { NiagaraGlyphs.isPickable(it) && it !in hidden }.toSet()
        val shown = available.filterNot { isDuplicate(it, available) }
        val places = shown.filter { it in placeOrder }.sortedBy { placeOrder.getValue(it) }
        val apps = shown.filter(NiagaraGlyphs::isApp)
        val more = shown.filter { it !in placeOrder && !NiagaraGlyphs.isApp(it) }
        return places.map { entry(it, Section.PLACES) } +
            more.map { entry(it, Section.MORE) }.sortedBy { it.label.lowercase() } +
            apps.map { entry(it, Section.APPS) }.sortedBy { it.label.lowercase() }
    }

    /** `act_X` duplicates `cat_X` (act_home vs cat_home): only the category glyph is offered. */
    fun isDuplicate(glyph: String, available: Set<String>): Boolean =
        glyph.startsWith("act_") && "cat_" + glyph.removePrefix("act_") in available

    /** Picker entries while Niagara is missing: the built-in Material icons, with their Niagara glyph's synonyms. */
    fun builtin(): List<Entry> = BuiltInIcons.all.map { (name, _) ->
        val glyph = NiagaraGlyphs.forConcept[name]
        val label = IconNames.builtin(name)
        Entry(IconRef.builtin(name), label, Section.PLACES, searchText(listOf(label, name) + synonyms[glyph].orEmpty()))
    }

    private fun entry(glyph: String, section: Section): Entry {
        val label = NiagaraGlyphs.displayName(glyph)
        val extra = when {
            NiagaraGlyphs.isApp(glyph) -> AppGlyphNames.packageWords(glyph)
            else -> listOf(glyph.substringAfter('_').replace('_', ' ')) + concepts[glyph].orEmpty()
        }
        return Entry(IconRef.niagara(glyph), label, section, searchText(listOf(label) + extra + synonyms[glyph].orEmpty()))
    }

    /** The built-in concept names find their mapped glyph ("pool" → jellyfish, decisions 5.6). */
    private val concepts: Map<String, List<String>> =
        NiagaraGlyphs.forConcept.entries.groupBy({ it.value }, { it.key })

    private fun searchText(terms: List<String>): String = " " + IconGuess.normalize(terms.joinToString(" ")) + " "

    /**
     * [all] with the [current] entry first (section [Section.CURRENT]) when it is known; [current] stays in its own
     * section too, highlighted there.
     */
    fun withCurrent(all: List<Entry>, current: IconRef?): List<Entry> {
        if (current == null) return all
        val match = all.firstOrNull { it.ref == current }
        val top = match?.copy(section = Section.CURRENT)
            ?: if (current.source == IconRef.SOURCE_NIAGARA) entry(current.name, Section.CURRENT) else null
        return listOfNotNull(top) + all
    }

    /**
     * Entries matching [query]: every query word must start a word of the entry's label or synonyms ("car" finds
     * "Cars" and "Car racing", not "Scar"); case and diacritics ignored. Empty query → [all] unchanged.
     */
    fun filter(all: List<Entry>, query: String): List<Entry> {
        val words = IconGuess.normalize(query).split(Regex("[^\\p{L}\\p{N}+.]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { e -> e.section != Section.CURRENT && words.all { w -> " $w" in e.search } }
    }
}
