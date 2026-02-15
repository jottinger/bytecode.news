/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for the GitHub polling service */
@ConfigurationProperties(prefix = "streampack.github")
data class GitHubProperties(
    val pollInterval: Duration = Duration.ofMinutes(60),
    val connectTimeoutSeconds: Int = 5,
    val readTimeoutSeconds: Int = 10,
)
