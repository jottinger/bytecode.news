/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.karma.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.karma.config.KarmaProperties
import com.enigmastation.streampack.karma.service.KarmaService
import java.util.regex.Pattern
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles karma increment/decrement: "foo++", "bar--", "c++--" */
@Component
class SetKarmaOperation(
    private val karmaService: KarmaService,
    private val karmaProperties: KarmaProperties,
) : TypedOperation<String>(String::class) {

    override val priority: Int = 40

    override val addressed: Boolean = false

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        val fixed = payload.fixArrows()
        val matcher = KARMA_PATTERN.matcher(fixed)
        if (!matcher.find()) return false
        val subject = matcher.group(1).stripCompletionSuffix()
        return subject.isNotEmpty() && subject.length <= 150
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome? {
        val fixed = payload.fixArrows()
        val matcher = KARMA_PATTERN.matcher(fixed)
        if (!matcher.find()) return null

        val subject = matcher.group(1).stripCompletionSuffix()
        if (subject.isEmpty() || subject.length > 150) return null

        val predicate = matcher.group(2)
        var increment = if (predicate == "++") 1 else -1

        // Check immune subjects
        if (karmaProperties.immuneSubjects.any { it.equals(subject, ignoreCase = true) }) {
            logger.debug("Ignoring karma change for immune subject: {}", subject)
            return null
        }

        // Resolve sender identity for self-karma check
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val senderNick = message.headers["nick"] as? String ?: provenance?.user?.username

        val selfKarma = senderNick != null && senderNick.equals(subject, ignoreCase = true)
        if (selfKarma && increment > 0) {
            increment = -1
        }

        val karma = karmaService.adjustKarma(subject, increment)

        return if (selfKarma) {
            val prefix =
                if (predicate == "++") {
                    "You can't increment your own karma! "
                } else {
                    ""
                }
            OperationResult.Success("${prefix}Your karma is now $karma.")
        } else {
            if (karma == 0) {
                OperationResult.Success("$subject has neutral karma.")
            } else {
                OperationResult.Success("$subject now has karma of $karma.")
            }
        }
    }

    companion object {
        // Greedy match from the right: last ++ or -- wins
        private val KARMA_PATTERN: Pattern = Pattern.compile("^(.+)(\\+{2}|--).*\$")

        private fun String.fixArrows() = this.replace("-->", "->").replace("<--", "<-")

        /** Strip IRC nick-completion suffixes (colon, comma, semicolon) */
        private fun String.stripCompletionSuffix() = this.trim().trimEnd(':', ',', ';').trim()
    }
}
