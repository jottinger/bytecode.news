/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.OtpRequest
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.EmailService
import com.enigmastation.streampack.core.service.OneTimeCodeService
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Generates a one-time passcode and sends it to the requested email address */
@Component
class OtpRequestOperation(
    private val oneTimeCodeService: OneTimeCodeService,
    private val emailService: EmailService,
) : TypedOperation<OtpRequest>(OtpRequest::class) {

    override fun handle(payload: OtpRequest, message: Message<*>): OperationOutcome {
        try {
            val otc = oneTimeCodeService.generateCode(payload.email)
            emailService.sendOneTimeCode(payload.email, otc.code)
        } catch (e: IllegalStateException) {
            logger.warn("OTP rate limit hit for {}", payload.email)
        } catch (e: Exception) {
            logger.error("Failed to send OTP code to {}: {}", payload.email, e.message)
        }
        /* Never reveal whether the email exists or whether the code was actually sent */
        return OperationResult.Success("If that email is registered or valid, a code has been sent")
    }
}
