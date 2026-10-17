package io.github.dreadboy.navigationshortcuts

import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconSettings
import io.github.dreadboy.navigationshortcuts.anycons.settings.AnyconStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreStateTest {
    private fun entry(label: String) = Entry(label, "$label dest", IconRef.builtin("pin"), id = "id-$label")

    @Test
    fun newWidgetGetsEmptyDefaultGrid() {
        val s = StoreState().ensureConfig(7)
        assertEquals(WidgetConfig(1, 4, emptyList()), s.configs[7])
    }

    @Test
    fun ensureConfigKeepsExisting() {
        val s = StoreState(configs = mapOf(7 to WidgetConfig(2, 2, listOf(entry("a")))))
        assertSame(s, s.ensureConfig(7))
    }

    @Test
    fun newWidgetTakesOverPendingAndGrowsToFit() {
        val pending = (1..5).map { entry("p$it") }
        val s = StoreState(pending = pending).ensureConfig(3)
        assertEquals(pending, s.configs[3]!!.entries)
        assertEquals(1, s.configs[3]!!.rows)
        assertEquals(5, s.configs[3]!!.cols)
        assertTrue(s.pending.isEmpty())
    }

    @Test
    fun pendingBeyondSixBySixStaysPending() {
        val pending = (1..40).map { entry("p$it") }
        val s = StoreState(pending = pending).ensureConfig(3)
        assertEquals(36, s.configs[3]!!.entries.size)
        assertEquals(6, s.configs[3]!!.rows)
        assertEquals(4, s.pending.size)
    }

    @Test
    fun addEntryAndGridFull() {
        var s = StoreState()
        for (i in 1..4) s = s.addEntry(1, entry("e$i"))!!
        assertNull(s.addEntry(1, entry("e5")))
        val col = s.addEntry(1, entry("e5"), Growth.COLUMN)!!
        assertEquals(5, col.configs[1]!!.cols)
        assertEquals("e5", col.configs[1]!!.entries.last().label)
        val row = s.addEntry(1, entry("e5"), Growth.ROW)!!
        assertEquals(2, row.configs[1]!!.rows)
        assertEquals(5, row.configs[1]!!.entries.size)
    }

    @Test
    fun growthIsCappedAtSix() {
        val full = WidgetConfig(1, 6, (1..6).map { entry("e$it") })
        val s = StoreState(configs = mapOf(1 to full))
        assertNull(s.addEntry(1, entry("x"), Growth.COLUMN))
        assertEquals(2, s.addEntry(1, entry("x"), Growth.ROW)!!.configs[1]!!.rows)
    }

    @Test
    fun addEntryIsIdempotent() {
        val e = entry("a")
        val s = StoreState().addEntry(1, e)!!
        assertEquals(1, s.addEntry(1, e)!!.configs[1]!!.entries.size)
    }

    @Test
    fun pinCallbackAfterFirstUpdateTookPendingDoesNotDuplicate() {
        val e = entry("shared")
        val s = StoreState().addPending(e)
            .ensureConfig(9) // widget's first onUpdate takes the pending entry
            .completePin(9, e) // then the success callback arrives
        assertEquals(listOf(e), s.configs[9]!!.entries)
        assertTrue(s.pending.isEmpty())
    }

    @Test
    fun pinCallbackBeforeFirstUpdateAddsOnce() {
        val e = entry("shared")
        val s = StoreState().addPending(e)
            .completePin(9, e) // callback first: creates the config (taking pending) and does not add again
            .ensureConfig(9)
        assertEquals(listOf(e), s.configs[9]!!.entries)
        assertTrue(s.pending.isEmpty())
    }

    @Test
    fun pinCallbackWithLostPendingStillAddsTheEntry() {
        val e = entry("shared")
        val s = StoreState().completePin(9, e)
        assertEquals(listOf(e), s.configs[9]!!.entries)
    }

    @Test
    fun pinCallbackForEntryAlreadyOnAnotherWidgetIsIgnored() {
        val e = entry("shared")
        val s = StoreState().addPending(e).ensureConfig(4).completePin(9, e)
        assertEquals(listOf(e), s.configs[4]!!.entries)
        assertTrue(s.configs[9]!!.entries.isEmpty())
    }

    @Test
    fun pinCallbackIntoFullWidgetGrows() {
        val full = WidgetConfig(1, 4, (1..4).map { entry("e$it") })
        val s = StoreState(configs = mapOf(2 to full)).completePin(2, entry("new"))
        assertEquals(5, s.configs[2]!!.entries.size)
        assertEquals(5, s.configs[2]!!.cols)
    }

    @Test
    fun pruneAndDelete() {
        val s = StoreState(configs = mapOf(1 to WidgetConfig(), 2 to WidgetConfig(), 3 to WidgetConfig()))
        assertEquals(setOf(2), s.prune(listOf(2, 5)).configs.keys)
        assertEquals(setOf(1, 3), s.delete(listOf(2)).configs.keys)
    }

    // ---------------------------------------------------------------- E5: a removed widget's destinations are kept

    @Test
    fun deletedWidgetsDestinationsMoveToPending_andTheNextWidgetGetsThemBack() {
        val a = entry("a")
        val b = entry("b")
        val s = StoreState(configs = mapOf(4 to WidgetConfig(1, 4, listOf(a, b)))).delete(listOf(4))
        assertTrue(s.configs.isEmpty())
        assertEquals(listOf(a, b), s.pending)
        val next = s.ensureConfig(9)
        assertEquals(listOf(a, b), next.configs[9]!!.entries)
        assertTrue(next.pending.isEmpty())
    }

    @Test
    fun deleteAppendsAfterExistingPending_withoutDuplicates() {
        val p = entry("p")
        val a = entry("a")
        val s = StoreState(configs = mapOf(4 to WidgetConfig(1, 4, listOf(p, a))), pending = listOf(p)).delete(listOf(4))
        assertEquals("p already pending is not added twice", listOf(p, a), s.pending)
    }

    @Test
    fun deleteSkipsEntriesThatAreOnAnotherWidget() {
        val a = entry("a")
        val b = entry("b")
        val s = StoreState(configs = mapOf(2 to WidgetConfig(1, 4, listOf(a)), 4 to WidgetConfig(1, 4, listOf(a, b)))).delete(listOf(4))
        assertEquals(listOf(b), s.pending)
        assertEquals(listOf(a), s.configs[2]!!.entries)
    }

    @Test
    fun deleteOfUnknownOrEmptyWidgetChangesNothingElse() {
        val s = StoreState(configs = mapOf(2 to WidgetConfig(1, 4, listOf(entry("a")))), pending = listOf(entry("p")))
        assertSame(s, s.delete(listOf(7)))
        val emptyRemoved = StoreState(configs = s.configs + (3 to WidgetConfig()), pending = s.pending).delete(listOf(3))
        assertEquals(s, emptyRemoved)
    }

    @Test
    fun deleteOfSeveralWidgetsKeepsAll_inWidgetOrder() {
        val (a, b, c) = listOf(entry("a"), entry("b"), entry("c"))
        val s = StoreState(configs = mapOf(5 to WidgetConfig(1, 4, listOf(c)), 3 to WidgetConfig(1, 4, listOf(a, b)))).delete(listOf(5, 3))
        assertEquals(listOf(a, b, c), s.pending)
    }

    @Test
    fun pruneKeepsDestinationsOfVanishedWidgets() {
        val a = entry("a")
        val s = StoreState(configs = mapOf(4 to WidgetConfig(1, 4, listOf(a)), 6 to WidgetConfig(1, 4, listOf(entry("x"))))).prune(listOf(6))
        assertEquals(setOf(6), s.configs.keys)
        assertEquals(listOf(a), s.pending)
    }

    @Test
    fun fullWidgetRemoved_36Entries_comeBackInTheNextWidget_overflowStaysPending() {
        val pendingBefore = (1..3).map { entry("p$it") }
        val full = (1..36).map { entry("w$it") }
        val s = StoreState(configs = mapOf(4 to WidgetConfig(6, 6, full)), pending = pendingBefore).delete(listOf(4))
        assertEquals(39, s.pending.size)
        val next = s.ensureConfig(5)
        assertEquals(pendingBefore + full.take(33), next.configs[5]!!.entries)
        assertEquals(6 to 6, next.configs[5]!!.rows to next.configs[5]!!.cols)
        assertEquals(full.drop(33), next.pending)
        // Nothing lost, nothing twice.
        val all = next.configs.values.flatMap { it.entries } + next.pending
        assertEquals(39, all.map { it.id }.toSet().size)
        assertEquals(39, all.size)
    }

    /** "Replace": the old widget's onDeleted before the new widget's first update. */
    @Test
    fun replace_onDeletedFirst_thenNewWidgetsFirstUpdate() {
        val (a, b) = listOf(entry("a"), entry("b"))
        val start = StoreState(configs = mapOf(4 to WidgetConfig(1, 4, listOf(a, b))))
        val s = start.delete(listOf(4)).ensureConfig(5)
        assertEquals(mapOf(5 to WidgetConfig(1, 4, listOf(a, b))), s.configs)
        assertTrue(s.pending.isEmpty())
    }

    /** "Replace": the new widget's first update (empty config) before the old widget's onDeleted; same end state. */
    @Test
    fun replace_newWidgetsFirstUpdateFirst_thenOnDeleted() {
        val (a, b) = listOf(entry("a"), entry("b"))
        val start = StoreState(configs = mapOf(4 to WidgetConfig(1, 4, listOf(a, b))))
        val newFirst = start.ensureConfig(5).delete(listOf(4))
        assertEquals(start.delete(listOf(4)).ensureConfig(5), newFirst)
        assertEquals(listOf(a, b), newFirst.configs[5]!!.entries)
        assertTrue(newFirst.pending.isEmpty())
    }

    @Test
    fun replace_bothOrdersAgree_alsoWithPendingAndOverflow() {
        val pendingBefore = listOf(entry("p"))
        val old = (1..36).map { entry("w$it") }
        val start = StoreState(configs = mapOf(4 to WidgetConfig(6, 6, old)), pending = pendingBefore)
        val deletedFirst = start.delete(listOf(4)).ensureConfig(5)
        // New first: its first update takes "p"; it is no longer empty, so the old entries stay pending for the next one.
        val newFirst = start.ensureConfig(5).delete(listOf(4))
        for (s in listOf(deletedFirst, newFirst)) {
            val all = s.configs.values.flatMap { it.entries } + s.pending
            assertEquals("nothing lost or doubled", 37, all.map { it.id }.toSet().size)
            assertEquals(37, all.size)
        }
        assertEquals(36, deletedFirst.configs[5]!!.entries.size)
        assertEquals(listOf(pendingBefore.single()), newFirst.configs[5]!!.entries)
        assertEquals(old, newFirst.pending)
    }

    @Test
    fun olderEmptyWidget_orGhostWithLowerId_doesNotTakeOverARemovedWidgetsDestinations() {
        val a = entry("a")
        // 3 is an older (lower ID) empty widget, e.g. a ghost left by an earlier Replace (decisions 3.9).
        val s = StoreState(configs = mapOf(3 to WidgetConfig(), 4 to WidgetConfig(1, 4, listOf(a)))).delete(listOf(4))
        assertTrue(s.configs[3]!!.entries.isEmpty())
        assertEquals(listOf(a), s.pending)
    }

    @Test
    fun removeAndPlaceAgain_repeatedly_keepsTheSameEntriesOnce() {
        var s = StoreState(configs = mapOf(1 to WidgetConfig(1, 4, listOf(entry("a"), entry("b")))))
        for (id in 2..10) s = s.delete(listOf(id - 1)).ensureConfig(id)
        assertEquals(listOf("a", "b"), s.configs[10]!!.entries.map { it.label })
        assertTrue(s.pending.isEmpty())
    }

    @Test
    fun addPendingIgnoresDuplicates() {
        val e = entry("a")
        assertEquals(1, StoreState().addPending(e).addPending(e).pending.size)
    }

    @Test
    fun summaryForChooser() {
        assertNull(WidgetConfig().summary())
        assertEquals("Home · Gym · Stadium", WidgetConfig(entries = listOf(entry("Home"), entry("Gym"), entry("Stadium"))).summary())
    }

    @Test
    fun saveEditedReplacesConfigInEditedOrder() {
        val a = entry("a"); val b = entry("b"); val c = entry("c")
        val original = WidgetConfig(1, 4, listOf(a, b, c))
        val s = StoreState(configs = mapOf(5 to original))
        val edited = WidgetConfig(2, 2, listOf(c, a), style = AnyconSettings(style = AnyconStyle.Marble()))
        assertEquals(edited, s.saveEdited(5, original, edited).configs[5])
    }

    @Test
    fun saveEditedKeepsEntriesAddedMeanwhile() {
        val a = entry("a"); val b = entry("b"); val shared = entry("shared")
        val original = WidgetConfig(1, 2, listOf(a, b))
        // A share added "shared" (growing to 1×3) while the config screen was open.
        val s = StoreState(configs = mapOf(5 to WidgetConfig(1, 3, listOf(a, b, shared))))
        val edited = WidgetConfig(1, 2, listOf(b)) // user deleted a
        val saved = s.saveEdited(5, original, edited).configs[5]!!
        assertEquals(listOf(b, shared), saved.entries)
        assertEquals(1 to 2, saved.rows to saved.cols)
    }

    @Test
    fun saveEditedNeverDropsEntries() {
        val many = (1..5).map { entry("e$it") }
        val original = WidgetConfig(1, 4, emptyList())
        val tooSmall = WidgetConfig(1, 2, many) // UI blocks this; the store still keeps everything
        val saved = StoreState().saveEdited(5, original, tooSmall).configs[5]!!
        assertEquals(many, saved.entries)
        assertTrue(saved.capacity >= 5)
        val huge = (1..40).map { entry("h$it") }
        val after = StoreState().saveEdited(5, original, WidgetConfig(6, 6, huge))
        assertEquals(36, after.configs[5]!!.entries.size)
        assertEquals(huge.drop(36), after.pending)
    }

    @Test
    fun configJsonFormat() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val config = WidgetConfig(1, 4, listOf(Entry("Home", "46.05,14.5", IconRef.builtin("home"), id = "x")))
        val text = json.encodeToString(WidgetConfig.serializer(), config)
        assertTrue(
            text,
            text.startsWith(
                """{"rows":1,"cols":4,"entries":[{"label":"Home","destination":"46.05,14.5","icon":{"source":"builtin","name":"home"},"id":"x"}],"style":{"style":{"type":"sticker","palette":"coral"}""",
            ),
        )
        assertEquals(config, json.decodeFromString(WidgetConfig.serializer(), text))
        // A 0.2.0 config (no style) loads with the default style and keeps its entries (no migration).
        val v020 = json.decodeFromString(
            WidgetConfig.serializer(),
            """{"rows":1,"cols":4,"entries":[{"label":"Stožice Stadium","destination":"Stožice","icon":{"source":"builtin","name":"stadium"},"id":"s"}]}""",
        )
        assertEquals(WidgetConfig.DEFAULT_STYLE, v020.style)
        assertEquals(IconRef.builtin("stadium"), v020.entries.single().icon)
        // Another style round-trips.
        val watercolor = config.copy(style = AnyconSettings(style = AnyconStyle.Watercolor(), seedColor = 0xFF2E9D57.toInt()))
        assertEquals(watercolor, json.decodeFromString(WidgetConfig.serializer(), json.encodeToString(WidgetConfig.serializer(), watercolor)))
        // Entries without an id (hand-written or older) still load.
        val noId = json.decodeFromString(
            WidgetConfig.serializer(),
            """{"rows":2,"cols":3,"entries":[{"label":"A","destination":"B","icon":{"source":"own","name":"z"}}],"future":1}""",
        )
        assertEquals("A", noId.entries.single().label)
        assertTrue(noId.entries.single().id.isNotEmpty())
    }
}
