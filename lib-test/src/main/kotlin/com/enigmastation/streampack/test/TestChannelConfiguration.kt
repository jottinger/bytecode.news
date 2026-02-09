/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.test

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.integration.channel.DirectChannel

/**
 * Overrides the ingress channel with a DirectChannel so operations run on the test thread. This
 * allows @Transactional test rollback to work correctly -- the production ExecutorChannel
 * dispatches to virtual threads which cannot see uncommitted test data.
 */
@Configuration
class TestChannelConfiguration {

    @Bean
    @Primary
    fun ingressChannel(): DirectChannel {
        return DirectChannel()
    }
}
