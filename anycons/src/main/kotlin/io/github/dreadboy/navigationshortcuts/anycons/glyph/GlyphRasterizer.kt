package io.github.dreadboy.navigationshortcuts.anycons.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

/**
 * Rasterises a [Glyph] as one layer of a given [MaskStyle], the way a themed VectorDrawable would draw it:
 * each path is filled, then stroked, in document order, with source-over compositing; group transforms (pivot,
 * scale) are applied literally — Niagara's glyphs always pivot at (12, 12), even for non-24 viewports.
 *
 * The viewport is fitted into the destination rectangle with a uniform scale and centred, so non-square glyphs
 * (e.g. 16×24) keep their proportions inside a square icon.
 */
object GlyphRasterizer {

    /** Draws the glyph's mask (role alphas from [style]) in black onto [canvas] inside [dst]. */
    fun drawMask(canvas: Canvas, glyph: Glyph, style: MaskStyle, dst: RectF) {
        val scale = minOf(dst.width() / glyph.viewportWidth, dst.height() / glyph.viewportHeight)
        if (scale <= 0f || scale.isNaN()) return
        val offsetX = dst.left + (dst.width() - glyph.viewportWidth * scale) / 2f
        val offsetY = dst.top + (dst.height() - glyph.viewportHeight * scale) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
        for (gp in glyph.paths) {
            val fillAlpha = style.alphaOf(gp.fillRole)
            val strokeWidth = when (val w = gp.strokeWidth) {
                ThemedFloat.Themed -> style.strokeWidth
                is ThemedFloat.Literal -> w.value
            }
            val strokeAlpha = if (strokeWidth > 0f) style.alphaOf(gp.strokeRole) else 0
            if (fillAlpha == 0 && strokeAlpha == 0) continue
            val path = toPath(gp.pathData) ?: continue

            groupMatrix(gp.groups, style, matrix)
            canvas.withTranslation(offsetX, offsetY) {
                scale(scale, scale)
                concat(matrix)
                drawPathLayers(this, gp, path, paint, fillAlpha, strokeAlpha, strokeWidth)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun drawPathLayers(
        canvas: Canvas,
        gp: GlyphPath,
        path: Path,
        paint: Paint,
        fillAlpha: Int,
        strokeAlpha: Int,
        strokeWidth: Float,
    ) {
        if (fillAlpha > 0) {
            path.fillType = when (gp.fillType) {
                FillType.NON_ZERO -> Path.FillType.WINDING
                FillType.EVEN_ODD -> Path.FillType.EVEN_ODD
            }
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(fillAlpha, 0, 0, 0)
            canvas.drawPath(path, paint)
        }
        if (strokeAlpha > 0) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(strokeAlpha, 0, 0, 0)
            paint.strokeWidth = strokeWidth
            paint.strokeMiter = gp.strokeMiterLimit
            paint.strokeCap = when (gp.strokeCap) {
                StrokeCap.BUTT -> Paint.Cap.BUTT
                StrokeCap.ROUND -> Paint.Cap.ROUND
                StrokeCap.SQUARE -> Paint.Cap.SQUARE
            }
            paint.strokeJoin = when (gp.strokeJoin) {
                StrokeJoin.MITER -> Paint.Join.MITER
                StrokeJoin.ROUND -> Paint.Join.ROUND
                StrokeJoin.BEVEL -> Paint.Join.BEVEL
            }
            canvas.drawPath(path, paint)
        }
    }

    /** Renders the black mask into a new [width]×[height] bitmap. */
    fun renderMask(glyph: Glyph, style: MaskStyle, width: Int, height: Int = width): Bitmap {
        val bitmap = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        drawMask(Canvas(bitmap), glyph, style, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        return bitmap
    }

    /**
     * Renders the layer tinted to [tint] with SRC_IN semantics: every pixel becomes [tint], its alpha multiplied by
     * the mask alpha (so per-path alphas such as MixedFill's 0xA0 silhouette survive).
     */
    fun renderTinted(glyph: Glyph, style: MaskStyle, tint: Int, width: Int, height: Int = width): Bitmap {
        val bitmap = renderMask(glyph, style, width, height)
        Canvas(bitmap).drawColor(tint, PorterDuff.Mode.SRC_IN)
        return bitmap
    }

    private fun groupMatrix(groups: List<GroupTransform>, style: MaskStyle, out: Matrix) {
        out.reset()
        val local = Matrix()
        // Outermost first: total = outer * inner, so pre-concat each inner group.
        for (g in groups) {
            val sx = resolveScale(g.scaleX, style)
            val sy = resolveScale(g.scaleY, style)
            local.reset()
            local.postTranslate(-g.pivotX, -g.pivotY)
            local.postScale(sx, sy)
            local.postRotate(g.rotation, 0f, 0f)
            local.postTranslate(g.translateX + g.pivotX, g.translateY + g.pivotY)
            out.preConcat(local)
        }
    }

    private fun resolveScale(value: ThemedFloat, style: MaskStyle): Float = when (value) {
        ThemedFloat.Themed -> style.groupScale
        is ThemedFloat.Literal -> value.value
    }

    private fun toPath(pathData: String): Path? = try {
        PathParser.createPathFromPathData(pathData)
    } catch (_: RuntimeException) {
        null
    }
}
