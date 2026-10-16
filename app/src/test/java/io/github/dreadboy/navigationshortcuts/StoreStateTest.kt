package io.github.dreadboy.navigationshortcuts

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
    fun configJsonFormat() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val config = WidgetConfig(1, 4, listOf(Entry("Home", "46.05,14.5", IconRef.builtin("home"), id = "x")))
        val text = json.encodeToString(WidgetConfig.serializer(), config)
        assertEquals(
            """{"rows":1,"cols":4,"entries":[{"label":"Home","destination":"46.05,14.5","icon":{"source":"builtin","name":"home"},"id":"x"}]}""",
            text,
        )
        assertEquals(config, json.decodeFromString(WidgetConfig.serializer(), text))
        // Entries without an id (hand-written or older) still load.
        val noId = json.decodeFromString(
            WidgetConfig.serializer(),
            """{"rows":2,"cols":3,"entries":[{"label":"A","destination":"B","icon":{"source":"own","name":"z"}}],"future":1}""",
        )
        assertEquals("A", noId.entries.single().label)
        assertTrue(noId.entries.single().id.isNotEmpty())
    }
}
