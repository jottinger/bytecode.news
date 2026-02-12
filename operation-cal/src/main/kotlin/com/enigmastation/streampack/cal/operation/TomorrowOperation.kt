/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.operation

import com.enigmastation.streampack.cal.service.CalendarService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.LocalDate
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Reports tomorrow's date in various calendar systems. */
@Component
class TomorrowOperation(private val calendarService: CalendarService) :
    TypedOperation<String>(String::class) {

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val trimmed = payload.trim()
        return trimmed == "tomorrow" || trimmed.startsWith("tomorrow ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val trimmed = payload.trim()
        val argument = trimmed.removePrefix("tomorrow").trim()
        val tomorrow = LocalDate.now().plusDays(1)

        if (argument.isEmpty()) {
            return OperationResult.Success("Tomorrow is ${calendarService.formatDate(tomorrow)}")
        }

        if (argument.equals("list", ignoreCase = true)) {
            val names = calendarService.listCalendars().joinToString(", ") { it.name }
            return OperationResult.Success("Available calendars: $names")
        }

        val formatted = calendarService.formatDate(tomorrow, argument)
        return if (formatted != null) {
            val displayName = calendarService.getCalendar(argument)!!.displayName
            OperationResult.Success("Tomorrow is $formatted ($displayName)")
        } else {
            OperationResult.Error(
                "Unknown calendar: $argument. Use 'tomorrow list' to see available calendars."
            )
        }
    }
}
