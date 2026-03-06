/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.config

import com.enigmastation.streampack.startrader.model.Commodity

data class PlanetConfig(
    val name: String,
    val fixed: Boolean = false,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val production: Map<Commodity, Double>,
    val population: Double,
)
