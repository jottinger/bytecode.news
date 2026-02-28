/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.ProtocolAdapter
import org.springframework.stereotype.Component

/** Declares the blog HTTP service as a discoverable protocol adapter */
@Component
class BlogProtocolAdapter : ProtocolAdapter {
    override val protocol: Protocol = Protocol.HTTP
    override val serviceName: String = "blog"

    override fun wouldTriggerIngress(text: String): Boolean = false

    override fun sendReply(provenance: Provenance, text: String) {
        // HTTP controllers handle responses directly
    }
}
