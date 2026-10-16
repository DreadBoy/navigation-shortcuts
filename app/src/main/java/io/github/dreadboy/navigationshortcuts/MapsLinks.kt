package io.github.dreadboy.navigationshortcuts

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder

/** What Maps put into EXTRA_TEXT: either a bare URL (signed out) or "Name\nAddress\nURL" (decisions 1.5). */
data class SharedText(val url: String?, val nameLines: List<String>) {
    /** First non-URL line, i.e. the place name, if Maps sent one. */
    val name: String? get() = nameLines.firstOrNull()
}

/** A place parsed from a full `google.com/maps/place/...` URL. */
data class ParsedPlace(val label: String, val destination: String, val hasCoordinates: Boolean)

/** Outcome of resolving a share at add time. */
sealed interface Resolution {
    /** The label to prefill (editable). */
    val label: String

    /** Resolved: [destination] is "lat,lng" or the place text from the link. */
    data class Resolved(override val label: String, val destination: String, val hasCoordinates: Boolean) : Resolution

    /**
     * Resolution failed (offline, timeout, unexpected link). [destination] is the shared name text if Maps sent
     * one, else null and the user has to type it.
     */
    data class Failed(
        override val label: String,
        val destination: String?,
        val kind: FailureKind,
        /** Technical detail for logs; the UI shows a message per [kind]. */
        val reason: String,
    ) : Resolution
}

/** Why a share could not be resolved; each kind has its own user-facing message. */
enum class FailureKind {
    /** No network at all (unknown host). */
    NO_INTERNET,

    /** Network trouble: connection failed or timed out, also after retries. */
    NETWORK,

    /** The short link still answered 404 after the retries (decisions 3.5). */
    LINK_NOT_LIVE,

    /** Maps shared a `?cid=` link without name or address (decisions 3.5). */
    CID_ONLY,

    /** No link, or not a Google Maps link. */
    NOT_MAPS,

    /** A Maps link of a shape we don't know. */
    UNEXPECTED,
}

/**
 * Parses Google Maps shares. Pure functions, no Android or network: unit-tested over all fixtures in
 * app/src/test/resources/maps-share-fixtures.json. Rules: decisions 1.5.
 */
object MapsShareParser {
    const val DROPPED_PIN = "Dropped pin"

    private val urlRegex = Regex("""https?://\S+""")
    private val coordinatesRegex = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val placeSegmentRegex = Regex("""/maps/place/([^/?#]+)""")
    private val latLngRegex = Regex("""^\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*$""")

    fun parseSharedText(text: String): SharedText {
        val url = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', '>')
        val lines = text.lines().map { it.replace(urlRegex, "").trim() }.filter { it.isNotEmpty() }
        return SharedText(url, lines)
    }

    private fun failureKind(e: Exception): FailureKind = when (e) {
        is ShortLinkFetcher.NotYetLiveException -> FailureKind.LINK_NOT_LIVE
        is java.net.UnknownHostException -> FailureKind.NO_INTERNET
        is java.io.IOException -> FailureKind.NETWORK
        else -> FailureKind.UNEXPECTED
    }

