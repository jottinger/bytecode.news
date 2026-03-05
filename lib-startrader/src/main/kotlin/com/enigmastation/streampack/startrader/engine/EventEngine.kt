/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.engine

import com.enigmastation.streampack.startrader.config.SimulationConfig
import com.enigmastation.streampack.startrader.model.ActiveEvent
import com.enigmastation.streampack.startrader.model.Commodity
import com.enigmastation.streampack.startrader.model.UniverseState
import java.util.Random
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EventEngine(private val random: Random = Random()) {
    private val logger = LoggerFactory.getLogger(EventEngine::class.java)

    companion object {
        /** Base probability per tick that a new event spawns somewhere */
        const val EVENT_SPAWN_PROBABILITY = 0.05

        /** Max concurrent events across the universe */
        const val MAX_ACTIVE_EVENTS = 3
    }

    /**
     * Process events: apply active event effects, decrement timers, expire completed events, and
     * potentially spawn new events.
     */
    fun processEvents(state: UniverseState, config: SimulationConfig): UniverseState {
        var updatedState = applyActiveEvents(state, config)
        updatedState = decrementAndExpire(updatedState)
        updatedState = maybeSpawnEvent(updatedState, config)
        return updatedState
    }

    /** Apply active event modifiers to consumption on affected planets */
    private fun applyActiveEvents(state: UniverseState, config: SimulationConfig): UniverseState {
        if (state.activeEvents.isEmpty()) return state

        val planets = state.planets.toMutableList()
        val eventLog = state.eventLog.toMutableList()

        for (event in state.activeEvents) {
            val planetIndex = planets.indexOfFirst { it.name == event.planet }
            if (planetIndex < 0) continue

            val planet = planets[planetIndex]
            val commodity = event.event.commodity
            val supply = planet.supply.toMutableMap()
            val currentSupply = supply[commodity] ?: 0.0

            // Event increases consumption of the affected commodity
            val baseConsumptionRate = config.populationConsumptionRates[commodity] ?: 0.0
            val extraConsumption =
                planet.population * baseConsumptionRate * (event.event.consumptionMultiplier - 1.0)

            supply[commodity] = maxOf(0.0, currentSupply - extraConsumption)
            planets[planetIndex] = planet.copy(supply = supply)

            logger.debug(
                "Event {} at {}: extra consumption of {:.2f} {} ({} ticks remaining)",
                event.event.id,
                event.planet,
                extraConsumption,
                commodity.displayName,
                event.remainingTicks,
            )
        }

        return state.copy(planets = planets, eventLog = eventLog)
    }

    /** Decrement remaining ticks and remove expired events */
    private fun decrementAndExpire(state: UniverseState): UniverseState {
        val eventLog = state.eventLog.toMutableList()
        val updatedEvents =
            state.activeEvents.mapNotNull { event ->
                val remaining = event.remainingTicks - 1
                if (remaining <= 0) {
                    val msg =
                        "Tick ${state.tickCount}: ${event.event.id} at ${event.planet} has ended"
                    eventLog.add(msg)
                    logger.info(msg)
                    null
                } else {
                    event.copy(remainingTicks = remaining)
                }
            }
        return state.copy(activeEvents = updatedEvents, eventLog = eventLog)
    }

    /** Potentially spawn a new random event */
    private fun maybeSpawnEvent(state: UniverseState, config: SimulationConfig): UniverseState {
        if (state.activeEvents.size >= MAX_ACTIVE_EVENTS) return state
        if (config.events.isEmpty()) return state
        if (random.nextDouble() > EVENT_SPAWN_PROBABILITY) return state

        val event = config.events[random.nextInt(config.events.size)]
        val planet = state.planets[random.nextInt(state.planets.size)]

        // Check if this event type is already active on this planet
        val alreadyActive =
            state.activeEvents.any { it.event.id == event.id && it.planet == planet.name }
        if (alreadyActive) return state

        val duration = event.minDuration + random.nextInt(event.maxDuration - event.minDuration + 1)
        val activeEvent =
            ActiveEvent(event = event, planet = planet.name, remainingTicks = duration)

        val msg = "Tick ${state.tickCount}: ${event.id} strikes ${planet.name} (${duration} ticks)"
        logger.info(msg)

        return state.copy(
            activeEvents = state.activeEvents + activeEvent,
            eventLog = state.eventLog + msg,
        )
    }

    /** Compute the total consumption multiplier for a commodity on a specific planet */
    fun consumptionMultiplier(
        activeEvents: List<ActiveEvent>,
        planet: String,
        commodity: Commodity,
    ): Double {
        return activeEvents
            .filter { it.planet == planet && it.event.commodity == commodity }
            .fold(1.0) { acc, event -> acc * event.event.consumptionMultiplier }
    }
}
