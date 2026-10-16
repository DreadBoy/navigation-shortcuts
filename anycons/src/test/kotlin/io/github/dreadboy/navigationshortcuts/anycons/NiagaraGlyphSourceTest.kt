package io.github.dreadboy.navigationshortcuts.anycons

import org.robolectric.RuntimeEnvironment
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.source.NiagaraGlyphSource
import io.github.dreadboy.navigationshortcuts.anycons.source.Texture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NiagaraGlyphSourceTest {

    @Test
    fun `returns null cleanly when Niagara is not installed`() {
        val source = NiagaraGlyphSource(RuntimeEnvironment.getApplication())
        assertFalse(source.isAvailable)
        assertNull(source.glyph("cat_home"))
        assertNull(source.glyph("cat_home")) // cached miss
        for (t in Texture.entries) assertNull(source.texture(t))
        source.invalidate()
        assertNull(source.glyph("cat_home"))

        val renderer = AnyconRenderer(source)
        assertNull(renderer.render("cat_home", AnyconSettings(), 96, "home"))
    }

    @Test
    fun `texture candidates try the known name first and cover suffix fallbacks`() {
        val names = Texture.MARBLE_OVERLAY.candidateNames
        assertEquals("anycons_tex_marble_overlay_q92", names.first())
        assertTrue(names.contains("anycons_tex_marble_overlay"))
        assertTrue(names.contains("anycons_tex_marble_overlay_q100"))
        assertEquals(names.size, names.toSet().size)
    }
}
