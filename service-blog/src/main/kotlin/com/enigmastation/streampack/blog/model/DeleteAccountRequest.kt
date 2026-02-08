/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to soft-delete a user account. Null username means self-deletion. */
data class DeleteAccountRequest(val username: String? = null)
