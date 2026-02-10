/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.service

import com.enigmastation.streampack.factoid.entity.Factoid
import com.enigmastation.streampack.factoid.entity.FactoidAttribute
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.repository.FactoidAttributeRepository
import com.enigmastation.streampack.factoid.repository.FactoidRepository
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Manages factoid persistence with lock enforcement and progressive selector search */
@Service
class FactoidService(
    private val factoidRepository: FactoidRepository,
    private val factoidAttributeRepository: FactoidAttributeRepository,
) {
    private val logger = LoggerFactory.getLogger(FactoidService::class.java)

    /** Returns all attributes for a given selector */
    @Transactional(readOnly = true)
    fun findBySelector(selector: String): List<FactoidAttribute> {
        return factoidAttributeRepository.findByFactoidSelectorIgnoreCase(selector)
    }

    /** Returns a specific attribute for a selector, or null */
    @Transactional(readOnly = true)
    fun findBySelectorAndType(selector: String, type: FactoidAttributeType): FactoidAttribute? {
        return factoidAttributeRepository.findByFactoidSelectorIgnoreCaseAndAttributeType(
            selector,
            type,
        )
    }

    /** Upserts a factoid attribute; creates the parent factoid if needed; respects lock */
    @Transactional
    fun save(
        selector: String,
        type: FactoidAttributeType,
        value: String,
        updatedBy: String?,
    ): SaveResult {
        val normalized = selector.lowercase()
        val now = Instant.now()
        val existingFactoid = factoidRepository.findBySelectorIgnoreCase(normalized)

        if (existingFactoid != null && existingFactoid.locked) {
            logger.debug("Factoid '{}' is locked, rejecting update", normalized)
            return SaveResult.Locked(normalized)
        }

        val factoid =
            if (existingFactoid != null) {
                factoidRepository.save(existingFactoid.copy(updatedBy = updatedBy, updatedAt = now))
            } else {
                factoidRepository.save(
                    Factoid(
                        selector = normalized,
                        locked = false,
                        updatedBy = updatedBy,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }

        val existingAttribute =
            factoidAttributeRepository.findByFactoidSelectorIgnoreCaseAndAttributeType(
                normalized,
                type,
            )

        if (existingAttribute != null) {
            factoidAttributeRepository.save(
                existingAttribute.copy(
                    attributeValue = value,
                    updatedBy = updatedBy,
                    updatedAt = now,
                )
            )
        } else {
            factoidAttributeRepository.save(
                FactoidAttribute(
                    factoid = factoid,
                    attributeType = type,
                    attributeValue = value,
                    updatedBy = updatedBy,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
        return SaveResult.Ok
    }

    /** Deletes a factoid and all its attributes */
    @Transactional
    fun deleteSelector(selector: String) {
        val factoid = factoidRepository.findBySelectorIgnoreCase(selector)
        if (factoid != null) {
            val attributes = factoidAttributeRepository.findByFactoidSelectorIgnoreCase(selector)
            factoidAttributeRepository.deleteAll(attributes)
            factoidAttributeRepository.flush()
            factoidRepository.delete(factoid)
            factoidRepository.flush()
        }
    }

    /** Toggles the lock flag on a factoid */
    @Transactional
    fun setLocked(selector: String, locked: Boolean): Boolean {
        val factoid = factoidRepository.findBySelectorIgnoreCase(selector)
        if (factoid != null) {
            factoidRepository.save(factoid.copy(locked = locked, updatedAt = Instant.now()))
            return true
        }
        return false
    }

    /**
     * Progressive search: tries longest match first, returning the matched selector and leftover
     * arguments. For "ask joe about kotlin", tries "ask joe about kotlin", then "ask joe about",
     * then "ask joe", then "ask".
     */
    @Transactional(readOnly = true)
    fun findSelectorWithArguments(query: String): Pair<String, String>? {
        val components = query.split(" ")
        for (i in components.indices) {
            val searchSelector = components.take(components.size - i).joinToString(" ")
            val match = factoidRepository.findBySelectorIgnoreCase(searchSelector)
            if (match != null) {
                val extras = components.drop(components.size - i)
                return Pair(match.selector, extras.joinToString(" "))
            }
        }
        return null
    }

    /** LIKE search across selectors, attribute values, and updatedBy */
    @Transactional(readOnly = true)
    fun searchForTerm(term: String): List<String> {
        return factoidAttributeRepository.searchForTerm("%${term.lowercase()}%")
    }

    /** Paginated listing of all factoids */
    @Transactional(readOnly = true)
    fun findAll(pageable: Pageable): Page<Factoid> {
        return factoidRepository.findAllByOrderBySelectorAsc(pageable)
    }

    /** Paginated search across factoid selectors */
    @Transactional(readOnly = true)
    fun searchPaginated(term: String, pageable: Pageable): Page<Factoid> {
        return factoidRepository.searchBySelector("%${term.lowercase()}%", pageable)
    }

    /** Result of a save attempt */
    sealed interface SaveResult {
        data object Ok : SaveResult

        data class Locked(val selector: String) : SaveResult
    }
}
