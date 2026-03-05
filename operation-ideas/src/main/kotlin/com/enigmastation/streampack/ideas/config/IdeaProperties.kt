/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ideas.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration for article idea session behavior */
@ConfigurationProperties(prefix = "streampack.ideas")
data class IdeaProperties(val sessionTimeoutMinutes: Long = 5)
