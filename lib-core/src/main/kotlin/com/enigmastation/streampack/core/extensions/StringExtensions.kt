/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.extensions

/** Trims and collapses internal whitespace runs to single spaces */
fun String.compress(): String = this.trim().replace(Regex("\\s+"), " ")

/** Returns true if the last character is sentence-ending punctuation */
fun String.endsWithPunctuation(): Boolean {
    if (this.isEmpty()) return false
    return this.last() in ".!?;:"
}
