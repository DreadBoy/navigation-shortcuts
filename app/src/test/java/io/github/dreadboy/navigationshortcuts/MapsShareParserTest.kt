package io.github.dreadboy.navigationshortcuts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Link resolver rules (decisions 1.5) over every captured Maps share in maps-share-fixtures.json. */
class MapsShareParserTest {

    private val fixtures: List<JsonObject> by lazy {
        val text = javaClass.classLoader!!.getResource("maps-share-fixtures.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject["fixtures"]!!.jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    /** The first Location header, exactly as captured. */
    private fun JsonObject.firstLocation(): String =
        this["redirect_chain"]!!.jsonArray.first().jsonObject["location"]!!.jsonPrimitive.content

    @Test
    fun fixturesAreLoaded() {
        assertEquals(12, fixtures.size)
    }

    @Test
    fun everyFixtureResolvesToTheExpectedDestinationAndLabel() {
        for (f in fixtures) {
            val id = f.str("id")
            val sharedText = f.str("shared_text")!!
            val shortUrl = f.str("short_url")!!
            val result = MapsShareParser.resolve(sharedText) { url ->
                assertEquals("fetched URL for $id", shortUrl, url)
                f.firstLocation()
            }
            if (result !is Resolution.Resolved) fail("$id: expected Resolved, got $result")
            result as Resolution.Resolved

            val lat = f.dbl("expected_lat")
            val lng = f.dbl("expected_lng")
            if (lat != null && lng != null) {
                assertTrue("$id has coordinates", result.hasCoordinates)
                val (rLat, rLng) = result.destination.split(",").map { it.toDouble() }
                assertEquals("$id lat", lat, rLat, 0.0)
                assertEquals("$id lng", lng, rLng, 0.0)
            } else {
                assertFalse("$id has no coordinates", result.hasCoordinates)
                assertEquals("$id query", f.str("expected_query"), result.destination)
            }
            assertEquals("$id label", f.str("expected_name") ?: MapsShareParser.DROPPED_PIN, result.label)
        }
    }

    @Test
    fun finalUrlsParseWithoutNetwork() {
        for (f in fixtures) {
            val finalUrl = f.str("final_url")!!
            assertTrue(MapsShareParser.isFullMapsUrl(finalUrl))
            val result = MapsShareParser.resolve(finalUrl) { fail("no network for a full URL"); null }
            assertTrue("${f.str("id")}: $result", result is Resolution.Resolved)
        }
    }

    @Test
    fun ftidIsNeverPartOfTheDestination() {
        for (f in fixtures) {
            val place = MapsShareParser.parseMapsUrl(f.firstLocation())!!
            assertFalse(place.destination.contains("0x"))
            assertFalse(place.destination.contains("!"))
        }
    }

    @Test
    fun sharedUrlIsDetectedAsShortLink() {
        for (f in fixtures) {
            val shared = MapsShareParser.parseSharedText(f.str("shared_text")!!)
            assertEquals(f.str("short_url"), shared.url)
            assertNull(shared.name)
            assertTrue(MapsShareParser.isShortLink(shared.url!!))
        }
    }

    @Test
    fun nameAddressUrlFormatPrefersTheSharedNameForTheLabel() {
        val text = "Stadion Stožice\nVojkova cesta 100, 1000 Ljubljana\nhttps://maps.app.goo.gl/fc1PMmD6rxxv1Kx59?g_st=a"
        val location = fixtures.first { it.str("id") == "stozice" }.firstLocation()
        val result = MapsShareParser.resolve(text) { location } as Resolution.Resolved
        assertEquals("Stadion Stožice", result.label)
        assertEquals("Stožice Stadium", result.destination)
    }

    @Test
    fun droppedPinWithSharedNamePrefersTheName() {
        val f = fixtures.first { it.str("id") == "dropped-pin" }
        val result = MapsShareParser.resolve("Parking spot\n${f.str("short_url")}") { f.firstLocation() } as Resolution.Resolved
        assertEquals("Parking spot", result.label)
        assertTrue(result.hasCoordinates)
    }

    @Test
    fun offlineWithNameFallsBackToTheSharedNameText() {
        val text = "Stadion Stožice\nVojkova cesta 100, 1000 Ljubljana\nhttps://maps.app.goo.gl/fc1PMmD6rxxv1Kx59?g_st=a"
        val result = MapsShareParser.resolve(text) { throw java.net.UnknownHostException("maps.app.goo.gl") }
        result as Resolution.Failed
        assertEquals(FailureKind.NO_INTERNET, result.kind)
        assertEquals("Stadion Stožice", result.label)
        assertEquals("Stadion Stožice, Vojkova cesta 100, 1000 Ljubljana", result.destination)
    }

    @Test
    fun offlineBareUrlHasNoDestination() {
        val result = MapsShareParser.resolve("https://maps.app.goo.gl/fc1PMmD6rxxv1Kx59?g_st=a") {
            throw java.net.SocketTimeoutException("timeout")
        }
        result as Resolution.Failed
        assertEquals(FailureKind.NETWORK, result.kind)
        assertNull(result.destination)
        assertEquals("", result.label)
    }

    @Test
    fun unknownRedirectShapeFails() {
        val result = MapsShareParser.resolve("https://maps.app.goo.gl/abc") { "https://www.google.com/maps/search/?api=1" }
        assertTrue(result is Resolution.Failed)
        val noRedirect = MapsShareParser.resolve("https://maps.app.goo.gl/abc") { null }
        assertTrue(noRedirect is Resolution.Failed)
    }

    @Test
    fun nonMapsLinksAreNotFetched() {
        val result = MapsShareParser.resolve("Look https://example.com/x") { fail("must not fetch"); null }
        result as Resolution.Failed
        assertEquals("Look", result.label)
        assertNotNull(result.destination)
    }

    /** Captured on the emulator 2026-10-16 (Stožice Stadium; cid = second half of its ftid 0x…:0xda0cde94b1e5e5ed). */
    @Test
    fun cidOnlyShareFailsWithAShareAgainHint() {
        val result = MapsShareParser.resolve("https://maps.google.com/?cid=15712177930209977837&entry=gps") {
            fail("must not fetch"); null
        }
        result as Resolution.Failed
        assertEquals(FailureKind.CID_ONLY, result.kind)
        assertNull(result.destination)
        val named = MapsShareParser.resolve("Stožice Stadium\nhttps://maps.google.com/?cid=15712177930209977837") { null }
        named as Resolution.Failed
        assertEquals("Stožice Stadium", named.destination)
        assertEquals(0xda0cde94b1e5e5edUL.toString(), "15712177930209977837")
    }

    /** Verifier round 2: Maps search "47.0 14.5" → Share gives this link (no short link, no network needed). */
    @Test
    fun coordinateSearchShareIsADroppedPin() {
        val result = MapsShareParser.resolve("https://maps.google.com/?q=47.000000,14.500000&entry=gps") {
            fail("must not fetch"); null
        }
        assertEquals(Resolution.Resolved(MapsShareParser.DROPPED_PIN, "47.000000,14.500000", hasCoordinates = true), result)
        val named = MapsShareParser.resolve("My spot\nhttps://www.google.com/maps?q=46.05%2C+14.5") { null } as Resolution.Resolved
        assertEquals("My spot", named.label)
        assertEquals("46.05,14.5", named.destination)
        assertEquals("-33.86,151.2", MapsShareParser.coordinateQuery("https://maps.google.com/?q=-33.86,151.2"))
    }

    @Test
    fun nonCoordinateQueriesAreNotDroppedPins() {
        assertNull(MapsShareParser.coordinateQuery("https://maps.google.com/?q=Bled"))
        assertNull(MapsShareParser.coordinateQuery("https://maps.google.com/?cid=15712177930209977837&entry=gps"))
        assertNull(MapsShareParser.coordinateQuery("https://maps.google.com/?q=95.0,14.5")) // latitude out of range
        assertNull(MapsShareParser.coordinateQuery("https://example.com/?q=47.0,14.5"))
        assertNull(MapsShareParser.coordinateQuery("http://maps.google.com/?q=47.0,14.5"))
        // A cid link still fails with its own reason.
        val cid = MapsShareParser.resolve("https://maps.google.com/?cid=15712177930209977837&entry=gps") { null }
        assertEquals(FailureKind.CID_ONLY, (cid as Resolution.Failed).kind)
    }

    @Test
    fun browserIntentRedirectIsRejected() {
        val f = fixtures.first()
        val intentLocation = f["redirect_chain_android_chrome_ua"]!!.jsonArray.first().jsonObject["location"]!!.jsonPrimitive.content
        val result = MapsShareParser.resolve(f.str("shared_text")!!) { intentLocation }
        assertTrue("intent:// redirect must not be parsed: $result", result is Resolution.Failed)
    }
}
