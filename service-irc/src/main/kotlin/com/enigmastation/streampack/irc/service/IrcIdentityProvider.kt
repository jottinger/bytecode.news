/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.service

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.service.IdentityDescription
import com.enigmastation.streampack.core.service.IdentityProvider
import com.enigmastation.streampack.core.service.IdentityResolution
import com.enigmastation.streampack.irc.repository.IrcNetworkRepository
import org.springframework.stereotype.Component

/** Validates IRC identities against registered networks */
@Component
class IrcIdentityProvider(private val networkRepository: IrcNetworkRepository) : IdentityProvider {

    override val protocol: Protocol = Protocol.IRC

    override fun resolveIdentity(
        serviceId: String,
        externalIdentifier: String,
    ): IdentityResolution {
        val network =
            networkRepository.findByNameAndDeletedFalse(serviceId)
                ?: return IdentityResolution.Invalid("Unknown IRC network: $serviceId")

        if (externalIdentifier.isBlank()) {
            return IdentityResolution.Invalid("IRC nick cannot be blank")
        }

        return IdentityResolution.Valid(
            serviceId = network.name,
            externalIdentifier = externalIdentifier,
        )
    }

    override fun describeIdentity(): IdentityDescription =
        IdentityDescription(
            protocol = Protocol.IRC,
            serviceIdLabel = "network",
            externalIdLabel = "nick",
            availableServices = networkRepository.findByDeletedFalse().map { it.name },
        )
}
