/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.model

/** Indicates how a GitHub repository delivers events into Nevet */
enum class DeliveryMode {
    POLLING,
    WEBHOOK,
}
