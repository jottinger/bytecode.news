/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.console

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.UserRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/** Interactive console adapter that runs as the first superadmin user */
@Component
@ConditionalOnProperty("streampack.console.enabled", havingValue = "true")
class ConsoleAdapter(
    private val eventGateway: EventGateway,
    private val userRepository: UserRepository,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(ConsoleAdapter::class.java)

    override fun run(args: ApplicationArguments) {
        val principal = resolveSuperAdmin()
        if (principal == null) {
            logger.error("No superadmin user found, console cannot start")
            return
        }
        logger.info("Console started as user '{}'", principal.username)

        val provenance =
            Provenance(
                protocol = Protocol.CONSOLE,
                serviceId = "",
                replyTo = "local",
                user = principal,
            )

        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            print("> ")
            System.out.flush()
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            if (line == "exit" || line == "quit") break

            val message =
                MessageBuilder.withPayload(line).setHeader(Provenance.HEADER, provenance).build()

            when (val result = eventGateway.process(message)) {
                is OperationResult.Success -> println(result.payload)
                is OperationResult.Error -> println("ERROR: ${result.message}")
                is OperationResult.NotHandled -> println("Unknown command: $line")
            }
        }
    }

    private fun resolveSuperAdmin(): UserPrincipal? {
        return userRepository.findActiveByRole(Role.SUPER_ADMIN).firstOrNull()?.toUserPrincipal()
    }
}
