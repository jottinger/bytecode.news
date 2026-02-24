/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.enigmastation.streampack"])
@ConfigurationPropertiesScan("com.enigmastation.streampack")
@EntityScan("com.enigmastation.streampack")
@EnableJpaRepositories("com.enigmastation.streampack")
class NevetApplication(
    @Autowired(required = false) private val gitProperties: GitProperties?,
    @Autowired(required = false) private val buildProperties: BuildProperties?,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Log build identity at startup so deployed instances are identifiable */
    @EventListener(ApplicationReadyEvent::class)
    fun logVersionOnStartup() {
        val parts = mutableListOf<String>()

        val name = buildProperties?.name ?: "Nevet"
        val version = buildProperties?.version
        parts.add(if (version != null) "$name $version" else name)

        val commit = gitProperties?.shortCommitId
        val branch = gitProperties?.branch
        if (commit != null) {
            parts.add(if (branch != null) "$commit ($branch)" else commit)
        } else {
            parts.add("development build")
        }

        logger.info("Started: {}", parts.joinToString(" | "))
    }
}

fun main(args: Array<String>) {
    runApplication<NevetApplication>(*args)
}
