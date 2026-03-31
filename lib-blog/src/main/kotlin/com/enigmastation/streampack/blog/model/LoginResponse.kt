/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import com.enigmastation.streampack.core.model.UserPrincipal
import com.fasterxml.jackson.annotation.JsonIgnore

/** Successful authentication result with JWT and resolved identity */
data class LoginResponse(
    val token: String,
    val principal: UserPrincipal,
    @JsonIgnore val refreshToken: String? = null,
)
