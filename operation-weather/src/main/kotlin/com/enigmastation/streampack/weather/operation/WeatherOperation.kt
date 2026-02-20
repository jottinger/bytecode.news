/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.weather.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.weather.model.WeatherRequest
import com.enigmastation.streampack.weather.service.WeatherService
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Handles "weather <location>" commands and typed WeatherRequest payloads */
@Component
class WeatherOperation(private val weatherService: WeatherService) :
    TranslatingOperation<WeatherRequest>(WeatherRequest::class) {

    override val addressed: Boolean = true
    override val operationGroup: String = "weather"

    override fun translate(payload: String, message: Message<*>): WeatherRequest? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("weather ", ignoreCase = true)) return null
        val location = trimmed.substring("weather ".length).trim()
        if (location.isBlank()) return null
        return WeatherRequest(location)
    }

    override fun handle(payload: WeatherRequest, message: Message<*>): OperationOutcome? {
        logger.debug("Received weather request for {}", payload)
        val result = weatherService.getWeather(payload.location) ?: return null

        val text =
            "The weather for ${result.location} is " +
                "${result.tempCelsius}C (${result.tempFahrenheit}F), " +
                "and is described as \"${result.description}\""

        return OperationResult.Success(text)
    }
}
