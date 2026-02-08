/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.repository

import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.model.Role
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, UUID> {
    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u WHERE u.deleted = false") fun findActive(): List<User>

    @Query(
        "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.role = :role AND u.deleted = false"
    )
    fun hasActiveWithRole(role: Role): Boolean

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleted = false")
    fun findActiveById(id: UUID): User?
}
