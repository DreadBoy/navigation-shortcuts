package io.github.dreadboy.navigationshortcuts.anycons.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import io.github.dreadboy.navigationshortcuts.anycons.color.Palettes
import io.github.dreadboy.navigationshortcuts.anycons.glyph.Glyph
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphRasterizer
import io.github.dreadboy.navigationshortcuts.anycons.glyph.MaskStyle
import io.github.dreadboy.navigationshortcuts.anycons.glyph.StrokeWeight
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import io.github.dreadboy.navigationshortcuts.anycons.settings.IconShape
import io.github.dreadboy.navigationshortcuts.anycons.settings.Thickness
import io.github.dreadboy.navigationshortcuts.anycons.source.Texture
import java.util.Random
import kotlin.math.roundToInt

/**
 * Renders one Anycon in any of the nine packs: a re-implementation of Niagara 1.16.33's composers from the
 * documented recipes (draw order, scales, transfer modes and the seeded texture placement).
 *
 * Inputs: the [Glyph] (ignored by Dots), [AnyconSettings], a texture lookup, the icon size and a per-destination
 * [Random]. For APP_COLORS palettes, `destinationColor` stands in for the app colour.
 *
 * Returns null when a needed input is missing (no glyph, or a texture the pack needs); never draws a half icon.
 */
object AnyconComposer {

    fun render(
        glyph: Glyph?,
        settings: AnyconSettings,
        textures: (Texture) -> Bitmap?,
        sizePx: Int,
        random: Random,
        destinationColor: Int? = null,
    ): Bitmap? {
        require(sizePx > 0) { "sizePx must be positive" }
        val style = settings.style
        if (style.usesGlyph && glyph == null) return null
        val seed = settings.seedColor
        val icon = when (style) {
            is AnyconStyle.Material -> material(glyph!!, style, settings.shape, seed, sizePx)
            is AnyconStyle.Bright -> bright(glyph!!, style, settings.shape, seed, destinationColor, sizePx)
            is AnyconStyle.Sticker -> sticker(glyph!!, style, seed, destinationColor, textures, sizePx)
            is AnyconStyle.Dots -> dots(style, seed, destinationColor, sizePx)
            is AnyconStyle.Outline -> outline(glyph!!, style, seed, sizePx)
            is AnyconStyle.OutlineChroma -> outlineChroma(glyph!!, style, seed, destinationColor, sizePx)
            is AnyconStyle.Watercolor -> watercolor(glyph!!, style, seed, destinationColor, textures, sizePx, random)
            is AnyconStyle.Marble -> marble(glyph!!, style, settings.shape, seed, textures, sizePx, random)
            AnyconStyle.Stellar -> stellar(glyph!!, settings.shape, seed, sizePx, random)
        } ?: return null
        if (!settings.effectiveLauncherShadow) return icon
        return applyLauncherShadow(icon).also { icon.recycle() }
    }

    // --- Material / Bright: shape background + glyph at 0.62 -------------------------------------------------

    private fun material(glyph: Glyph, style: AnyconStyle.Material, shape: IconShape, seed: Int, n: Int): Bitmap {
        val pal = Palettes.material(style.shade, seed)
        val weight = when (style.shade) {
            AnyconStyle.Material.Shade.DARK -> if (style.thickness == Thickness.THIN) StrokeWeight.W12 else StrokeWeight.W16
            else -> if (style.thickness == Thickness.THIN) StrokeWeight.W11 else StrokeWeight.W15
        }
        return shapeWithGlyph(glyph, shape, n, pal.background, pal.fill.takeIf { it != pal.background }, pal.stroke, weight)
    }

    private fun bright(
        glyph: Glyph,
        style: AnyconStyle.Bright,
        shape: IconShape,
        seed: Int,
        destinationColor: Int?,
        n: Int,
    ): Bitmap {
        val pal = Palettes.bright(style.palette, seed, destinationColor)
        return shapeWithGlyph(glyph, shape, n, pal.background, pal.fill, pal.stroke, StrokeWeight.W17)
    }

    private fun shapeWithGlyph(
        glyph: Glyph,
        shape: IconShape,
        n: Int,
        background: Int,
        fill: Int?,
        stroke: Int,
        weight: StrokeWeight,
    ): Bitmap {
        val ic = IconCanvas(n)
        Shapes.fill(ic.canvas, shape, n, background)
        val glyphRect = centeredFractionRect(n, 0.62f)
        if (fill != null) drawLayer(ic.canvas, glyph, MaskStyle.MIXED_FILL, fill, glyphRect)
        drawLayer(ic.canvas, glyph, MaskStyle.stroke(weight), stroke, glyphRect)
        return ic.bitmap
    }

