/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.model

data class EconomicEvent(
    val id: String,
    val commodity: Commodity,
    val consumptionMultiplier: Double,
    val minDuration: Int,
    val maxDuration: Int,
)
