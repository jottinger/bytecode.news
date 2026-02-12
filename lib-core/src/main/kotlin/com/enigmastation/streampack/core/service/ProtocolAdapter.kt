/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import com.enigmastation.streampack.core.model.Protocol

interface ProtocolAdapter {
    val protocol: Protocol
    val serviceName: String
}
