/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.tell.model

import com.enigmastation.streampack.core.model.Provenance

/** A request to deliver a message to a target identified by resolved provenance */
data class TellRequest(val targetProvenance: Provenance, val message: String)
