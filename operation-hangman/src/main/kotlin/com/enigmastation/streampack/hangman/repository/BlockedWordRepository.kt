/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.hangman.repository

import com.enigmastation.streampack.hangman.entity.BlockedWord
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface BlockedWordRepository : JpaRepository<BlockedWord, UUID> {
    fun existsByWord(word: String): Boolean

    fun findByWord(word: String): BlockedWord?

    fun deleteByWord(word: String)
}
