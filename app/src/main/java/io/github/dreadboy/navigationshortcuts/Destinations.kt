package io.github.dreadboy.navigationshortcuts

object NavigationUris {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Builds the Google Maps turn-by-turn driving URI for a destination query or "lat,lng". */
    fun drivingNavigation(destination: String): String =
        "google.navigation:q=" + encode(destination) + "&mode=d"

    /**
     * Percent-encodes [s] exactly like android.net.Uri.encode(s): letters, digits and
     * `_-!.~'()*` stay as they are, every other character is UTF-8 encoded as uppercase %XX.
     * Kept free of Android classes so it runs in plain JVM unit tests.
     */
    fun encode(s: String): String {
        val out = StringBuilder(s.length)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c < 0x80 && isUnreserved(c.toChar())) {
                out.append(c.toChar())
            } else {
                out.append('%')
                out.append(HEX[c shr 4])
                out.append(HEX[c and 0x0F])
            }
        }
        return out.toString()
    }

    private fun isUnreserved(ch: Char): Boolean =
        ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "_-!.~'()*"

    private val HEX = "0123456789ABCDEF".toCharArray()
}
