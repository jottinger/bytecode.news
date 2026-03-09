/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.parser

/** Pass-through argument type that accepts any single token. */
data object StringArgType : CommandArgType<String> {
    override fun parse(token: String): String = token
}
