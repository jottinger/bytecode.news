/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.model

/** Account lifecycle states: ACTIVE -> SUSPENDED -> ACTIVE (reversible) or -> ERASED (terminal) */
enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    ERASED,
}
