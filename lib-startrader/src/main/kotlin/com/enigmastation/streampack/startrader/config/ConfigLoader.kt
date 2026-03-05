/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component

@Component
class ConfigLoader {
    private val objectMapper = jacksonObjectMapper()

    /** Load simulation config from classpath resource */
    fun load(resourcePath: String = "universe-config.json"): SimulationConfig {
        val stream =
            javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalArgumentException("Config resource not found: $resourcePath")

        val raw: RawConfig = objectMapper.readValue(stream)
        return raw.toSimulationConfig()
    }
}
