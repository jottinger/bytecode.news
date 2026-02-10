/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.repository

import com.enigmastation.streampack.factoid.entity.Factoid
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FactoidRepository : JpaRepository<Factoid, UUID> {
    fun findBySelectorIgnoreCase(selector: String): Factoid?

    /** Paginated listing of all factoids ordered by selector */
    fun findAllByOrderBySelectorAsc(pageable: Pageable): Page<Factoid>

    /** Paginated search across selectors */
    @Query(
        "SELECT f FROM Factoid f WHERE LOWER(f.selector) LIKE :term ORDER BY f.selector",
        countQuery = "SELECT COUNT(f) FROM Factoid f WHERE LOWER(f.selector) LIKE :term",
    )
    fun searchBySelector(@Param("term") term: String, pageable: Pageable): Page<Factoid>
}
