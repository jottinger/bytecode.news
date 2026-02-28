/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.features.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Test-only security configuration for service-features. In production, the app module provides a
 * SecurityFilterChain via service-blog. This bean enables isolated testing without that dependency.
 */
@Configuration
class TestSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
