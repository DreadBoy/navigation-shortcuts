package io.github.dreadboy.navigationshortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationUrisTest {
    @Test
    fun hardcodedDestinationUri() {
        assertEquals(
            "google.navigation:q=Maribor%2C%20Slovenija&mode=d",
            NavigationUris.drivingNavigation(HardcodedDestination.QUERY),
        )
    }

    @Test
    fun coordinatesAreEncodedLikeUriEncode() {
        assertEquals(
            "google.navigation:q=46.5547%2C15.6459&mode=d",
            NavigationUris.drivingNavigation("46.5547,15.6459"),
        )
    }

    @Test
    fun encodeMatchesUriEncodeRules() {
        assertEquals("a-b_c.d~e!f'g(h)i*j", NavigationUris.encode("a-b_c.d~e!f'g(h)i*j"))
        assertEquals("%26%3D%2B%23%3F%2F", NavigationUris.encode("&=+#?/"))
        assertEquals("Ljubljanska%20cesta%20%C5%A1t.%205", NavigationUris.encode("Ljubljanska cesta št. 5"))
    }
}
