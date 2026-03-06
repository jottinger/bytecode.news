/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.config

import com.enigmastation.streampack.startrader.model.Commodity
import com.enigmastation.streampack.startrader.model.EconomicEvent
import com.enigmastation.streampack.startrader.model.ProductionMatrix

/** Intermediate DTO matching the JSON shape; productionMatrix is a flat map in JSON */
data class RawConfig(
    val commodityBasePrices: Map<Commodity, Double>,
    val productionMatrix: Map<Commodity, Map<Commodity, Double>>,
    val planets: List<PlanetConfig>,
    val npcDampening: NpcDampeningConfig,
    val events: List<EconomicEvent>,
    val populationConsumptionRates: Map<Commodity, Double>,
) {
    fun toSimulationConfig() =
        SimulationConfig(
            commodityBasePrices = commodityBasePrices,
            productionMatrix = ProductionMatrix(productionMatrix),
            planets = planets,
            npcDampening = npcDampening,
            events = events,
            populationConsumptionRates = populationConsumptionRates,
        )
}
