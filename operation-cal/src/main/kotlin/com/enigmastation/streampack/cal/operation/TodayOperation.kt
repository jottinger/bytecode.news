/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.cal.operation

import com.enigmastation.streampack.cal.service.CalendarService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import java.time.LocalDate
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Reports today's date in various calendar systems. */
@Component
class TodayOperation(private val calendarService: CalendarService) :
    TypedOperation<String>(String::class) {

    override val operationGroup: String = "cal"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val trimmed = payload.trim()
        return trimmed == "today" || trimmed.startsWith("today ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val trimmed = payload.trim()
        val argument = trimmed.removePrefix("today").trim()

        if (argument.isEmpty()) {
            return OperationResult.Success(
                "Today is ${calendarService.formatDate(LocalDate.now())}"
            )
        }

        if (argument.equals("list", ignoreCase = true)) {
            val names = calendarService.listCalendars().joinToString(", ") { it.name }
            return OperationResult.Success("Available calendars: $names")
        }

        val formatted = calendarService.formatDate(LocalDate.now(), argument)
        return if (formatted != null) {
            val displayName = calendarService.getCalendar(argument)!!.displayName
            OperationResult.Success("Today is $formatted ($displayName)")
        } else {
            OperationResult.Error(
                "Unknown calendar: $argument. Use 'today list' to see available calendars."
            )
        }
    }
}
