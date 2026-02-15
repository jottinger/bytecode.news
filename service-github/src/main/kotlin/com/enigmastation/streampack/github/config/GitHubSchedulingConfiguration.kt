/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.github.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Enables Spring scheduling for GitHub repository polling */
@Configuration @EnableScheduling class GitHubSchedulingConfiguration