    // --- Sticker: die-cut stamp + 0.7 front with glyph-masked fill ---------------------------------------------

    private fun sticker(
        glyph: Glyph,
        style: AnyconStyle.Sticker,
        seed: Int,
        destinationColor: Int?,
        textures: (Texture) -> Bitmap?,
        n: Int,
    ): Bitmap? {
        val pal = Palettes.sticker(style.palette, seed, destinationColor)
        val texture = when (style.palette) {
            AnyconStyle.Sticker.Palette.CORAL -> textures(Texture.CORAL_GRADIENT) ?: return null
            AnyconStyle.Sticker.Palette.OPAL -> textures(Texture.OPAL_GRADIENT) ?: return null
            else -> null
        }
        val maskStyle =
            if (style.palette == AnyconStyle.Sticker.Palette.APP_COLORS) MaskStyle.SUBTLE_MIXED_FILL else MaskStyle.MIXED_FILL

        // Front fill: colour or texture, keeping only the glyph's fill alpha.
        val front = IconCanvas(n)
        if (texture != null) front.canvas.drawBitmap(texture, null, front.inner, front.paint) else front.canvas.drawColor(pal.fill)
        val mask = GlyphRasterizer.renderMask(glyph, maskStyle, n)
        front.drawMasked(mask, null, PorterDuff.Mode.DST_ATOP, expanded = false)
        mask.recycle()

        val ic = IconCanvas(n)
        drawLayer(ic.canvas, glyph, MaskStyle.STAMP_STROKE, pal.background, ic.inner)
        val frontRect = insetRect(n, 0.7f)
        ic.canvas.drawBitmap(front.bitmap, null, frontRect, ic.paint)
        front.bitmap.recycle()
        drawLayer(ic.canvas, glyph, MaskStyle.stroke(StrokeWeight.W15), pal.stroke, frontRect)
        return ic.bitmap
    }

    // --- Dots: a dot or a diamond, no glyph ---------------------------------------------------------------------

    private fun dots(style: AnyconStyle.Dots, seed: Int, destinationColor: Int?, n: Int): Bitmap {
        val ic = IconCanvas(n)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palettes.dots(style.palette, seed, destinationColor) }
        val cx = ic.inner.exactCenterX()
        val cy = ic.inner.exactCenterY()
        when (style.shape) {
            AnyconStyle.Dots.Shape.DOT -> ic.canvas.drawCircle(cx, cy, 0.125f * n, paint)
            AnyconStyle.Dots.Shape.DIAMOND -> {
                val s = n * 0.25f
                val r = RectF(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
                ic.canvas.withRotation(45f, cx, cy) { drawRoundRect(r, 0.16f * s, 0.16f * s, paint) }
            }
        }
        return ic.bitmap
    }

    // --- Outline / Outline Chroma: glyph at 2/3, no background --------------------------------------------------

    private fun outline(glyph: Glyph, style: AnyconStyle.Outline, seed: Int, n: Int): Bitmap {
        val pal = Palettes.outline(style.textMode, seed)
        val weight = if (style.thickness == Thickness.THIN) StrokeWeight.W12 else StrokeWeight.W15
        val ic = IconCanvas(n)
        val r = insetRect(n, 2f / 3f)
        if (style.fillType == AnyconStyle.Outline.FillType.FILL) drawLayer(ic.canvas, glyph, MaskStyle.MIXED_FILL, pal.fill, r)
        drawLayer(ic.canvas, glyph, MaskStyle.stroke(weight), pal.stroke, r)
        return ic.bitmap
    }

    private fun outlineChroma(
        glyph: Glyph,
        style: AnyconStyle.OutlineChroma,
        seed: Int,
        destinationColor: Int?,
        n: Int,
    ): Bitmap {
        val stroke = Palettes.outlineChroma(style.palette, seed, destinationColor)
        val weight = if (style.thickness == Thickness.THIN) StrokeWeight.W13 else StrokeWeight.W15
        val ic = IconCanvas(n)
        drawLayer(ic.canvas, glyph, MaskStyle.stroke(weight), stroke, insetRect(n, 2f / 3f))
        return ic.bitmap
    }

    // --- Watercolor: OVERLAY-tinted wash, ragged alpha blob, glowing white stroke at 0.558 ----------------------

