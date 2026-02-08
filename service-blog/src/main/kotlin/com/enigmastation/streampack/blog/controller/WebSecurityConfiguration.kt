/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Minimal HTTP security configuration for the REST API.
 *
 * Authentication and authorization are handled at the operation layer, not the HTTP filter layer.
 * This chain disables CSRF (stateless API), enforces stateless sessions, and permits all requests.
 * When authenticated endpoints arrive later, a JWT filter will be added here.
 */
@Configuration
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
