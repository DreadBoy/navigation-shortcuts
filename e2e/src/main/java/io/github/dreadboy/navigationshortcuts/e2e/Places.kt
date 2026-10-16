package io.github.dreadboy.navigationshortcuts.e2e

/** A place the suite opens in Maps: typed [query], the suggestion row to tap, and the label our sheet resolves. */
data class Place(val query: String, val suggestion: String, val label: String)

object Places {
    val STOZICE = Place("Stozice Stadium Ljubljana", "Stožice Stadium", "Stožice Stadium")
    val CASTLE = Place("Ljubljanski grad", "Ljubljanski grad", "Ljubljana Castle")
}