    @Suppress("LongParameterList")
    private fun watercolor(
        glyph: Glyph,
        style: AnyconStyle.Watercolor,
        seed: Int,
        destinationColor: Int?,
        textures: (Texture) -> Bitmap?,
        n: Int,
        random: Random,
    ): Bitmap? {
        val overlay = textures(Texture.WATERCOLOR_OVERLAY) ?: return null
        val transparency = textures(Texture.WATERCOLOR_TRANSPARENCY) ?: return null
        val pal = Palettes.watercolor(style.palette, seed, destinationColor)
        val ic = IconCanvas(n)

        val washTile = IconCanvas.pickTile(random, overlay, 4, 2, continuous = false)
        ic.withRandomFlipRotate(random, freeRotation = false) {
            ic.drawOverlayTinted(overlay, washTile, pal.background, expanded = false)
        }
        val blobTile = IconCanvas.pickTile(random, transparency, 4, 2, continuous = false)
        ic.withRandomFlipRotate(random, freeRotation = true) {
            ic.drawMasked(transparency, blobTile, PorterDuff.Mode.DST_ATOP, expanded = true)
        }

        val g = (n * 0.558f).roundToInt().coerceAtLeast(1)
        drawGlowingStroke(ic.canvas, glyph, n, g, pal.stroke, pal.fill, glowAlpha = 179)
        return ic.bitmap
    }

    // --- Marble: OVERLAY-tinted marble, 50% silhouette + glowing stroke at 0.62, shape-clipped ------------------

    @Suppress("LongParameterList")
    private fun marble(
        glyph: Glyph,
        style: AnyconStyle.Marble,
        shape: IconShape,
        seed: Int,
        textures: (Texture) -> Bitmap?,
        n: Int,
        random: Random,
    ): Bitmap? {
        val marble = textures(Texture.MARBLE_OVERLAY) ?: return null
        val pal = Palettes.marble(style.palette, seed)
        val ic = IconCanvas(n)

        val tile = IconCanvas.pickTile(random, marble, 4, 4, continuous = false)
        ic.withRandomFlipRotate(random, freeRotation = true) {
            ic.drawOverlayTinted(marble, tile, pal.background, expanded = true)
        }

        val g = (n * 0.62f).roundToInt().coerceAtLeast(1)
        val offset = (n - g) / 2f
        val silhouette = GlyphRasterizer.renderTinted(glyph, MaskStyle.COMPLETE_FILL, (pal.fill and 0xFFFFFF) or 0x7F000000, g)
        ic.canvas.drawBitmap(silhouette, offset, offset, ic.paint)
        silhouette.recycle()
        drawGlowingStroke(ic.canvas, glyph, n, g, pal.stroke, pal.fill, glowAlpha = 255)

        return Shapes.clip(ic.bitmap, shape).also { ic.bitmap.recycle() }
    }

    // --- Stellar: procedural starfield + stroke at 0.62, shape-clipped ------------------------------------------

    private fun stellar(glyph: Glyph, shape: IconShape, seed: Int, n: Int, random: Random): Bitmap {
        val pal = Palettes.stellar(seed)
        val stars = StarTexture.forSize(n)
        val ic = IconCanvas(n)
        val tile = IconCanvas.pickTile(random, stars, 3, 3, continuous = true)
        ic.canvas.drawColor(pal.background)
        ic.withRandomFlipRotate(random, freeRotation = true) {
            ic.canvas.drawBitmap(stars, tile, ic.outer, ic.paint)
        }
        drawLayer(ic.canvas, glyph, MaskStyle.stroke(StrokeWeight.W17), pal.stroke, insetRect(n, 0.62f))
        return Shapes.clip(ic.bitmap, shape).also { ic.bitmap.recycle() }
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    /** Draws one tinted glyph layer into [rect] (rasterised at the rect's size, as a VectorDrawable would be). */
    private fun drawLayer(canvas: Canvas, glyph: Glyph, style: MaskStyle, tint: Int, rect: Rect) {
        if (rect.width() <= 0 || rect.height() <= 0) return
        val layer = GlyphRasterizer.renderTinted(glyph, style, tint, rect.width(), rect.height())
        canvas.drawBitmap(layer, rect.left.toFloat(), rect.top.toFloat(), null)
        layer.recycle()
    }

    /** Stroke W17 rasterised at g×g, centred in the N canvas, over a 0.2·N glow of itself in [glowColor]. */
    @Suppress("LongParameterList")
    private fun drawGlowingStroke(canvas: Canvas, glyph: Glyph, n: Int, g: Int, stroke: Int, glowColor: Int, glowAlpha: Int) {
        val offset = (n - g) / 2f
        val strokeBitmap = GlyphRasterizer.renderTinted(glyph, MaskStyle.stroke(StrokeWeight.W17), stroke, g)
        canvas.withTranslation(offset, offset) { Glow(n, small = false).draw(strokeBitmap, this, glowAlpha, 0, glowColor) }
        strokeBitmap.recycle()
    }
}
