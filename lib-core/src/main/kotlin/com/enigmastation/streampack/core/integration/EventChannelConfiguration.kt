/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.integration

import java.util.concurrent.Executors
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.annotation.IntegrationComponentScan
import org.springframework.integration.channel.ExecutorChannel

/** Configures the event system channels and enables discovery of SI gateway interfaces */
@Configuration
@IntegrationComponentScan(basePackageClasses = [EventGateway::class])
class EventChannelConfiguration {

    /** Ingress channel backed by virtual threads for scalable operation processing */
    @Bean
    @ConditionalOnMissingBean(name = ["ingressChannel"])
    fun ingressChannel(): ExecutorChannel {
        return ExecutorChannel(Executors.newVirtualThreadPerTaskExecutor())
    }
}
