/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.startrader.store

import com.enigmastation.streampack.startrader.model.UniverseState

interface UniverseStateStore {
    fun save(state: UniverseState)

    fun load(): UniverseState?
}
