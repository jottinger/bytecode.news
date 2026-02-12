/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.slack.service

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.service.IdentityProvider
import com.enigmastation.streampack.core.service.IdentityResolution
import com.enigmastation.streampack.slack.repository.SlackWorkspaceRepository
import org.springframework.stereotype.Component

/** Validates Slack identities against registered workspaces */
@Component
class SlackIdentityProvider(private val workspaceRepository: SlackWorkspaceRepository) :
    IdentityProvider {

    override val protocol: Protocol = Protocol.SLACK

    override fun resolveIdentity(
        serviceId: String,
        externalIdentifier: String,
    ): IdentityResolution {
        val workspace =
            workspaceRepository.findByNameAndDeletedFalse(serviceId)
                ?: return IdentityResolution.Invalid("Unknown Slack workspace: $serviceId")

        if (externalIdentifier.isBlank()) {
            return IdentityResolution.Invalid("Slack user ID cannot be blank")
        }

        return IdentityResolution.Valid(
            serviceId = workspace.name,
            externalIdentifier = externalIdentifier,
        )
    }
}
