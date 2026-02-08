/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Provides OpenAPI metadata and security scheme definitions for springdoc */
@Configuration
class OpenApiConfiguration {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("jvm.news API")
                    .version("0.1.0")
                    .description("Content hub and knowledge management for the JVM ecosystem")
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT"),
                    )
            )
}
