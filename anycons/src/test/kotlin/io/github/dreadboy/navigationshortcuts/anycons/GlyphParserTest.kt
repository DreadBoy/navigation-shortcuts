package io.github.dreadboy.navigationshortcuts.anycons

import io.github.dreadboy.navigationshortcuts.anycons.glyph.AttrRoleResolver
import io.github.dreadboy.navigationshortcuts.anycons.glyph.FillType
import io.github.dreadboy.navigationshortcuts.anycons.glyph.GlyphParser
import io.github.dreadboy.navigationshortcuts.anycons.glyph.PaintRole
import io.github.dreadboy.navigationshortcuts.anycons.glyph.StrokeCap
import io.github.dreadboy.navigationshortcuts.anycons.glyph.StrokeJoin
import io.github.dreadboy.navigationshortcuts.anycons.glyph.ThemedFloat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlyphParserTest {

    @Test
    fun `cat_home has bg, fg and a round stroke`() {
        val g = NiagaraFixtures.parseGlyph("cat_home")
        assertEquals(24f, g.viewportWidth)
        assertEquals(24f, g.widthDp)
        assertEquals(3, g.paths.size)
        assertEquals(listOf(PaintRole.BG, PaintRole.FG, PaintRole.NONE), g.paths.map { it.fillRole })
        assertEquals(listOf(PaintRole.NONE, PaintRole.NONE, PaintRole.STROKE), g.paths.map { it.strokeRole })
        val stroke = g.paths[2]
        assertEquals(ThemedFloat.Themed, stroke.strokeWidth)
        assertEquals(StrokeCap.ROUND, stroke.strokeCap)
        assertEquals(StrokeJoin.ROUND, stroke.strokeJoin)
        assertEquals(FillType.NON_ZERO, stroke.fillType)
        assertEquals(4f, stroke.strokeMiterLimit)
        val group = stroke.groups.single()
        assertEquals(12f, group.pivotX)
        assertEquals(12f, group.pivotY)
        assertEquals(ThemedFloat.Themed, group.scaleX)
    }

    @Test
    fun `non-24 viewports are read per glyph`() {
        val flash = NiagaraFixtures.parseGlyph("act_flash_small_and_narrow")
        assertEquals(16f, flash.viewportWidth)
        assertEquals(24f, flash.viewportHeight)
        assertEquals(16f, flash.widthDp)
        assertEquals(12f, flash.paths.single().groups.single().pivotX) // pivot stays 12 even here

        val pause = NiagaraFixtures.parseGlyph("act_pause_circled_48")
        assertEquals(48f, pause.viewportWidth)
        assertEquals(48f, pause.viewportHeight)
        assertTrue(pause.paths.all { p -> p.groups.all { it.pivotX == 12f && it.pivotY == 12f } })
    }

    @Test
    fun `missing caps and joins fall back to butt and miter`() {
        val circle = NiagaraFixtures.parseGlyph("act_circle_small")
        assertTrue(circle.paths.any { it.strokeRole == PaintRole.STROKE && it.strokeCap == StrokeCap.BUTT })

        val drag = NiagaraFixtures.parseGlyph("act_drag_indicator")
        assertTrue(drag.paths.any { it.strokeCap == StrokeCap.BUTT && it.strokeJoin == StrokeJoin.MITER })

        val star = NiagaraFixtures.parseGlyph("act_star_small")
        val starStroke = star.paths.first { it.strokeRole == PaintRole.STROKE }
        assertEquals(StrokeCap.BUTT, starStroke.strokeCap) // only the join is declared
        assertEquals(StrokeJoin.ROUND, starStroke.strokeJoin)
    }

    @Test
    fun `stroke-coloured fills are classified as stroke, not by position`() {
        val dots = NiagaraFixtures.parseGlyph("act_dots_three_vertical")
        val only = dots.paths.single()
        assertEquals(PaintRole.STROKE, only.fillRole)
        assertEquals(PaintRole.STROKE, only.strokeRole)

        val go = NiagaraFixtures.parseGlyph("cat_go_game")
        assertEquals(4, go.paths.size)
        assertEquals(listOf(PaintRole.BG, PaintRole.FG, PaintRole.NONE, PaintRole.STROKE), go.paths.map { it.fillRole })
        assertEquals(PaintRole.STROKE, go.paths[3].strokeRole)

        val add = NiagaraFixtures.parseGlyph("act_add") // stroke-only glyph
        assertTrue(add.paths.all { it.fillRole == PaintRole.NONE && it.strokeRole == PaintRole.STROKE })
    }

    @Test
    fun `weather glyph colours are not anycons roles`() {
        val w = NiagaraFixtures.parseGlyph("act_weather_clear")
        assertTrue(w.paths.none { it.fillRole in setOf(PaintRole.BG, PaintRole.FG) })
    }

    @Test
    fun `runtime binary-xml value forms are resolved by attr id`() {
        val bg = 0x7f040000
        val fg = 0x7f040001
        val stroke = 0x7f040003
        val resolver = AttrRoleResolver { mapOf(bg to PaintRole.BG, fg to PaintRole.FG, stroke to PaintRole.STROKE)[it] }
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="16.0dip" android:height="24.0dip" android:viewportWidth="16.0" android:viewportHeight="24.0">
  <group android:pivotX="12.0" android:pivotY="12.0" android:scaleX="?2130968578" android:scaleY="?2130968578">
    <path android:fillColor="?$bg" android:pathData="M1,1L15,1L15,23Z"/>
    <path android:fillColor="?$fg" android:pathData="M2,2L14,2L14,22Z" android:fillType="1"/>
    <path android:fillColor="?$stroke" android:strokeColor="?$stroke" android:strokeWidth="?2130968580"
        android:strokeLineCap="1" android:strokeLineJoin="2" android:pathData="M3,3L13,3"/>
    <path android:fillColor="#00000000" android:strokeColor="?2130968999" android:strokeWidth="0.0" android:pathData="M3,3L13,3"/>
  </group>
</vector>"""
        val g = GlyphParser(resolver).parse("runtime", NiagaraFixtures.xmlParser(xml))
        assertEquals(16f, g.widthDp)
        assertEquals(24f, g.heightDp)
        assertEquals(listOf(PaintRole.BG, PaintRole.FG, PaintRole.STROKE, PaintRole.OTHER), g.paths.map { it.fillRole })
        assertEquals(PaintRole.STROKE, g.paths[2].strokeRole)
        assertEquals(PaintRole.OTHER, g.paths[3].strokeRole) // unknown attr id
        assertEquals(FillType.EVEN_ODD, g.paths[1].fillType)
        assertEquals(StrokeCap.ROUND, g.paths[2].strokeCap)
        assertEquals(StrokeJoin.BEVEL, g.paths[2].strokeJoin)
        assertEquals(ThemedFloat.Themed, g.paths[2].strokeWidth)
        assertEquals(ThemedFloat.Literal(0f), g.paths[3].strokeWidth)
        assertEquals(ThemedFloat.Themed, g.paths[0].groups.single().scaleX)
    }

    @Test
    fun `attr name forms`() {
        val p = GlyphParser()
        assertEquals(PaintRole.BG, p.classify("?AnyconsBgFillColor"))
        assertEquals(PaintRole.FG, p.classify("?attr/AnyconsFgFillColor"))
        assertEquals(PaintRole.STROKE, p.classify("?bitpit.launcher:attr/AnyconsStrokeColor"))
        assertEquals(PaintRole.OTHER, p.classify("?weatherCold"))
        assertEquals(PaintRole.OTHER, p.classify("#ff000000"))
        assertEquals(PaintRole.OTHER, p.classify("@color/foo"))
        assertEquals(PaintRole.OTHER, p.classify("?2130968576")) // no resolver: unknown id
        assertEquals(PaintRole.NONE, p.classify(null))
    }

    @Test(expected = GlyphParser.GlyphParseException::class)
    fun `non-vector xml is rejected`() {
        GlyphParser().parse("bad", NiagaraFixtures.xmlParser("<shape/>"))
    }
}
