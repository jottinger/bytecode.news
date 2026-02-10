/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.calendar

import com.enigmastation.streampack.cal.model.CalendarSystem
import java.time.chrono.ThaiBuddhistChronology
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.stereotype.Component

/** Thai Buddhist calendar using java.time.chrono.ThaiBuddhistChronology. */
@Component
class ThaiBuddhistCalendarSystem : CalendarSystem {
    override val name = "thai-buddhist"
    override val displayName = "Thai Buddhist"

    private val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, y GGGG", Locale.ENGLISH)

    override fun today(): String {
        val date = ThaiBuddhistChronology.INSTANCE.dateNow()
        return date.format(formatter)
    }
}
