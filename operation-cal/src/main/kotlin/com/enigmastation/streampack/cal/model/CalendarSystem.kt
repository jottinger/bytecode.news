/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.model

/** A named calendar system that can format today's date as an ASCII string. */
interface CalendarSystem {
    /** Command identifier used in "today <name>" lookups, e.g. "gregorian", "hebrew" */
    val name: String

    /** Human-readable label for listing, e.g. "Gregorian", "Hebrew" */
    val displayName: String

    /** Returns today's date formatted as 7-bit ASCII text */
    fun today(): String
}
