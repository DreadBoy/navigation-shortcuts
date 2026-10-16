package io.github.dreadboy.navigationshortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

/** Chooser order/labels and ghost flagging (decisions 3.9), with probe statuses as measured on Niagara. */
class WidgetChooserTest {
    private fun shown(gen: Long, at: Long) = RenderProbe.Status(pushed = gen, shown = gen, pushedAt = at, shownAt = at, unconfirmedSince = at)
    private fun pending(pushed: Long, shown: Long, since: Long, shownAt: Long) =
        RenderProbe.Status(pushed = pushed, shown = shown, pushedAt = since, shownAt = shownAt, unconfirmedSince = since)

    @Test
    fun ghostIsFlaggedWhenAnotherWidgetWasDisplayedLater() {
        // Repro B as measured: ghost 251 got render 3 at t=100 and never showed it; visible 252 showed render 3 at t=150.
        val statuses = mapOf(251 to pending(3, 2, since = 100, shownAt = 50), 252 to shown(3, 150))
        assertEquals(setOf(251), WidgetChooser.notSeen(statuses))
        assertEquals(listOf(252, 251), WidgetChooser.order(statuses.keys, WidgetChooser.notSeen(statuses)))
    }

    @Test
    fun widgetsPendingTogetherAreNotFlagged() {
        // Both updated while the user was in Maps (launcher not listening): nobody displayed anything since.
        val statuses = mapOf(1 to pending(4, 3, since = 100, shownAt = 90), 2 to pending(5, 4, since = 100, shownAt = 90))
        assertEquals(emptySet<Int>(), WidgetChooser.notSeen(statuses))
        assertEquals(listOf(2, 1), WidgetChooser.order(statuses.keys, emptySet()))
    }

    @Test
    fun withoutAWitnessAWidgetIsNeverFlaggedRegardlessOfAge() {
        // Verifier round 2: a visible widget, user stays in Maps for >10 min (or days) — still not flagged.
        val single = mapOf(7 to pending(2, 1, since = 1_000, shownAt = 500))
        assertEquals(emptySet<Int>(), WidgetChooser.notSeen(single))
        // A witness that was displayed only *before* this widget's render was pushed doesn't count either.
        val oldWitness = mapOf(7 to pending(9, 8, since = 10_000_000, shownAt = 500), 8 to shown(4, 9_999_999))
        assertEquals(emptySet<Int>(), WidgetChooser.notSeen(oldWitness))
    }

    @Test
    fun newestFirstWithoutProbeData() {
        val none = RenderProbe.Status(0, 0, 0, 0, 0)
        val statuses = mapOf(3 to none, 9 to none, 5 to none)
        assertEquals(listOf(9, 5, 3), WidgetChooser.order(statuses.keys, WidgetChooser.notSeen(statuses)))
    }

    @Test
    fun duplicateLabelsAreDisambiguated() {
        val labels = WidgetChooser.labels(
            ids = listOf(4, 8, 6, 2),
            summaries = mapOf(4 to null, 8 to null, 6 to null, 2 to "Home · Gym"),
            notSeen = setOf(4),
            emptyLabel = "Empty widget",
            newest = "newest",
            older = "older",
            notSeenSuffix = "not seen on home screen",
        )
        assertEquals("Empty widget (newest)", labels[8])
        assertEquals("Empty widget (older 1)", labels[6])
        assertEquals("Empty widget (older 2) — not seen on home screen", labels[4])
        assertEquals("Home · Gym", labels[2])
        val two = WidgetChooser.labels(listOf(1, 2), mapOf(), emptySet(), "Empty widget", "newest", "older", "x")
        assertEquals(mapOf(2 to "Empty widget (newest)", 1 to "Empty widget (older)"), two)
    }
}
