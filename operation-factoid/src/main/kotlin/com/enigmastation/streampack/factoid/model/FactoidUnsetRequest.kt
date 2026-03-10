/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.model

/** Typed request to remove a mutable attribute from an existing factoid. */
data class FactoidUnsetRequest(val selector: String, val attribute: FactoidAttributeType)
