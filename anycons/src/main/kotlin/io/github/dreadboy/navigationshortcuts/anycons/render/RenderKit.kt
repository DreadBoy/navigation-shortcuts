package io.github.dreadboy.navigationshortcuts.anycons.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import io.github.dreadboy.navigationshortcuts.anycons.settings.IconShape
import java.util.Random
import kotlin.math.roundToInt

/**
 * An N×N drawing surface with the helpers every pack composer shares: bilinear bitmap filtering, the N×N `inner`
 * and 1.5N `outer` rectangles, the seeded tile picker and flip/rotate, and texture drawing modes.
 */
internal class IconCanvas(val size: Int) {
    val bitmap: Bitmap = createBitmap(size, size)
    val canvas: Canvas = Canvas(bitmap).apply {
        drawFilter = PaintFlagsDrawFilter(Paint.DITHER_FLAG, Paint.FILTER_BITMAP_FLAG)
    }
    val paint = Paint()

    val inner = Rect(0, 0, size, size)
    val outer: Rect = (size * 0.25f).toInt().let { q -> Rect(-q, -q, size + q, size + q) }

    /** Draws [texture] (or its [src] tile) tinted by an OVERLAY colour filter of [color]. */
    fun drawOverlayTinted(texture: Bitmap, src: Rect?, color: Int, expanded: Boolean) {
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.OVERLAY)
        canvas.drawBitmap(texture, src, if (expanded) outer else inner, paint)
        paint.colorFilter = null
    }

    /** Draws [mask] with a destination-keeping transfer mode (DST_ATOP or DST_IN). */
    fun drawMasked(mask: Bitmap, src: Rect?, mode: PorterDuff.Mode, expanded: Boolean) {
        paint.xfermode = PorterDuffXfermode(mode)
        canvas.drawBitmap(mask, src, if (expanded) outer else inner, paint)
        paint.xfermode = null
    }

    /**
     * Runs [block] with a random flip (x, then y) and rotation about the centre. Random call order: flipX, flipY,
     * then rotation (`nextFloat() * 360` when [freeRotation], else `nextInt(4) * 90`).
     */
    inline fun withRandomFlipRotate(random: Random, freeRotation: Boolean, block: () -> Unit) {
        val cx = inner.exactCenterX()
        val cy = inner.exactCenterY()
        val saved = canvas.save()
        val fx = if (random.nextBoolean()) -1f else 1f
        val fy = if (random.nextBoolean()) -1f else 1f
        if (fx == -1f || fy == -1f) canvas.scale(fx, fy, cx, cy)
        val degrees = if (freeRotation) random.nextFloat() * 360f else random.nextInt(4) * 90f
        canvas.rotate(degrees, cx, cy)
        try {
            block()
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    companion object {
        /**
         * Picks a tile of a [cols]×[rows] texture atlas. Discrete: `nextInt(cols)`, `nextInt(rows)`. Continuous: a
         * tile-sized window at `nextDouble() * (cols - 1)`, `nextDouble() * (rows - 1)` tiles. Returns null (whole
         * bitmap) for a 1×1 atlas.
         */
        fun pickTile(random: Random, bitmap: Bitmap, cols: Int, rows: Int, continuous: Boolean): Rect? {
            val tileW = bitmap.width / cols
            val tileH = bitmap.height / rows
            if (!continuous) {
                val col = random.nextInt(cols)
                val row = random.nextInt(rows)
                if (cols == 1 && rows == 1) return null
                return Rect(col * tileW, row * tileH, col * tileW + tileW, row * tileH + tileH)
            }
            val x = random.nextDouble() * (cols - 1)
            val y = random.nextDouble() * (rows - 1)
            if (cols == 1 && rows == 1) return null
            val left = (x * tileW).toInt()
            val top = (y * tileH).toInt()
            return Rect(left, top, left + tileW, top + tileH)
        }
    }
}

/**
 * Soft glow / drop shadow: the blurred alpha of a bitmap drawn in a colour under the bitmap itself.
 * Blur radius is `0.2·N`, or `N/96` for the small (launcher shadow) variant.
 */
internal class Glow(private val size: Int, small: Boolean) {
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        maskFilter = BlurMaskFilter(size * if (small) 1f / 96f else 0.2f, BlurMaskFilter.Blur.NORMAL)
    }
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Draws the blurred alpha of [bitmap] in [color] with alpha [alpha1] at its natural offset, again with alpha
     * [alpha2] shifted down by N/48, and finally [bitmap] itself at (0, 0).
     */
    fun draw(bitmap: Bitmap, canvas: Canvas, alpha1: Int, alpha2: Int, color: Int) {
        val offset = IntArray(2)
        val blurred = bitmap.extractAlpha(blurPaint, offset)
        drawPaint.color = color
        if (alpha1 > 0) {
            drawPaint.alpha = alpha1
            canvas.drawBitmap(blurred, offset[0].toFloat(), offset[1].toFloat(), drawPaint)
        }
        if (alpha2 > 0) {
            drawPaint.alpha = alpha2
            canvas.drawBitmap(blurred, offset[0].toFloat(), offset[1] + size / 48f, drawPaint)
        }
        drawPaint.color = Color.BLACK
        drawPaint.alpha = 255
        canvas.drawBitmap(bitmap, 0f, 0f, drawPaint)
        blurred.recycle()
    }
}

