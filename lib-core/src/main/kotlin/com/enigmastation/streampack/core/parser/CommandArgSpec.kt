/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.parser

/**
 * Named argument slot in a [CommandPattern].
 *
 * `name` is used as the capture key in [CommandMatchResult.Match.captures].
 */
data class CommandArgSpec<T>(val name: String, val type: CommandArgType<T>)
