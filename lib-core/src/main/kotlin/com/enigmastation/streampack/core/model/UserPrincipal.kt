/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.model

import java.util.UUID

/** Lightweight identity carried in Provenance headers after authentication */
data class UserPrincipal(
    val id: UUID,
    val username: String,
    val displayName: String,
    val role: Role,
)
