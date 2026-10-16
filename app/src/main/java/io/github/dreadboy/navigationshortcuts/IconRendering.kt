package io.github.dreadboy.navigationshortcuts

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import io.github.dreadboy.navigationshortcuts.anycons.AnyconRenderer
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.source.NiagaraGlyphSource
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws `niagara` icons (decisions 5.3/5.5): glyphs read from the installed Niagara, composed by [AnyconRenderer] in the
 * widget's style. Process-wide, so glyphs and bitmaps are cached across widget updates; rebuilt when Niagara is
 * installed, updated or removed (its `lastUpdateTime` changes). Call off the main thread.
 *
 * Availability is decided by the glyph source, never by a non-null bitmap (Dots draws without any glyph).
 */
object IconRendering {
    private const val TAG = "NavShortIcons"

    /** Largest icon bitmap per cell (px). */
    const val MAX_CELL_PX = 128

    /**
     * Total icon bitmap bytes per widget update. RemoteViews travel through a binder transaction (~1 MB buffer per
     * process), so the grid's bitmaps stay well under half of that (decisions 5.5).
     */
    const val BITMAP_BUDGET_BYTES = 400 * 1024

    private val lock = Any()
    private var stamp: Long? = null
    private var session: Session? = null
    private var glyphNames: List<String>? = null
    private var loggedXmlForms = false

    /** One consistent view of Niagara for a batch of renders. */
    class Session(
        val source: NiagaraGlyphSource,
        val renderer: AnyconRenderer,
        /** Niagara is installed and its resources are readable. */
        val niagaraInstalled: Boolean,
    ) {
        /** Whether glyph [name] can be read from Niagara (the only availability signal). */
        fun hasGlyph(name: String): Boolean = niagaraInstalled && source.glyph(name) != null
    }

    /** Result of drawing one icon: the bitmap, or null → use the built-in fallback; timings for the 5.3 measurements. */
    data class Drawn(val bitmap: Bitmap?, val glyphMs: Double, val renderMs: Double)

    fun session(context: Context): Session = synchronized(lock) {
        val current = niagaraStamp(context)
        val existing = session
        if (existing != null && current == stamp) return existing
        if (existing != null) Log.i(TAG, "Niagara changed (lastUpdateTime $stamp → $current): dropping glyph caches")
        val source = NiagaraGlyphSource(context)
        Session(source, AnyconRenderer(source), source.isAvailable).also {
            session = it
            stamp = current
            glyphNames = null
            if (BuildConfig.DEBUG && it.niagaraInstalled && !loggedXmlForms) {
                loggedXmlForms = true
                logXmlAttributeForms(context)
            }
        }
    }

    /**
     * Draws [icon] in [style] at [sizePx], seeded per destination by [seedKey] (the entry id), as a Niagara glyph:
     * its own for `niagara` refs, the equivalent for `builtin` refs ([NiagaraGlyphs.glyphsFor], decisions 5.6). Null
     * when Niagara is missing or no candidate glyph can be loaded or drawn: the caller shows the built-in fallback.
     */
    fun draw(session: Session, icon: IconRef, style: AnyconSettings, sizePx: Int, seedKey: String): Drawn {
        if (!session.niagaraInstalled) return Drawn(null, 0.0, 0.0)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val glyph = NiagaraGlyphs.glyphsFor(icon).firstOrNull { name ->
            try {
                session.hasGlyph(name)
            } catch (e: Exception) {
                Log.w(TAG, "glyph $name failed", e)
                false
            }
        }
        val t1 = SystemClock.elapsedRealtimeNanos()
        val bitmap = if (glyph == null) null else try {
            // "App colours": each destination gets its own colour, by category or per entry (decisions 5b, U/E9).
            val color = if (DestinationColors.usesAppColor(style)) DestinationColors.colorFor(glyph, seedKey) else null
            session.renderer.render(glyph, style, sizePx, seedKey, color)
        } catch (e: Exception) {
            Log.w(TAG, "render $glyph failed", e)
            null
        }
        val t2 = SystemClock.elapsedRealtimeNanos()
        return Drawn(bitmap, (t1 - t0) / 1e6, (t2 - t1) / 1e6)
    }

