package io.github.dreadboy.navigationshortcuts

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * Tells displayed widgets apart from "ghost" widget IDs (decisions 3.9). Every render of a widget carries a 1 px
 * image whose URI points at [RenderProbeProvider] with the widget ID and a per-widget render generation. A launcher
 * that really shows the widget applies the RemoteViews to a host view and loads that image from us, which records
 * "generation G of widget W was displayed". A widget ID that is still bound but no longer shown (Niagara's
 * "Replace widget" keeps the old ID bound) has no host view, so nothing is ever loaded for it.
 */
object RenderProbe {
    private const val PREFS = "render_probe"

    fun authority(context: Context) = "${context.packageName}.renderprobe"

    /** Next generation for [appWidgetId]; called once per pushed render. */
    fun nextGeneration(context: Context, appWidgetId: Int): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pushed = prefs.getLong("pushed_$appWidgetId", 0)
        val next = pushed + 1
        val now = System.currentTimeMillis()
        val editor = prefs.edit().putLong("pushed_$appWidgetId", next).putLong("pushed_at_$appWidgetId", now)
        // First render the launcher has not displayed yet: remember since when this widget is unconfirmed.
        if (prefs.getLong("shown_$appWidgetId", 0) >= pushed) editor.putLong("unconfirmed_since_$appWidgetId", now)
        editor.apply()
        return next
    }

    fun uri(context: Context, appWidgetId: Int, generation: Long): Uri =
        Uri.parse("content://${authority(context)}/probe/$appWidgetId/$generation.png")

    /**
     * Records that render [generation] of [appWidgetId] was displayed. Ignored unless the widget is bound to us and we
     * actually pushed that generation: the provider is exported (the launcher must read it), so any app could call it;
     * this keeps a caller from inventing widgets/keys or pre-confirming renders we haven't sent. Returns whether recorded.
     */
    fun recordDisplayed(context: Context, appWidgetId: Int, generation: Long, boundIds: Collection<Int>): Boolean {
        if (appWidgetId !in boundIds) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (generation < 1 || generation > prefs.getLong("pushed_$appWidgetId", 0)) return false
        if (generation > prefs.getLong("shown_$appWidgetId", 0)) {
            prefs.edit().putLong("shown_$appWidgetId", generation)
                .putLong("shown_at_$appWidgetId", System.currentTimeMillis()).apply()
        }
        return true
    }

    data class Status(val pushed: Long, val shown: Long, val pushedAt: Long, val shownAt: Long, val unconfirmedSince: Long) {
        /** The launcher displayed the latest render (or we never rendered it, e.g. right after an update). */
        val latestShown: Boolean get() = shown >= pushed
    }

    fun status(context: Context, appWidgetId: Int): Status {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Status(
            pushed = prefs.getLong("pushed_$appWidgetId", 0),
            shown = prefs.getLong("shown_$appWidgetId", 0),
            pushedAt = prefs.getLong("pushed_at_$appWidgetId", 0),
            shownAt = prefs.getLong("shown_at_$appWidgetId", 0),
            unconfirmedSince = prefs.getLong("unconfirmed_since_$appWidgetId", 0),
        )
    }

    fun forget(context: Context, appWidgetIds: Collection<Int>) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (id in appWidgetIds) {
            listOf("pushed_", "pushed_at_", "shown_", "shown_at_", "unconfirmed_since_").forEach { editor.remove(it + id) }
        }
        editor.apply()
    }
}

/** Serves the probe image (1×1 transparent PNG) and records which widget render was displayed. */
class RenderProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val segments = uri.pathSegments // probe/<widgetId>/<generation>.png
        val widgetId = segments.getOrNull(1)?.toIntOrNull()
        val generation = segments.getOrNull(2)?.substringBefore('.')?.toLongOrNull()
        // A launcher applying a render only ever reads; anything else is not a display.
        if (mode == "r" && widgetId != null && generation != null) {
            val bound = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NavigationWidgetProvider::class.java)).toList()
            val recorded = RenderProbe.recordDisplayed(context, widgetId, generation, bound)
            Log.d(TAG, "displayed widget=$widgetId generation=$generation by uid=${Binder.getCallingUid()} recorded=$recorded")
        }
        return ParcelFileDescriptor.open(pixel(context), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun pixel(context: Context): File {
        val file = File(context.cacheDir, "probe_pixel.png")
        if (!file.exists()) file.writeBytes(TRANSPARENT_PNG)
        return file
    }

    override fun getType(uri: Uri): String = "image/png"
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    private companion object {
        const val TAG = "NavShortProbe"

        /** 1×1 fully transparent PNG. */
        val TRANSPARENT_PNG: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte(),
        )
    }
}
