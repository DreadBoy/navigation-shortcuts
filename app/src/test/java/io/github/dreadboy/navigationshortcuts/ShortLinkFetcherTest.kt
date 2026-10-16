package io.github.dreadboy.navigationshortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ConnectException
import java.net.UnknownHostException

/** Retry policy for just-created short links (decisions 3.5), with a fake clock and fetcher. */
class ShortLinkFetcherTest {
    private var clock = 0L
    private val sleeps = mutableListOf<Long>()

    private fun run(fetch: (String) -> String?): String? = ShortLinkFetcher.firstLocationWhenLive(
        "https://maps.app.goo.gl/x",
        fetch = fetch,
        sleep = { sleeps += it; clock += it },
        now = { clock },
    )

    @Test
    fun retriesWhileNotYetLive() {
        var calls = 0
        val location = run {
            calls++
            if (calls < 3) throw ShortLinkFetcher.NotYetLiveException() else "https://www.google.com/maps/place/X"
        }
        assertEquals("https://www.google.com/maps/place/X", location)
        assertEquals(3, calls)
        assertEquals(listOf(1_000L, 1_000L), sleeps)
    }

    @Test
    fun retriesTransientConnectFailures() {
        var calls = 0
        run { calls++; if (calls == 1) throw ConnectException("Failed to connect") else "ok" }
        assertEquals(2, calls)
    }

    @Test
    fun givesUpAfterTheBudget() {
        var calls = 0
        try {
            run { calls++; throw ShortLinkFetcher.NotYetLiveException() }
            fail("expected NotYetLiveException")
        } catch (e: ShortLinkFetcher.NotYetLiveException) {
            assertTrue("bounded: $calls calls", calls in 10..13)
            assertTrue("total wait ≤ 12 s: $clock", clock <= 12_000)
        }
    }

    @Test
    fun noNetworkFailsAtOnce() {
        var calls = 0
        try {
            run { calls++; throw UnknownHostException("maps.app.goo.gl") }
            fail("expected UnknownHostException")
        } catch (e: UnknownHostException) {
            assertEquals(1, calls)
            assertTrue(sleeps.isEmpty())
        }
    }
}
