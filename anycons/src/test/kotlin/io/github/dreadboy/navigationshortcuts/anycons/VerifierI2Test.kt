package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.graphics.Color
import io.github.dreadboy.navigationshortcuts.anycons.color.Palettes
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphParser
import io.github.dreadboy.navigationshortcuts.anycons.glyph.PaintRole
import io.github.dreadboy.navigationshortcuts.anycons.glyph.StrokeCap
import io.github.dreadboy.navigationshortcuts.anycons.glyph.StrokeJoin
import io.github.dreadboy.navigationshortcuts.anycons.render.AnyconComposer
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Marble
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Material
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Outline
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Sticker
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness
import io.github.dreadboy.navigationshortcuts.anycons.source.GlyphSource
import io.github.dreadboy.navigationshortcuts.anycons.source.Texture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Random
import kotlin.math.abs

/** Independent checks by verifier I2 (palettes vs a separate Python implementation, Random order, fallbacks). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerifierI2Test {

    private fun close(a: Int, b: Int, tol: Int = 1): Boolean =
        listOf(24, 16, 8, 0).all { s -> abs(((a shr s) and 0xFF) - ((b shr s) and 0xFF)) <= tol }

    @Test
    fun `palettes match independently computed values`() {
        val lines = javaClass.classLoader!!.getResource("verifier-palettes.txt")!!.readText().lines().filter { it.isNotBlank() }
        val failures = mutableListOf<String>()
        for (line in lines) {
            val parts = line.split(" ")
            val seed = parts[0].toInt()
            val key = parts[1]
            val exp = parts.drop(2).map { it.toInt() }
            val act: List<Int> = when (key) {
                "matL" -> Palettes.material(Material.Shade.LIGHT, seed).let { listOf(it.stroke, it.fill, it.background) }
                "matV" -> Palettes.material(Material.Shade.VIVID, seed).let { listOf(it.stroke, it.fill, it.background) }
                "matD" -> Palettes.material(Material.Shade.DARK, seed).let { listOf(it.stroke, it.fill, it.background) }
                "stkT" -> Palettes.sticker(Sticker.Palette.THEME_COLORS, seed, null).let { listOf(it.stroke, it.fill, it.background) }
                "stkA" -> Palettes.sticker(Sticker.Palette.APP_COLORS, 0, seed).let { listOf(it.stroke, it.fill, it.background) }
                "ocT" -> listOf(Palettes.outlineChroma(AppOrThemePalette.THEME_COLORS, seed, null))
                "ocA" -> listOf(Palettes.outlineChroma(AppOrThemePalette.APP_COLORS, 0, seed))
                "wc" -> Palettes.watercolor(AppOrThemePalette.THEME_COLORS, seed, null).let { listOf(it.stroke, it.fill, it.background) }
                "marT" -> listOf(Palettes.marble(Marble.Palette.THEME_COLORS, seed).fill)
                "brT" -> Palettes.bright(AppOrThemePalette.THEME_COLORS, seed, null).let { listOf(it.stroke, it.fill, it.background) }
                "brA" -> Palettes.bright(AppOrThemePalette.APP_COLORS, 0, seed).let { listOf(it.stroke, it.fill, it.background) }
                "st" -> Palettes.stellar(seed).let { listOf(it.stroke, it.fill, it.background) }
                "olL" -> listOf(Palettes.outline(Outline.TextMode.LIGHT_TEXT, seed).fill)
                "olD" -> listOf(Palettes.outline(Outline.TextMode.DARK_TEXT, seed).fill)
                "dots" -> listOf(Palettes.dots(AppOrThemePalette.THEME_COLORS, seed, null))
                else -> error(key)
            }
            exp.zip(act).forEachIndexed { i, (e, a) ->
                if (!close(e, a)) failures += "%08x %s[%d]: expected %08x got %08x".format(seed, key, i, e, a)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // Fixed palettes.
        assertTrue(close(0xFFFFFFFF.toInt(), Palettes.marble(Marble.Palette.WHITE, 0).fill))
        assertTrue(close(0xFF020000.toInt(), Palettes.marble(Marble.Palette.BLACK, 0).fill))
        assertEquals(0xFF121212.toInt(), Palettes.marble(Marble.Palette.WHITE, 0).stroke)
        assertEquals(0xFF482658.toInt(), Palettes.sticker(Sticker.Palette.CORAL, 0, null).stroke)
        // Dots APP: 0x54FFFFFF over #757575 (and over the app colour).
        val dotsApp = Palettes.dots(AppOrThemePalette.APP_COLORS, 0, null)
        val expected = 0x75 + ((0xFF - 0x75) * 0x54 + 127) / 255
        assertTrue("%08x".format(dotsApp), abs(Color.red(dotsApp) - expected) <= 1)
    }

    /** Records the order of Random calls. */
    private class RecordingRandom(seed: Long) : Random(seed) {
        val calls = mutableListOf<String>()
        override fun nextInt(bound: Int): Int = super.nextInt(bound).also { calls += "int$bound" }
        override fun nextBoolean(): Boolean = super.nextBoolean().also { calls += "bool" }
        override fun nextFloat(): Float = super.nextFloat().also { calls += "float" }
        override fun nextDouble(): Double = super.nextDouble().also { calls += "double" }
    }

    @Test
    fun `random call order follows the spec`() {
        val src = TestGlyphs.syntheticSource()
        val g = TestGlyphs.house()
        fun order(style: AnyconStyle): List<String> {
            val r = RecordingRandom(7)
            assertNotNull(AnyconComposer.render(g, AnyconSettings(style), src::texture, 64, r))
            return r.calls
        }
        assertEquals(
            listOf("int4", "int2", "bool", "bool", "int4", "int4", "int2", "bool", "bool", "float"),
            order(AnyconStyle.Watercolor()),
        )
        assertEquals(listOf("int4", "int4", "bool", "bool", "float"), order(Marble()))
        assertEquals(listOf("double", "double", "bool", "bool", "float"), order(AnyconStyle.Stellar))
        assertEquals(emptyList<String>(), order(Material()))
        assertEquals(emptyList<String>(), order(Sticker(Sticker.Palette.CORAL)))
    }

    @Test
    fun `parser handles compiled-xml value forms on tricky layouts`() {
        val xml = """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="16.0dip"
            android:height="24.0dip" android:viewportWidth="16.0" android:viewportHeight="24.0">
            <group android:pivotX="12.0" android:pivotY="12.0" android:scaleX="?2130968578" android:scaleY="?2130968578">
              <path android:fillColor="?2130968577" android:pathData="M1,1L5,5" />
              <path android:fillColor="?2130968579" android:strokeColor="?2130968579" android:strokeWidth="?2130968580"
                 android:strokeLineCap="2" android:strokeLineJoin="2" android:pathData="M1,1L5,5" />
              <path android:fillColor="#00000000" android:strokeColor="?2130968999" android:strokeWidth="?2130968580"
                 android:pathData="M1,1L5,5" />
              <path android:strokeColor="?2130968579" android:strokeWidth="?2130968580" android:strokeLineCap="0"
                 android:pathData="M1,1L5,5" />
            </group></vector>"""
        val roles = mapOf(0x7f040000 to PaintRole.BG, 0x7f040001 to PaintRole.FG, 0x7f040003 to PaintRole.STROKE)
        val g = GlyphParser { roles[it] }.parse("t", NiagaraFixtures.xmlParser(xml))
        assertEquals(16f, g.viewportWidth)
        assertEquals(16f, g.widthDp)
        assertEquals(listOf(PaintRole.FG, PaintRole.STROKE, PaintRole.OTHER, PaintRole.NONE), g.paths.map { it.fillRole })
        assertEquals(listOf(PaintRole.NONE, PaintRole.STROKE, PaintRole.OTHER, PaintRole.STROKE), g.paths.map { it.strokeRole })
        assertEquals(StrokeCap.SQUARE, g.paths[1].strokeCap)
        assertEquals(StrokeJoin.BEVEL, g.paths[1].strokeJoin)
        assertEquals(StrokeCap.BUTT, g.paths[3].strokeCap)
        assertEquals(StrokeJoin.MITER, g.paths[3].strokeJoin)
        assertEquals(12f, g.paths[0].groups.single().pivotX)
    }

    @Test
    fun `niagara tricky glyph roles`() {
        NiagaraFixtures.requireDecoded()
        fun roles(n: String) = NiagaraFixtures.parseGlyph(n).paths.map { it.fillRole to it.strokeRole }
        val n = PaintRole.NONE
        assertEquals(
            listOf(PaintRole.BG to n, PaintRole.FG to n, n to PaintRole.STROKE, PaintRole.STROKE to PaintRole.STROKE),
            roles("cat_go_game"),
        )
        assertEquals(listOf(PaintRole.STROKE to PaintRole.STROKE), roles("act_drag_indicator"))
        val mc = NiagaraFixtures.parseGlyph("app_com_mojang_minecraftpe").paths
        assertEquals(PaintRole.STROKE, mc[3].fillRole)
        assertTrue(roles("act_weather_rain_1_light").all { (f, s) -> f in setOf(n, PaintRole.OTHER) && s in setOf(n, PaintRole.OTHER) })
        val narrow = NiagaraFixtures.parseGlyph("act_flash_small_and_narrow")
        assertEquals(16f, narrow.viewportWidth)
        assertEquals(24f, narrow.viewportHeight)
        assertEquals(StrokeJoin.MITER, NiagaraFixtures.parseGlyph("act_two_colums").paths.single().strokeJoin)
        // Fill-only glyph in the stroke layer of a weather glyph is invisible in every mode.
        val weather = NiagaraFixtures.source.glyph("act_weather_rain_1_light")!!
        val bmp = AnyconComposer.render(
            weather, AnyconSettings(Outline(), launcherShadow = false), NiagaraFixtures.source::texture, 64, Random(1),
        )!!
        val px = IntArray(64 * 64).also { bmp.getPixels(it, 0, 64, 0, 0, 64, 64) }
        assertTrue(px.all { Color.alpha(it) == 0 })
    }

    @Test
    fun `all glyph statistics match the spec`() {
        val dir = File(NiagaraFixtures.requireDecoded(), "res/drawable")
        val files = dir.listFiles { f -> f.name.startsWith("ic_ip_") && f.name.endsWith(".xml") }!!
        assertEquals(891, files.size)
        val glyphs = files.map { NiagaraFixtures.parseGlyph(it.name.removePrefix("ic_ip_").removeSuffix(".xml")) }
        val strokeFilled = glyphs.filter { g -> g.paths.any { it.fillRole == PaintRole.STROKE } }.map { it.name }.sorted()
        assertEquals(
            listOf("act_dots_three_vertical", "act_drag_indicator", "app_com_mojang_minecraftpe", "cat_go_game"),
            strokeFilled,
        )
        val strokePaths = glyphs.flatMap { g -> g.paths.filter { it.strokeRole != PaintRole.NONE }.map { g.name to it } }
        val nonRound = strokePaths.filter { (_, p) -> p.strokeCap != StrokeCap.ROUND || p.strokeJoin != StrokeJoin.ROUND }
        assertEquals(18, nonRound.size)
        assertEquals(16, nonRound.map { it.first }.toSet().size)
        assertEquals(14, nonRound.count { it.second.strokeCap != StrokeCap.ROUND })
        assertEquals(17, nonRound.count { it.second.strokeJoin != StrokeJoin.ROUND })
        val nonSquare = glyphs.filter { it.viewportWidth != 24f || it.viewportHeight != 24f }.map { it.name }.sorted()
        assertEquals(listOf("act_flash_small_and_narrow", "act_pause_circled_48", "act_play_circled_48"), nonSquare)
        // Every glyph renders in every pack-relevant mask mode without throwing.
        for (g in glyphs) {
            for (style in listOf(io.github.dreadboy.navigationshortcuts.anycons.glyph.MaskStyle.MIXED_FILL,
                io.github.dreadboy.navigationshortcuts.anycons.glyph.MaskStyle.STAMP_STROKE)) {
                io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphRasterizer.renderMask(g, style, 24).recycle()
            }
        }
    }

    @Test
    fun `fallbacks return null and never throw`() {
        val g = TestGlyphs.house()
        val throwing = object : GlyphSource {
            override fun glyph(name: String): Glyph = throw IllegalStateException("boom")
            override fun texture(texture: Texture): Bitmap = throw IllegalStateException("boom")
        }
        assertNull(AnyconRenderer(throwing).render("x", AnyconSettings(), 64, "k"))
        val noTextures = object : GlyphSource {
            override fun glyph(name: String): Glyph = g
            override fun texture(texture: Texture): Bitmap? = null
        }
        val r = AnyconRenderer(noTextures)
        for (s in listOf(Sticker(Sticker.Palette.CORAL), Sticker(Sticker.Palette.OPAL), Marble(), AnyconStyle.Watercolor())) {
            assertNull(s.toString(), r.render("x", AnyconSettings(s), 64, "k"))
        }
        assertNotNull(r.render("x", AnyconSettings(AnyconStyle.Stellar), 64, "k"))
        assertNull(r.render("x", AnyconSettings(), 0, "k"))
        // Malformed / truncated XML: the parser throws (NiagaraGlyphSource catches Exception).
        for (bad in listOf("<vector", "<foo/>", "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"/>")) {
            try {
                GlyphParser().parse("bad", NiagaraFixtures.xmlParser(bad))
                fail("expected exception for $bad")
            } catch (e: Exception) {
                // ok: an Exception, not an Error
            }
        }
        // Bad path data is skipped, not fatal.
        val badPath = GlyphParser().parse(
            "bp",
            NiagaraFixtures.xmlParser(
                """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:viewportWidth="24"
                android:viewportHeight="24"><path android:fillColor="?AnyconsBgFillColor" android:pathData="Mzz!!" /></vector>""",
            ),
        )
        assertNotNull(AnyconComposer.render(badPath, AnyconSettings(), { null }, 32, Random(1)))
    }

    @Test
    fun `glyph placement scales`() {
        // Opaque-bbox of the stroke layer for a full-viewport square glyph: 0.62 (Material), 2/3 (Outline), 0.7 (Sticker).
        val sq = GlyphParser().parse(
            "sq",
            NiagaraFixtures.xmlParser(
                """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:viewportWidth="24"
                android:viewportHeight="24"><path android:strokeColor="?AnyconsStrokeColor" android:strokeWidth="0.01"
                android:fillColor="?AnyconsStrokeColor" android:pathData="M0,0L24,0L24,24L0,24Z" /></vector>""",
            ),
        )
        fun bbox(style: AnyconStyle, n: Int): IntArray {
            val b = AnyconComposer.render(
                sq, AnyconSettings(style, launcherShadow = false), { null }, n, Random(1),
            )!!
            val stroke = when (style) {
                is Material -> Palettes.material(style.shade, AnyconSettings.DEFAULT_SEED_COLOR).stroke
                else -> Color.WHITE
            }
            var minX = n; var minY = n; var maxX = -1; var maxY = -1
            for (y in 0 until n) for (x in 0 until n) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) > 128 && close(p or 0xFF000000.toInt(), stroke, 8)) {
                    minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                }
            }
            return intArrayOf(minX, minY, maxX + 1, maxY + 1)
        }
        assertEquals(listOf(19, 19, 81, 81), bbox(Material(), 100).toList()) // (int)(50 ± 31)
        assertEquals(listOf(16, 16, 80, 80), bbox(Outline(), 96).toList()) // inset round(96/6)
    }

    @Test
    fun `dump renders for visual review`() {
        NiagaraFixtures.requireDecoded()
        val outDir = File(System.getProperty("anycons.buildDir") ?: "build", "verifier-i2").apply { mkdirs() }
        val src = NiagaraFixtures.source
        val combos = listOf(
            "act_flash_small_and_narrow", "act_pause_circled_48", "act_drag_indicator", "cat_go_game",
            "app_com_mojang_minecraftpe", "act_two_colums",
        )
        val styles = listOf(
            "mat" to Material(), "stk" to Sticker(Sticker.Palette.CORAL), "stl" to AnyconStyle.Stellar,
            "wc" to AnyconStyle.Watercolor(), "mar" to Marble(), "ol" to Outline(Outline.FillType.FILL, Thickness.THIN),
        )
        val n = 256
        val sheet = androidx.core.graphics.createBitmap(styles.size * n, combos.size * n)
        val c = android.graphics.Canvas(sheet).apply { drawColor(0xFF808080.toInt()) }
        combos.forEachIndexed { row, gname ->
            styles.forEachIndexed { col, (_, st) ->
                val b = AnyconRenderer(src, 1).render(gname, AnyconSettings(st), n, gname)
                assertNotNull("$gname $st", b)
                c.drawBitmap(b!!, (col * n).toFloat(), (row * n).toFloat(), null)
            }
        }
        File(outDir, "tricky.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val sizes = listOf(48, 96, 192, 512)
        val st = androidx.core.graphics.createBitmap(sizes.sum(), 512)
        val c2 = android.graphics.Canvas(st)
        var x = 0
        for (s in sizes) {
            val b = AnyconRenderer(src, 1).render("cat_home", AnyconSettings(AnyconStyle.Stellar), s, "home")!!
            c2.drawBitmap(b, x.toFloat(), 0f, null)
            x += s
        }
        File(outDir, "stellar-sizes.png").outputStream().use { st.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
