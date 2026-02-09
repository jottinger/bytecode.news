/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.calc.operation

import com.enigmastation.streampack.calc.service.CalculatorService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
class CalculatorOperation(val service: CalculatorService) :
    TypedOperation<String>((String::class)) {

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        return payload.trim().startsWith("calc ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val expression = payload.trim().substringAfter("calc ").trim()
        if (expression.isBlank()) {
            return OperationResult.Error("No expression provided")
        }
        return service.evaluate(expression)?.let {
            OperationResult.Success("The result of $expression is: $it")
        } ?: OperationResult.Error("Invalid expression: $expression")
    }
}
