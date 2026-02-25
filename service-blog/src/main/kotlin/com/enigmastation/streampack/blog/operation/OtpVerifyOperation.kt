/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.OtpVerifyRequest
import com.enigmastation.streampack.blog.service.UserConvergenceService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.OneTimeCodeService
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Validates a one-time passcode and authenticates the user, creating an account if needed */
@Component
class OtpVerifyOperation(
    private val oneTimeCodeService: OneTimeCodeService,
    private val userConvergenceService: UserConvergenceService,
) : TypedOperation<OtpVerifyRequest>(OtpVerifyRequest::class) {

    override fun handle(payload: OtpVerifyRequest, message: Message<*>): OperationOutcome {
        if (!oneTimeCodeService.consumeCode(payload.email, payload.code)) {
            return OperationResult.Error("Invalid or expired code")
        }

        return try {
            val loginResponse = userConvergenceService.converge(payload.email)
            OperationResult.Success(loginResponse)
        } catch (e: IllegalStateException) {
            OperationResult.Error(e.message ?: "Authentication failed")
        }
    }
}
