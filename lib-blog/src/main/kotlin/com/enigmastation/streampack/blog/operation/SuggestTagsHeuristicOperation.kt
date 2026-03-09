/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.SuggestTagsRequest
import com.enigmastation.streampack.blog.model.SuggestTagsResponse
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.service.TypedOperation
import com.enigmastation.streampack.taxonomy.model.FindTaxonomySnapshotRequest
import com.enigmastation.streampack.taxonomy.model.TaxonomySnapshot
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Non-AI heuristic tag suggester for draft content. Uses title/body token scoring plus known
 * taxonomy tags.
 */
@Component
class SuggestTagsHeuristicOperation(private val eventGateway: EventGateway) :
    TypedOperation<SuggestTagsRequest>(SuggestTagsRequest::class) {

    override fun handle(payload: SuggestTagsRequest, message: Message<*>): OperationOutcome {
        val title = payload.title.trim()
        val markdown = payload.markdownSource.trim()
        if (title.isBlank()) return OperationResult.Error("Title is required")
        if (markdown.isBlank()) return OperationResult.Error("Content is required")

        val titleLower = title.lowercase()
        val bodyLower = markdown.lowercase()
        val allText = "$titleLower\n$bodyLower"

        val knownTags = findKnownTags(message.headers[Provenance.HEADER] as? Provenance)
        val existing = payload.existingTags.mapNotNull { normalizeTag(it) }

        val hashtags =
            Regex("""#([a-zA-Z0-9][a-zA-Z0-9+._-]{1,40})""")
                .findAll(allText)
                .mapNotNull { normalizeTag(it.groupValues[1]) }
                .toSet()

        val words =
            Regex("""[a-z0-9][a-z0-9+._-]{1,40}""").findAll(allText).map { it.value }.toList()
        val frequencies = words.groupingBy { it }.eachCount()

        val candidates = mutableSetOf<String>()
        candidates.addAll(existing)
        candidates.addAll(hashtags)
        candidates.addAll(knownTags)
        candidates.addAll(
            frequencies.entries
                .asSequence()
                .filter { (token, count) -> count >= 2 && token !in STOPWORDS }
                .map { it.key }
                .take(40)
                .toList()
        )

        val scored =
            candidates
                .map { tag ->
                    tag to
                        scoreTag(
                            tag,
                            titleLower,
                            bodyLower,
                            frequencies,
                            knownTags,
                            existing,
                            hashtags,
                        )
                }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }
                )
                .map { it.first }
                .take(10)

        return OperationResult.Success(SuggestTagsResponse(scored))
    }

    private fun findKnownTags(provenance: Provenance?): Set<String> {
        if (provenance == null) return emptySet()
        val message =
            MessageBuilder.withPayload(FindTaxonomySnapshotRequest as Any)
                .setHeader(Provenance.HEADER, provenance)
                .build()

        return when (val result = eventGateway.process(message)) {
            is OperationResult.Success -> {
                val snapshot = result.payload as? TaxonomySnapshot
                snapshot?.aggregate?.keys?.mapNotNull(::normalizeTag)?.toSet().orEmpty()
            }
            else -> emptySet()
        }
    }

    private fun scoreTag(
        tag: String,
        titleLower: String,
        bodyLower: String,
        frequencies: Map<String, Int>,
        knownTags: Set<String>,
        existing: List<String>,
        hashtags: Set<String>,
    ): Int {
        var score = 0

        if (tag in existing) score += 100
        if (tag in hashtags) score += 60
        if (containsExact(titleLower, tag)) score += 40
        if (containsExact(bodyLower, tag)) score += 20

        score += (frequencies[tag] ?: 0) * 6

        if (tag in knownTags) score += 8
        if (tag in STOPWORDS) score -= 50

        return score
    }

    private fun containsExact(text: String, token: String): Boolean {
        val escaped = Regex.escape(token)
        return Regex("""(?<![a-z0-9])$escaped(?![a-z0-9])""").containsMatchIn(text)
    }

    private fun normalizeTag(raw: String): String? {
        val cleaned = raw.trim().lowercase().removePrefix("#")
        if (cleaned.isBlank()) return null
        if (cleaned.startsWith("_")) return null
        if (cleaned.length < 2) return null
        return cleaned
    }

    companion object {
        private val STOPWORDS =
            setOf(
                "the",
                "and",
                "that",
                "this",
                "with",
                "from",
                "have",
                "your",
                "into",
                "about",
                "what",
                "when",
                "where",
                "which",
                "while",
                "just",
                "also",
                "then",
                "than",
                "they",
                "them",
                "their",
                "there",
                "were",
                "been",
                "being",
                "very",
                "some",
                "more",
                "most",
                "many",
                "much",
                "only",
                "like",
                "does",
                "dont",
                "did",
                "can",
                "could",
                "should",
                "would",
                "will",
                "you",
                "for",
                "are",
                "not",
                "use",
                "using",
            )
    }
}
