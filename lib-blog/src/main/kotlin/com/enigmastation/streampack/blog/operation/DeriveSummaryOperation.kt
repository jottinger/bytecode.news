/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.model.DeriveSummaryRequest
import com.enigmastation.streampack.blog.model.DeriveSummaryResponse
import com.enigmastation.streampack.blog.service.MarkdownRenderingService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Derives a non-persistent heuristic summary for editor preview. */
@Component
class DeriveSummaryOperation(private val markdownRenderingService: MarkdownRenderingService) :
    TypedOperation<DeriveSummaryRequest>(DeriveSummaryRequest::class) {

    override fun handle(payload: DeriveSummaryRequest, message: Message<*>): OperationOutcome {
        val title = payload.title.trim()
        val markdown = payload.markdownSource.trim()
        if (title.isBlank()) return OperationResult.Error("Title is required")
        if (markdown.isBlank()) return OperationResult.Error("Content is required")

        val summary = markdownRenderingService.excerpt(markdown).ifBlank { title }
        return OperationResult.Success(DeriveSummaryResponse(summary))
    }
}
