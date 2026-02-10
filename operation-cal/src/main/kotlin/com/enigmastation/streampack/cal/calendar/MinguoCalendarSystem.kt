/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.calendar

import com.enigmastation.streampack.cal.model.CalendarSystem
import java.time.chrono.MinguoChronology
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.stereotype.Component

/** Minguo (Republic of China) calendar using java.time.chrono.MinguoChronology. */
@Component
class MinguoCalendarSystem : CalendarSystem {
    override val name = "minguo"
    override val displayName = "Minguo"

    private val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, y GGGG", Locale.ENGLISH)

    override fun today(): String {
        val date = MinguoChronology.INSTANCE.dateNow()
        return date.format(formatter)
    }
}
