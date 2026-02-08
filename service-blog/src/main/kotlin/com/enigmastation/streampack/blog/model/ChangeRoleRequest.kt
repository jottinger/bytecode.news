/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

import com.enigmastation.streampack.core.model.Role

/** Super-admin request to change a user's role */
data class ChangeRoleRequest(val username: String, val newRole: Role)