    /**
     * Icon bitmap size for a grid with [bitmapCells] Niagara icons shown at most [targetPx] big (the widget's icon size,
     * decisions 5b U): at most [MAX_CELL_PX], reduced so that all ARGB_8888 bitmaps together stay within
     * [BITMAP_BUDGET_BYTES] (6×6 → 53 px).
     */
    fun cellSizePx(bitmapCells: Int, targetPx: Int = MAX_CELL_PX): Int {
        val cap = min(MAX_CELL_PX, targetPx.coerceAtLeast(1))
        if (bitmapCells <= 0) return cap
        return min(cap, sqrt(BITMAP_BUDGET_BYTES / (4.0 * bitmapCells)).toInt())
    }

    /**
     * Pickable Niagara glyph names (`cat_*` and `act_*`, no weather, no per-app glyphs), sorted, or empty when Niagara
     * is missing. Enumerated from Niagara's drawable IDs (dense per type), so glyphs added by Niagara updates appear.
     */
    fun pickableGlyphs(context: Context, session: Session): List<String> = synchronized(lock) {
        glyphNames?.let { return it }
        val names = if (!session.niagaraInstalled) emptyList() else try {
            val t0 = SystemClock.elapsedRealtime()
            enumerateGlyphs(context).filter(NiagaraGlyphs::isPickable).sorted().also {
                Log.i(TAG, "enumerated ${it.size} pickable Niagara glyphs in ${SystemClock.elapsedRealtime() - t0} ms")
            }
        } catch (e: Exception) {
            Log.w(TAG, "glyph enumeration failed", e)
            emptyList()
        }
        names.also { glyphNames = it }
    }

    @Suppress("DiscouragedApi") // another package's resources: lookup by name is the only way
    private fun enumerateGlyphs(context: Context): List<String> {
        val res = context.packageManager.getResourcesForApplication(NiagaraGlyphSource.PACKAGE)
        val anchor = res.getIdentifier("ic_ip_cat_home", "drawable", NiagaraGlyphSource.PACKAGE)
        if (anchor == 0) return emptyList()
        val base = anchor and 0xFFFF0000.toInt()
        val names = ArrayList<String>()
        var misses = 0
        var entry = 0
        while (entry <= 0xFFFF && misses < 32) {
            try {
                val name = res.getResourceEntryName(base or entry)
                misses = 0
                if (name.startsWith(NiagaraGlyphSource.GLYPH_PREFIX)) names += name.removePrefix(NiagaraGlyphSource.GLYPH_PREFIX)
            } catch (_: Resources.NotFoundException) {
                misses++
            }
            entry++
        }
        return names
    }

    private fun niagaraStamp(context: Context): Long = try {
        context.packageManager.getPackageInfo(NiagaraGlyphSource.PACKAGE, 0).lastUpdateTime
    } catch (_: PackageManager.NameNotFoundException) {
        -1L
    }

    /** Debug builds: which attribute value forms `getXml` returns on this device (decisions 5.3). */
    @Suppress("DiscouragedApi")
    private fun logXmlAttributeForms(context: Context) {
        try {
            val res = context.packageManager.getResourcesForApplication(NiagaraGlyphSource.PACKAGE)
            val id = res.getIdentifier("ic_ip_cat_home", "drawable", NiagaraGlyphSource.PACKAGE)
            res.getXml(id).use { xml ->
                while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                    val attrs = (0 until xml.attributeCount).joinToString(" ") { i ->
                        val value = xml.getAttributeValue(i)
                        "${xml.getAttributeName(i)}=${if (value.length > 24) value.take(24) + "…" else value}"
                    }
                    Log.i(TAG, "getXml ic_ip_cat_home <${xml.name}> $attrs")
                }
            }
            for (attr in listOf("AnyconsBgFillColor", "AnyconsFgFillColor", "AnyconsStrokeColor", "AnyconsScale")) {
                Log.i(TAG, "attr $attr = ?${res.getIdentifier(attr, "attr", NiagaraGlyphSource.PACKAGE)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getXml diagnostics failed", e)
        }
    }
}
