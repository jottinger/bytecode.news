/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import com.enigmastation.streampack.core.model.UserPrincipal

/** Successful authentication result with JWT and resolved identity */
data class LoginResponse(val token: String, val principal: UserPrincipal)
