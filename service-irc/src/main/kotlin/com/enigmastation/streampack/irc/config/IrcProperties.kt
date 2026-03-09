/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.irc.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Controls whether the IRC connection infrastructure is activated */
@ConfigurationProperties(prefix = "streampack.irc")
data class IrcProperties(
    val enabled: Boolean = false,
    val signalCharacter: String = "!",
    val identity: String = "Nevet IRC Bridge",
)
