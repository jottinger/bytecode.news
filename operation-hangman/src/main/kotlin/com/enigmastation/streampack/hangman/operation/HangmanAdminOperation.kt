/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.hangman.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.hangman.service.HangmanService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Admin commands for managing the hangman blocklist */
@Component
class HangmanAdminOperation(private val hangmanService: HangmanService) :
    TypedOperation<String>(String::class) {

    override val priority: Int = 49
    override val addressed: Boolean = true
    override val operationGroup: String = "hangman-admin"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val cmd = payload.compress().lowercase()
        return cmd.startsWith("hangman block ") || cmd.startsWith("hangman unblock ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val role = provenance?.user?.role ?: Role.GUEST
        if (role < Role.ADMIN) {
            return OperationResult.Error("Only admins can manage the hangman blocklist.")
        }

        val compressed = payload.compress().lowercase()
        return when {
            compressed.startsWith("hangman block ") -> {
                val word = compressed.substringAfter("hangman block ").trim()
                if (word.isBlank()) {
                    return OperationResult.Error("Usage: {{ref:hangman block <word>}}")
                }
                hangmanService.blockWord(word)
                OperationResult.Success("Blocked '$word' from hangman games.")
            }
            compressed.startsWith("hangman unblock ") -> {
                val word = compressed.substringAfter("hangman unblock ").trim()
                if (word.isBlank()) {
                    return OperationResult.Error("Usage: {{ref:hangman unblock <word>}}")
                }
                hangmanService.unblockWord(word)
                OperationResult.Success("Unblocked '$word' from hangman games.")
            }
            else -> null
        }
    }
}
