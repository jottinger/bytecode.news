/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import com.enigmastation.streampack.core.model.Role

/**
 * HTTP-layer DTO for role change; controller combines with path variable to build AlterUserRequest
 */
data class RoleUpdateRequest(val newRole: Role)