    /**
     * `https://maps.google.com/?cid=<decimal>` without a `/place/` path: what Maps shares now and then instead of a short
     * link (1 of ~36 shares on the emulator). The cid is the place's feature ID, but neither the URL nor the page it
     * redirects to carries a name or address we may read (decisions 1.5), so it cannot become a destination.
     */
    fun isCidOnlyUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val googleHost = host == "maps.google.com" || host == "google.com" || host.endsWith(".google.com")
        return googleHost && uri.rawQuery.orEmpty().split('&').any { it.startsWith("cid=") } &&
            !uri.rawPath.orEmpty().contains("/place/")
    }

    /**
     * "lat,lng" from a Google Maps link whose `q` parameter is a coordinate pair and that has no `/place/` path, e.g.
     * `https://maps.google.com/?q=47.000000,14.500000&entry=gps` (what Maps shares for a coordinate search) or
     * `https://www.google.com/maps?q=46.05,14.5`. Null for anything else (text queries, cid links, place URLs).
     */
    fun coordinateQuery(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val googleHost = host == "maps.google.com" || host == "google.com" || host.endsWith(".google.com")
        val path = uri.rawPath.orEmpty()
        if (uri.scheme != "https" || !googleHost || "/place/" in path) return null
        if (path.isNotEmpty() && path != "/" && !path.startsWith("/maps")) return null
        val q = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("q=") }?.removePrefix("q=")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: return null
        if (!latLngRegex.matches(q)) return null
        val (lat, lng) = q.split(',').map { it.trim() }
        if (lat.toDouble() !in -90.0..90.0 || lng.toDouble() !in -180.0..180.0) return null
        return "$lat,$lng"
    }

    /** True for links that need one redirect hop (Maps short links). */
    fun isShortLink(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && (host == "maps.app.goo.gl" || (host == "goo.gl" && uri.path.orEmpty().startsWith("/maps")))
    }

    /** True for full Google Maps URLs that can be parsed without network. */
    fun isFullMapsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val googleHost = host == "google.com" || host.endsWith(".google.com") || Regex("""(www\.|maps\.)?google\.[a-z.]+""").matches(host)
        return uri.scheme == "https" && googleHost && uri.rawPath.orEmpty().startsWith("/maps")
    }

    /**
     * `!3d<lat>!4d<lng>` → destination "lat,lng"; else the URL-decoded `/place/` segment is the destination
     * (the normal case for named places). Label = segment up to the first comma, or [DROPPED_PIN] for a
     * coordinates-only segment. Never reads anything else (no ftid, no viewport `/@`).
     */
    fun parseMapsUrl(url: String): ParsedPlace? {
        val segment = placeSegmentRegex.find(url)?.groupValues?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.trim()?.takeIf { it.isNotEmpty() }
        val coordinates = coordinatesRegex.find(url)?.let { "${it.groupValues[1]},${it.groupValues[2]}" }
        val label = when {
            segment == null -> null
            latLngRegex.matches(segment) -> DROPPED_PIN
            else -> segment.substringBefore(',').trim()
        }
        return when {
            coordinates != null -> ParsedPlace(label ?: DROPPED_PIN, coordinates, hasCoordinates = true)
            segment != null && label != null -> ParsedPlace(label, segment, hasCoordinates = latLngRegex.matches(segment))
            else -> null
        }
    }

    /**
     * Full resolution of a share. [fetchLocation] returns the first `Location` header of a short link (or throws /
     * returns null on failure); it is only called for short links.
     */
    fun resolve(text: String, fetchLocation: (String) -> String?): Resolution {
        val shared = parseSharedText(text)
        val sharedName = shared.name
        val fallbackDestination = shared.nameLines.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val url = shared.url
            ?: return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, FailureKind.NOT_MAPS, "no link in the shared text")

        coordinateQuery(url)?.let { latLng ->
            // Maps shares a coordinate search as `maps.google.com/?q=<lat>,<lng>` (round 3): a dropped pin.
            return Resolution.Resolved(sharedName ?: DROPPED_PIN, latLng, hasCoordinates = true)
        }
        if (isCidOnlyUrl(url)) {
            // Maps' occasional fallback share (decisions 3.5): a place ID without name or address.
            return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, FailureKind.CID_ONLY, "cid-only link")
        }
        val fullUrl = when {
            isFullMapsUrl(url) -> url
            isShortLink(url) -> try {
                fetchLocation(url)
            } catch (e: Exception) {
                return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, failureKind(e), "${e.javaClass.simpleName}: ${e.message}")
            } ?: return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, FailureKind.UNEXPECTED, "no redirect from the link")
            else -> return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, FailureKind.NOT_MAPS, "not a Google Maps link")
        }
        coordinateQuery(fullUrl)?.let { latLng ->
            // Maps 25.11 shares a coordinate search as `goo.gl/maps/<id>` redirecting to `maps.google.com/?q=<lat>,<lng>`.
            return Resolution.Resolved(sharedName ?: DROPPED_PIN, latLng, hasCoordinates = true)
        }
        if (!isFullMapsUrl(fullUrl)) {
            return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, FailureKind.UNEXPECTED, "unexpected redirect")
        }
        val place = parseMapsUrl(fullUrl)
            ?: return Resolution.Failed(sharedName.orEmpty(), fallbackDestination, FailureKind.UNEXPECTED, "unexpected link format")
        return Resolution.Resolved(sharedName ?: place.label, place.destination, place.hasCoordinates)
    }
}

/** Network half of the resolver: reads the first redirect of a Maps short link. Call off the main thread. */
object ShortLinkFetcher {
    private const val TIMEOUT_MS = 4_000

    /** A just-shared short link answers 404 until it is live (decisions 3.5): keep asking for this long. */
    private const val NOT_YET_LIVE_BUDGET_MS = 12_000L
    private const val RETRY_DELAY_MS = 1_000L

    /**
     * [firstLocation], retrying while the link answers 404 (Maps creates the short link at share time and it can
     * take a few seconds to resolve) or the connection fails transiently (connect error / timeout). No retry when
     * there is no network at all (unknown host): that fails at once. [log] receives one line per attempt.
     */
    fun firstLocationWhenLive(
        url: String,
        log: (String) -> Unit = {},
        fetch: (String) -> String? = ::firstLocation,
        sleep: (Long) -> Unit = Thread::sleep,
        now: () -> Long = System::currentTimeMillis,
    ): String? {
        val start = now()
        var attempt = 1
        while (true) {
            try {
                return fetch(url).also { log("attempt $attempt after ${now() - start} ms: redirect") }
            } catch (e: java.io.IOException) {
                val retryable = e is NotYetLiveException || e is java.net.ConnectException ||
                    e is java.net.SocketTimeoutException || e is java.net.NoRouteToHostException
                val elapsed = now() - start
                log("attempt $attempt after $elapsed ms: ${e.javaClass.simpleName} ${e.message}")
                if (!retryable || elapsed + RETRY_DELAY_MS > NOT_YET_LIVE_BUDGET_MS) throw e
                sleep(RETRY_DELAY_MS)
                attempt++
            }
        }
    }

    class NotYetLiveException : java.io.IOException("link not found (HTTP 404)")

    /**
     * Reads the first `Location` header without following redirects. Uses the platform's default (Dalvik)
     * user agent: a browser UA gets an `intent://` redirect instead (decisions 1.5).
     */
    fun firstLocation(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NotYetLiveException()
            if (code !in 300..399) throw java.io.IOException("HTTP $code instead of a redirect")
            return connection.getHeaderField("Location")
        } finally {
            connection.disconnect()
        }
    }
}
