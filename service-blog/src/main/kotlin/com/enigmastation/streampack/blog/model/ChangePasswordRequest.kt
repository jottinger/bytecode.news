/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.model

/** Request to change the authenticated user's password */
data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)
