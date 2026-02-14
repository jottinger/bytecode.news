/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.model

/** Admin request to remove a protocol identity binding from a user */
data class UnlinkProtocolRequest(
    val username: String,
    val protocol: Protocol,
    val serviceId: String,
    val externalIdentifier: String,
)
