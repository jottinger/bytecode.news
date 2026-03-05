/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.config

import com.enigmastation.streampack.startrader.model.Commodity
import com.enigmastation.streampack.startrader.model.EconomicEvent
import com.enigmastation.streampack.startrader.model.ProductionMatrix

data class SimulationConfig(
    val commodityBasePrices: Map<Commodity, Double>,
    val productionMatrix: ProductionMatrix,
    val planets: List<PlanetConfig>,
    val npcDampening: NpcDampeningConfig,
    val events: List<EconomicEvent>,
    val populationConsumptionRates: Map<Commodity, Double>,
)
