/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.karma.service

import com.enigmastation.streampack.karma.entity.KarmaRecord
import com.enigmastation.streampack.karma.repository.KarmaRecordRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.roundToInt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KarmaService(private val repository: KarmaRecordRepository) {
    private val logger = LoggerFactory.getLogger(KarmaService::class.java)

    /** Adjusts karma for a subject by the given increment, returns the current decayed score. */
    @Transactional
    fun adjustKarma(subject: String, increment: Int): Int {
        val normalized = subject.lowercase()
        val today = LocalDate.now()
        val existing = repository.findBySubjectAndRecordDate(normalized, today)
        if (existing != null) {
            repository.save(existing.copy(delta = existing.delta + increment))
        } else {
            repository.save(
                KarmaRecord(subject = normalized, recordDate = today, delta = increment)
            )
        }
        return getKarma(subject)
    }

    /** Computes the decayed karma score for a subject. Purges records older than 1 year. */
    @Transactional
    fun getKarma(subject: String): Int {
        val normalized = subject.lowercase()
        val now = LocalDate.now()
        val cutoff = now.minusYears(1)
        repository.deleteByRecordDateBefore(cutoff)

        val records = repository.findBySubject(normalized)
        if (records.isEmpty()) return 0

        val score =
            records.sumOf { record ->
                val ageInDays = ChronoUnit.DAYS.between(record.recordDate, now)
                record.delta * exp(-0.002 * ageInDays)
            }
        return score.roundToInt()
    }

    /** Returns true if any karma records exist for the subject. */
    @Transactional(readOnly = true)
    fun hasKarma(subject: String): Boolean {
        return repository.findBySubject(subject.lowercase()).isNotEmpty()
    }
}
