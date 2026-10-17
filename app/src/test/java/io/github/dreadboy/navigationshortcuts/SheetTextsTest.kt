package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetTextsTest {
    // ------------------------------------------------------------------ E23 toast

    @Test
    fun othersNamesUpToThreeLabelsThenEllipsis() {
        assertNull(SheetTexts.others(emptyList()))
        assertEquals("Doma", SheetTexts.others(listOf("Doma")))
        assertEquals("Doma, Služba, Fitnes", SheetTexts.others(listOf("Doma", "Služba", "Fitnes")))
        assertEquals("Doma, Služba, Fitnes, …", SheetTexts.others(listOf("Doma", "Služba", "Fitnes", "Spar")))
        assertEquals("Doma, …", SheetTexts.others(listOf("Doma", "Služba"), max = 1))
    }

    // ------------------------------------------------------------------ E14 label length

    /** 10 px per character, like a monospaced font. */
    private val mono: (String) -> Float = { it.length * 10f }

    @Test
    fun shortLabelIsShownWhole() {
        assertEquals("Doma", SheetTexts.shownLabel("Doma", 40f, mono))
        assertEquals("Doma", SheetTexts.shownLabel("  Doma ", 40f, mono))
    }

    @Test
    fun longLabelIsCutWithEllipsisWithinTheWidth() {
        val shown = SheetTexts.shownLabel("Stožice Stadium", 80f, mono)
        assertEquals("Stožice…", shown)
        assertTrue(mono(shown) <= 80f)
    }

    @Test
    fun cutDropsTrailingSpaceBeforeTheEllipsis() {
        assertEquals("Ab…", SheetTexts.shownLabel("Ab cdefgh", 40f, mono))
    }

    @Test
    fun tinyWidthStillShowsOneCharacter() {
        assertEquals("S…", SheetTexts.shownLabel("Stadium", 5f, mono))
    }

    @Test
    fun labelWidthFollowsColumns() {
        // 315 dp slot: 1×4 → 78.75 − 4 − 3; more columns → narrower.
        assertEquals(71.75f, SheetTexts.labelWidthDp(4), 0.001f)
        assertTrue(SheetTexts.labelWidthDp(6) < SheetTexts.labelWidthDp(4))
        assertEquals(SheetTexts.labelWidthDp(6), SheetTexts.labelWidthDp(9), 0.001f)
    }

    // ------------------------------------------------------------------ E1 unsaved changes

    @Test
    fun unsavedChangesDetectsEveryEditableField() {
        val a = Entry("A", "a dest", IconRef.niagara("cat_home"), id = "a")
        val b = Entry("B", "b dest", IconRef.niagara("cat_office"), id = "b")
        val original = WidgetConfig(1, 4, listOf(a, b))
        assertFalse("nothing loaded yet", SheetTexts.hasUnsavedChanges(null, original))
        assertFalse(SheetTexts.hasUnsavedChanges(original, original.copy()))
        assertFalse(SheetTexts.hasUnsavedChanges(original, WidgetConfig(1, 4, listOf(a.copy(), b.copy()))))
        val edits = listOf(
            original.copy(rows = 2),
            original.copy(cols = 5),
            original.copy(entries = listOf(b, a)),
            original.copy(entries = listOf(a)),
            original.copy(entries = listOf(a, b.copy(label = "B2"))),
            original.copy(entries = listOf(a, b.copy(destination = "elsewhere"))),
            original.copy(entries = listOf(a, b.copy(icon = IconRef.niagara("cat_food")))),
            original.copy(style = original.style.copy(style = AnyconStyle.Stellar)),
            original.copy(style = original.style.copy(seedColor = 0xFF00897B.toInt())),
            original.copy(iconSize = IconSize.LARGE),
        )
        for (edited in edits) assertTrue("$edited", SheetTexts.hasUnsavedChanges(original, edited))
    }

    @Test
    fun deleteThenUndoIsNoChange() {
        val a = Entry("A", "a dest", IconRef.niagara("cat_home"), id = "a")
        val b = Entry("B", "b dest", IconRef.niagara("cat_office"), id = "b")
        val original = WidgetConfig(1, 4, listOf(a, b))
        val entries = original.entries.toMutableList()
        val removedAt = entries.indexOfFirst { it.id == "a" }
        val removed = entries.removeAt(removedAt)
        assertTrue(SheetTexts.hasUnsavedChanges(original, original.copy(entries = entries.toList())))
        entries.add(removedAt, removed)
        assertFalse(SheetTexts.hasUnsavedChanges(original, original.copy(entries = entries.toList())))
    }
}
