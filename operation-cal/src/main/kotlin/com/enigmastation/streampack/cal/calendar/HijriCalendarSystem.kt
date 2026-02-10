/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.calendar

import com.enigmastation.streampack.cal.model.CalendarSystem
import java.time.chrono.HijrahChronology
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.stereotype.Component

/** Islamic (Hijri) calendar using java.time.chrono.HijrahChronology. */
@Component
class HijriCalendarSystem : CalendarSystem {
    override val name = "hijri"
    override val displayName = "Hijri"

    private val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy GGGG", Locale.ENGLISH)

    override fun today(): String {
        val date = HijrahChronology.INSTANCE.dateNow()
        return date.format(formatter)
    }
}
