/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.calc.operation

import com.enigmastation.streampack.calc.service.CalculatorService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.parser.CommandLexer
import com.enigmastation.streampack.core.parser.CommandMatchResult
import com.enigmastation.streampack.core.parser.CommandPattern
import com.enigmastation.streampack.core.parser.CommandPatternMatcher
import com.enigmastation.streampack.core.service.TranslatingOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

data class CalculatorRequest(val expression: String)

/** Evaluates `calc` commands using shared command lexer/matcher parsing. */
@Component
class CalculatorOperation(val service: CalculatorService) :
    TranslatingOperation<CalculatorRequest>(CalculatorRequest::class) {

    override val operationGroup: String = "calc"

    override fun translate(payload: String, message: Message<*>): CalculatorRequest? {
        return when (matcher.match(payload)) {
            is CommandMatchResult.Match -> {
                val lexed = CommandLexer.lex(payload)
                val expression = lexed.tokens.drop(1).joinToString(" ").trim()
                CalculatorRequest(expression)
            }
            is CommandMatchResult.TooManyArguments -> {
                val lexed = CommandLexer.lex(payload)
                val expression = lexed.tokens.drop(1).joinToString(" ").trim()
                CalculatorRequest(expression)
            }
            else -> null
        }
    }

    override fun handle(payload: CalculatorRequest, message: Message<*>): OperationOutcome {
        val expression = payload.expression
        if (expression.isBlank()) {
            return OperationResult.Error("No expression provided")
        }
        return service.evaluate(expression)?.let {
            OperationResult.Success("The result of $expression is: $it")
        } ?: OperationResult.Error("Invalid expression: $expression")
    }

    private companion object {
        private val matcher =
            CommandPatternMatcher(listOf(CommandPattern(name = "calc", literals = listOf("calc"))))
    }
}
