package io.github.dreadboy.navigationshortcuts.anycons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Dots
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Marble
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Material
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Outline
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle.Sticker
import io.github.dreadboy.navigationshortcuts.anycons.settings.AppOrThemePalette
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders destination glyphs × all packs into `anycons/build/contact-sheet/anycons-sheet.png` for visual review.
 * The sheet contains Niagara art, so it stays in the (git-ignored) build dir. Copy target: env `ANYCONS_SHEET_COPY`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactSheetTest {

    @Test
    fun `render contact sheet`() {
        NiagaraFixtures.requireDecoded()
        val renderer = AnyconRenderer(NiagaraFixtures.source, maxCacheBytes = 1)
        val cell = 192
        val pad = 16
        val labelW = 300
        val header = 48
        val cols = GLYPHS.size
        val sheet = createBitmap(labelW + cols * (cell + pad) + pad, header + ROWS.size * (cell + pad) + pad)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.BLACK }
        val tile = Paint()

        GLYPHS.forEachIndexed { c, name ->
            canvas.drawText(name.removePrefix("cat_"), (labelW + c * (cell + pad) + pad).toFloat(), 32f, text)
        }
        ROWS.forEachIndexed { r, (label, style) ->
            val top = header + r * (cell + pad) + pad
            canvas.drawText(label, 12f, top + cell / 2f + 8f, text)
            tile.color = if (needsDarkTile(style)) 0xFF2B2B2B.toInt() else 0xFFE4E4E4.toInt()
            GLYPHS.forEachIndexed { c, name ->
                val left = labelW + c * (cell + pad) + pad
                canvas.drawRect(left.toFloat(), top.toFloat(), (left + cell).toFloat(), (top + cell).toFloat(), tile)
                val icon = renderer.render(name, AnyconSettings(style, seedColor = SEED), cell, seedKey = name)
                assertNotNull("$label / $name", icon)
                canvas.drawBitmap(icon!!, left.toFloat(), top.toFloat(), null)
            }
        }

        val out = File(System.getProperty("anycons.buildDir") ?: "build", "contact-sheet/anycons-sheet.png")
        out.parentFile!!.mkdirs()
        out.outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(out.length() > 10_000)
        System.getenv("ANYCONS_SHEET_COPY")?.let { target -> out.copyTo(File(target), overwrite = true) }
        println("Contact sheet: ${out.absolutePath}")
    }

    private fun needsDarkTile(style: AnyconStyle) = when (style) {
        is Outline -> style.textMode == Outline.TextMode.LIGHT_TEXT
        is AnyconStyle.OutlineChroma -> true
        else -> false
    }

    companion object {
        const val SEED = 0xFF109DFF.toInt()

        val GLYPHS = listOf(
            "cat_home", "cat_office", "cat_fitness", "cat_education",
            "cat_shopping", "cat_food", "cat_health", "cat_air_travel",
        )

        val ROWS: List<Pair<String, AnyconStyle>> = listOf(
            "Material Light" to Material(Material.Shade.LIGHT),
            "Material Vivid" to Material(Material.Shade.VIVID),
            "Material Dark" to Material(Material.Shade.DARK),
            "Material Vivid thin" to Material(Material.Shade.VIVID, Thickness.THIN),
            "Sticker Coral" to Sticker(Sticker.Palette.CORAL),
            "Sticker Opal" to Sticker(Sticker.Palette.OPAL),
            "Sticker Theme" to Sticker(Sticker.Palette.THEME_COLORS),
            "Sticker App" to Sticker(Sticker.Palette.APP_COLORS),
            "Dots dot" to Dots(Dots.Shape.DOT),
            "Dots diamond" to Dots(Dots.Shape.DIAMOND),
            "Outline light" to Outline(Outline.FillType.NO_FILL),
            "Outline fill light" to Outline(Outline.FillType.FILL),
            "Outline dark" to Outline(Outline.FillType.NO_FILL, textMode = Outline.TextMode.DARK_TEXT),
            "Outline fill dark" to Outline(Outline.FillType.FILL, textMode = Outline.TextMode.DARK_TEXT),
            "Outline Chroma" to AnyconStyle.OutlineChroma(),
            "Watercolor" to AnyconStyle.Watercolor(),
            "Marble White" to Marble(Marble.Palette.WHITE),
            "Marble Black" to Marble(Marble.Palette.BLACK),
            "Marble Theme" to Marble(Marble.Palette.THEME_COLORS),
            "Bright" to AnyconStyle.Bright(),
            "Bright App" to AnyconStyle.Bright(AppOrThemePalette.APP_COLORS),
            "Stellar" to AnyconStyle.Stellar,
        )
    }
}
