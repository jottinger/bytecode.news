package com.enigmastation.streampack.startrader.config

data class NpcDampeningConfig(
    val baseFireProbability: Double,
    val priceDeviationMultiplier: Double,
    val maxAdjustmentFraction: Double,
    val minimumReferenceSupply: Double = 25.0,
)
