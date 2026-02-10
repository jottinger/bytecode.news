/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.calendar

import com.enigmastation.streampack.cal.model.CalendarSystem
import java.time.chrono.JapaneseChronology
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.stereotype.Component

/** Japanese imperial calendar using java.time.chrono.JapaneseChronology. */
@Component
class JapaneseCalendarSystem : CalendarSystem {
    override val name = "japanese"
    override val displayName = "Japanese"

    private val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, y GGGG", Locale.ENGLISH)

    override fun today(): String {
        val date = JapaneseChronology.INSTANCE.dateNow()
        return date.format(formatter)
    }
}
