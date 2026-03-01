/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Category
import com.enigmastation.streampack.blog.model.CreateCategoryRequest
import com.enigmastation.streampack.blog.model.CreateCategoryResponse
import com.enigmastation.streampack.blog.repository.CategoryRepository
import com.enigmastation.streampack.blog.service.SlugGenerationService
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Admin creates a new content category */
@Component
class CreateCategoryOperation(
    private val categoryRepository: CategoryRepository,
    private val slugGenerationService: SlugGenerationService,
) : TypedOperation<CreateCategoryRequest>(CreateCategoryRequest::class) {

    override val priority = 50

    override fun handle(payload: CreateCategoryRequest, message: Message<*>): OperationOutcome {
        val provenance =
            message.headers[Provenance.HEADER] as? Provenance
                ?: return OperationResult.Error("No provenance context")

        val principal = provenance.user ?: return OperationResult.Error("Authentication required")

        if (principal.role != Role.ADMIN && principal.role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Admin access required")
        }

        val name = payload.name.trim()
        if (name.isBlank()) {
            return OperationResult.Error("Category name is required")
        }

        val existing = categoryRepository.findByName(name)
        if (existing != null) {
            return OperationResult.Error("Category name already exists")
        }

        val parent =
            if (payload.parentId != null) {
                val p =
                    categoryRepository.findById(payload.parentId).orElse(null)
                        ?: return OperationResult.Error("Parent category not found")
                if (p.deleted) {
                    return OperationResult.Error("Parent category not found")
                }
                p
            } else {
                null
            }

        val slug = slugGenerationService.slugify(name)
        val category = categoryRepository.save(Category(name = name, slug = slug, parent = parent))

        logger.info("Category created: {} ({})", category.name, category.id)

        return OperationResult.Success(
            CreateCategoryResponse(
                id = category.id,
                name = category.name,
                slug = category.slug,
                parentName = parent?.name,
            )
        )
    }
}