internal object Shapes {
    private val cache = HashMap<IconShape, Path>()

    private fun basePath(shape: IconShape): Path = synchronized(cache) {
        cache.getOrPut(shape) { PathParser.createPathFromPathData(shape.pathData100) }
    }

    /** The shape path scaled from its 100×100 box to [bounds]. */
    fun path(shape: IconShape, bounds: Rect): Path {
        val m = Matrix()
        m.setScale(bounds.width() / 100f, bounds.height() / 100f)
        m.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
        val out = Path()
        basePath(shape).transform(m, out)
        return out
    }

    /** Fills [shape] with [color] across the [size]×[size] canvas. */
    fun fill(canvas: Canvas, shape: IconShape, size: Int, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.color = color }
        canvas.drawPath(path(shape, Rect(0, 0, size, size)), paint)
    }

    /**
     * Composites [content] over black and clips it to [shape]: the shape path is filled with a CLAMP bitmap shader
     * of the composite, so the edge is anti-aliased.
     */
    fun clip(content: Bitmap, shape: IconShape): Bitmap {
        val size = content.width
        val composite = createBitmap(size, size)
        Canvas(composite).apply {
            drawColor(Color.BLACK)
            drawBitmap(content, 0f, 0f, null)
        }
        val out = createBitmap(size, size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(composite, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawPath(path(shape, Rect(0, 0, size, size)), paint)
        composite.recycle()
        return out
    }
}

/** Niagara's launcher drop shadow: blur N/96, black α30 at (0,0) and α61 at (0, N/48), then the icon. */
internal fun applyLauncherShadow(icon: Bitmap): Bitmap {
    val out = createBitmap(icon.width, icon.height)
    Glow(icon.width, small = true).draw(icon, Canvas(out), 30, 61, Color.BLACK)
    return out
}

/** Bounds of a glyph drawn at [fraction] of [size], centred, truncated like Niagara's shape-background drawable. */
internal fun centeredFractionRect(size: Int, fraction: Float): Rect {
    val half = fraction / 2f * size
    val c = size / 2f
    return Rect((c - half).toInt(), (c - half).toInt(), (c + half).toInt(), (c + half).toInt())
}

/** Bounds after insetting each side by `round((1 - fraction) / 2 · size)` (Niagara's inset drawable). */
internal fun insetRect(size: Int, fraction: Float): Rect {
    val inset = ((1f - fraction) / 2f * size).roundToInt()
    return Rect(inset, inset, size - inset, size - inset)
}
