/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/** Provides shared security infrastructure beans */
@Configuration
class SecurityConfiguration {

    @Bean fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()
}
