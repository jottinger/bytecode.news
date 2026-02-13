/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.controller

import com.enigmastation.streampack.blog.model.CategorySummary
import com.enigmastation.streampack.blog.repository.CategoryRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for public category listing */
@RestController
@Tag(name = "Categories")
class CategoryController(private val categoryRepository: CategoryRepository) {

    @Operation(summary = "List active categories")
    @ApiResponse(
        responseCode = "200",
        description = "List of active categories",
        content =
            [Content(array = ArraySchema(schema = Schema(implementation = CategorySummary::class)))],
    )
    @GetMapping("/categories")
    fun listCategories(): ResponseEntity<List<CategorySummary>> {
        val categories =
            categoryRepository.findActive().map { category ->
                CategorySummary(
                    id = category.id,
                    name = category.name,
                    slug = category.slug,
                    parentName = category.parent?.name,
                )
            }
        return ResponseEntity.ok(categories)
    }
}
