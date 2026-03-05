/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.ui

import com.enigmastation.streampack.startrader.model.Commodity
import com.enigmastation.streampack.startrader.model.UniverseState
import javax.swing.table.AbstractTableModel

class PriceTableModel : AbstractTableModel() {
    private var state: UniverseState? = null
    private var previousPrices: Map<String, Map<Commodity, Double>> = emptyMap()
    private val commodities = Commodity.entries.toList()

    fun updateState(newState: UniverseState) {
        // Capture previous prices before updating
        state?.let { prev -> previousPrices = prev.planets.associate { it.name to it.prices } }
        state = newState
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = state?.planets?.size ?: 0

    override fun getColumnCount(): Int = commodities.size + 1

    override fun getColumnName(column: Int): String =
        if (column == 0) "Planet" else commodities[column - 1].displayName

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val planet = state?.planets?.get(rowIndex) ?: return ""
        if (columnIndex == 0) return planet.name
        val commodity = commodities[columnIndex - 1]
        val price = planet.prices[commodity] ?: 0.0
        return String.format("%.1f", price)
    }

    /** Returns price change direction: positive if price rose, negative if fell, 0 if unchanged */
    fun priceChange(row: Int, col: Int): Double {
        if (col == 0) return 0.0
        val planet = state?.planets?.get(row) ?: return 0.0
        val commodity = commodities[col - 1]
        val currentPrice = planet.prices[commodity] ?: 0.0
        val previousPrice = previousPrices[planet.name]?.get(commodity) ?: currentPrice
        return currentPrice - previousPrice
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
}
