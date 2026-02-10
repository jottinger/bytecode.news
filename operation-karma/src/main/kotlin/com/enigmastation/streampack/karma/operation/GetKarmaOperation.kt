/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.karma.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.karma.service.KarmaService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles addressed karma queries: "karma foo" */
@Component
class GetKarmaOperation(private val karmaService: KarmaService) :
    TypedOperation<String>(String::class) {

    override val priority: Int = 50

    override val addressed: Boolean = true

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        return payload.trim().startsWith("karma ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val subject = payload.trim().removePrefix("karma ").trim()
        if (subject.isBlank()) {
            return OperationResult.Error("Usage: karma <subject>")
        }

        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val senderNick = message.headers["nick"] as? String ?: provenance?.user?.username
        val selfQuery = senderNick != null && senderNick.equals(subject, ignoreCase = true)

        return if (karmaService.hasKarma(subject)) {
            val karma = karmaService.getKarma(subject)
            val karmaExpression =
                if (karma == 0) {
                    "neutral karma"
                } else {
                    "karma of $karma"
                }
            if (selfQuery) {
                OperationResult.Success("$subject, you have $karmaExpression.")
            } else {
                OperationResult.Success("$subject has $karmaExpression.")
            }
        } else {
            if (selfQuery) {
                OperationResult.Success("$subject, you have no karma data.")
            } else {
                OperationResult.Success("$subject has no karma data.")
            }
        }
    }
}
