/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.model

/** Hierarchical user roles: GUEST < USER < ADMIN < SUPER_ADMIN */
enum class Role {
    GUEST,
    USER,
    ADMIN,
    SUPER_ADMIN,
}
