package io.github.dreadboy.navigationshortcuts.anycons.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.dreadboy.navigationshortcuts.anycons.glyph.AttrRoleResolver
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphParser
import io.github.dreadboy.navigationshortcuts.anycons.glyph.PaintRole

/**
 * Reads Anycon glyphs and textures from the installed Niagara launcher (`bitpit.launcher`) at runtime.
 *
 * Glyph XML is parsed directly ([GlyphParser]) rather than inflated with Niagara's themes, which gives per-path
 * control and avoids unresolved theme attributes. Theme-attribute ids are resolved by name, so Niagara rebuilds that
 * shift the ids keep working. Resources are looked up by name with `getIdentifier` because they belong to another
 * package and have no R constants here.
 *
 * Every call returns null on any failure (Niagara missing, name missing, parse or decode error). Results, including
 * misses, are cached until [invalidate].
 */
class NiagaraGlyphSource(context: Context) : GlyphSource {
    private val appContext = context.applicationContext ?: context
    private val lock = Any()
    private var resources: Resources? = null
    private var resourcesLoaded = false
    private var parser: GlyphParser? = null
    private val glyphs = HashMap<String, Glyph?>()
    private val textures = HashMap<Texture, Bitmap?>()

    /** Whether the Niagara package and its resources are reachable. */
    val isAvailable: Boolean get() = synchronized(lock) { niagaraResources() != null }

    override fun glyph(name: String): Glyph? = synchronized(lock) {
        if (glyphs.containsKey(name)) return glyphs[name]
        val result = try {
            loadGlyph(name)
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        glyphs[name] = result
        result
    }

    override fun texture(texture: Texture): Bitmap? = synchronized(lock) {
        if (textures.containsKey(texture)) return textures[texture]
        val result = try {
            loadTexture(texture)
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        textures[texture] = result
        result
    }

    /** Drops cached resources (e.g. after Niagara was installed, updated or removed). */
    fun invalidate() = synchronized(lock) {
        resources = null
        resourcesLoaded = false
        parser = null
        glyphs.clear()
        textures.clear()
    }

    private fun niagaraResources(): Resources? {
        if (!resourcesLoaded) {
            resourcesLoaded = true
            resources = try {
                appContext.packageManager.getResourcesForApplication(PACKAGE)
            } catch (_: Exception) {
                null
            }
        }
        return resources
    }

    @SuppressLint("DiscouragedApi") // Another package's resources: lookup by name is the only way.
    private fun identifier(res: Resources, name: String, type: String): Int = res.getIdentifier(name, type, PACKAGE)

    private fun glyphParser(res: Resources): GlyphParser = parser ?: run {
        val roles = HashMap<Int, PaintRole>()
        identifier(res, GlyphParser.ATTR_BG, "attr").takeIf { it != 0 }?.let { roles[it] = PaintRole.BG }
        identifier(res, GlyphParser.ATTR_FG, "attr").takeIf { it != 0 }?.let { roles[it] = PaintRole.FG }
        identifier(res, GlyphParser.ATTR_STROKE, "attr").takeIf { it != 0 }?.let { roles[it] = PaintRole.STROKE }
        GlyphParser(AttrRoleResolver { roles[it] }).also { parser = it }
    }

    private fun loadGlyph(name: String): Glyph? {
        val res = niagaraResources() ?: return null
        val id = identifier(res, GLYPH_PREFIX + name, "drawable")
        if (id == 0) return null
        val p = glyphParser(res)
        return res.getXml(id).use { xml -> p.parse(name, xml) }
    }

    private fun loadTexture(texture: Texture): Bitmap? {
        val res = niagaraResources() ?: return null
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        for (candidate in texture.candidateNames) {
            val id = identifier(res, candidate, "drawable")
            if (id == 0) continue
            return BitmapFactory.decodeResource(res, id, options)
        }
        return null
    }

    companion object {
        const val PACKAGE = "bitpit.launcher"
        const val GLYPH_PREFIX = "ic_ip_"
    }
}
