/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.config

import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

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
