/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.blog.operation

import com.enigmastation.streampack.blog.entity.Category
import com.enigmastation.streampack.blog.model.ContentOperationConfirmation
import com.enigmastation.streampack.blog.model.SoftDeleteCategoryRequest
import com.enigmastation.streampack.blog.repository.CategoryRepository
import com.enigmastation.streampack.core.entity.User
import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import com.enigmastation.streampack.core.repository.UserRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class SoftDeleteCategoryOperationTests {

    @Autowired lateinit var eventGateway: EventGateway
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var categoryRepository: CategoryRepository

    private lateinit var adminUser: User
    private lateinit var regularUser: User
    private lateinit var category: Category

    @BeforeEach
    fun setUp() {
        adminUser =
            userRepository.save(
                User(
                    username = "catdeladmin",
                    email = "catdeladmin@test.com",
                    displayName = "Admin",
                    emailVerified = true,
                    role = Role.ADMIN,
                )
            )
        regularUser =
            userRepository.save(
                User(
                    username = "catdeluser",
                    email = "catdeluser@test.com",
                    displayName = "User",
                    emailVerified = true,
                    role = Role.USER,
                )
            )
        category = categoryRepository.save(Category(name = "ToDelete", slug = "to-delete"))
    }

    private fun createMessage(request: SoftDeleteCategoryRequest, user: User?) =
        MessageBuilder.withPayload(request)
            .setHeader(
                Provenance.HEADER,
                Provenance(
                    protocol = Protocol.HTTP,
                    serviceId = "blog-service",
                    replyTo = "admin/categories/delete",
                    user =
                        user?.let {
                            UserPrincipal(
                                id = it.id,
                                username = it.username,
                                displayName = it.displayName,
                                role = it.role,
                            )
                        },
                ),
            )
            .build()

    @Test
    fun `admin soft-deletes category`() {
        val request = SoftDeleteCategoryRequest(category.id)
        val result = eventGateway.process(createMessage(request, adminUser))

        assertInstanceOf(OperationResult.Success::class.java, result)
        val confirmation =
            (result as OperationResult.Success).payload as ContentOperationConfirmation
        assertEquals("Category deleted", confirmation.message)

        val updated = categoryRepository.findById(category.id).orElse(null)
        assertTrue(updated.deleted)
    }

    @Test
    fun `non-admin rejected`() {
        val request = SoftDeleteCategoryRequest(category.id)
        val result = eventGateway.process(createMessage(request, regularUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Admin access required", (result as OperationResult.Error).message)
    }

    @Test
    fun `nonexistent category returns error`() {
        val request = SoftDeleteCategoryRequest(UUID.randomUUID())
        val result = eventGateway.process(createMessage(request, adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Category not found", (result as OperationResult.Error).message)
    }

    @Test
    fun `already-deleted category returns error`() {
        val deletedCat = categoryRepository.save(category.copy(deleted = true))
        val request = SoftDeleteCategoryRequest(deletedCat.id)
        val result = eventGateway.process(createMessage(request, adminUser))

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals("Category is already deleted", (result as OperationResult.Error).message)
    }
}
