/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.config

import com.enigmastation.streampack.startrader.model.Commodity
import com.enigmastation.streampack.startrader.model.EconomicEvent
import com.enigmastation.streampack.startrader.model.ProductionMatrix
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class ConfigLoader(private val objectMapper: ObjectMapper) {

    /** Load simulation config from classpath resource */
    fun load(resourcePath: String = "universe-config.json"): SimulationConfig {
        val stream =
            javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalArgumentException("Config resource not found: $resourcePath")

        val root = objectMapper.readTree(stream)
        return parseConfig(root)
    }

    private fun parseConfig(root: JsonNode): SimulationConfig {
        val basePrices = parseCommodityBasePrices(root["commodities"])
        val matrix = parseProductionMatrix(root["productionMatrix"])
        val planets = parsePlanets(root["planets"])
        val npcDampening = parseNpcDampening(root["npcDampening"])
        val events = parseEvents(root["events"])
        val consumptionRates = parseConsumptionRates(root["populationConsumptionRates"])

        return SimulationConfig(
            commodityBasePrices = basePrices,
            productionMatrix = matrix,
            planets = planets,
            npcDampening = npcDampening,
            events = events,
            populationConsumptionRates = consumptionRates,
        )
    }

    private fun parseCommodityBasePrices(node: JsonNode): Map<Commodity, Double> =
        node.associate { Commodity.valueOf(it["id"].asText()) to it["basePrice"].asDouble() }

    private fun parseProductionMatrix(node: JsonNode): ProductionMatrix {
        val inputs = mutableMapOf<Commodity, Map<Commodity, Double>>()
        node.fields().forEach { (outputName, inputNode) ->
            val output = Commodity.valueOf(outputName)
            val inputMap = mutableMapOf<Commodity, Double>()
            inputNode.fields().forEach { (inputName, rate) ->
                inputMap[Commodity.valueOf(inputName)] = rate.asDouble()
            }
            inputs[output] = inputMap
        }
        return ProductionMatrix(inputs)
    }

    private fun parsePlanets(node: JsonNode): List<PlanetConfig> =
        node.map { planet ->
            val production = mutableMapOf<Commodity, Double>()
            planet["production"].fields().forEach { (name, rate) ->
                production[Commodity.valueOf(name)] = rate.asDouble()
            }
            PlanetConfig(
                name = planet["name"].asText(),
                fixed = planet["fixed"]?.asBoolean() ?: false,
                x = planet["x"]?.asDouble(),
                y = planet["y"]?.asDouble(),
                z = planet["z"]?.asDouble(),
                production = production,
                population = planet["population"].asDouble(),
            )
        }

    private fun parseNpcDampening(node: JsonNode): NpcDampeningConfig =
        NpcDampeningConfig(
            baseFireProbability = node["baseFireProbability"].asDouble(),
            priceDeviationMultiplier = node["priceDeviationMultiplier"].asDouble(),
            maxAdjustmentFraction = node["maxAdjustmentFraction"].asDouble(),
        )

    private fun parseEvents(node: JsonNode): List<EconomicEvent> =
        node.map {
            EconomicEvent(
                id = it["id"].asText(),
                commodity = Commodity.valueOf(it["commodity"].asText()),
                consumptionMultiplier = it["consumptionMultiplier"].asDouble(),
                minDuration = it["minDuration"].asInt(),
                maxDuration = it["maxDuration"].asInt(),
            )
        }

    private fun parseConsumptionRates(node: JsonNode): Map<Commodity, Double> {
        val rates = mutableMapOf<Commodity, Double>()
        node.fields().forEach { (name, rate) -> rates[Commodity.valueOf(name)] = rate.asDouble() }
        return rates
    }
}
