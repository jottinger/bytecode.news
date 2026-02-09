/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.enigmastation.streampack"])
@ConfigurationPropertiesScan("com.enigmastation.streampack")
@EntityScan("com.enigmastation.streampack")
@EnableJpaRepositories("com.enigmastation.streampack")
class BlogApplication

fun main(args: Array<String>) {
    runApplication<BlogApplication>(*args)
}
