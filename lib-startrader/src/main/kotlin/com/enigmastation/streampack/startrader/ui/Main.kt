/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.ui

import com.enigmastation.streampack.startrader.config.ConfigLoader
import com.enigmastation.streampack.startrader.engine.EventEngine
import com.enigmastation.streampack.startrader.engine.NpcDampeningEngine
import com.enigmastation.streampack.startrader.engine.PriceEngine
import com.enigmastation.streampack.startrader.engine.ProductionEngine
import com.enigmastation.streampack.startrader.engine.SimulationEngine
import com.enigmastation.streampack.startrader.engine.UniverseSeeder
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
    // Wire up components manually (no Spring context needed for the UI harness)
    val configLoader = ConfigLoader()
    val productionEngine = ProductionEngine()
    val npcDampeningEngine = NpcDampeningEngine()
    val eventEngine = EventEngine()
    val priceEngine = PriceEngine()
    val simulationEngine =
        SimulationEngine(productionEngine, npcDampeningEngine, eventEngine, priceEngine)
    val seeder = UniverseSeeder()

    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val frame = StarTraderFrame(simulationEngine, seeder, configLoader)
        frame.isVisible = true
    }
}
