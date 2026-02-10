/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.calendar

import com.enigmastation.streampack.cal.model.CalendarSystem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.stereotype.Component

/** Gregorian calendar using java.time.LocalDate. */
@Component
class GregorianCalendarSystem : CalendarSystem {
    override val name = "gregorian"
    override val displayName = "Gregorian"

    private val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)

    override fun today(): String = LocalDate.now().format(formatter)
}
