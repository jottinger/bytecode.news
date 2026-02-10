/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.extensions.compress
import com.enigmastation.streampack.core.extensions.joinToStringWithAnd
import com.enigmastation.streampack.core.extensions.pluralize
import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.service.TranslatingOperation
import com.enigmastation.streampack.factoid.entity.FactoidAttribute
import com.enigmastation.streampack.factoid.model.FactoidAttributeType
import com.enigmastation.streampack.factoid.model.FactoidQueryRequest
import com.enigmastation.streampack.factoid.service.FactoidService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Catch-all factoid lookup; returns null when no factoid matches so the chain continues */
@Component
class GetFactoidOperation(private val factoidService: FactoidService) :
    TranslatingOperation<FactoidQueryRequest>(FactoidQueryRequest::class) {

    private val logger = LoggerFactory.getLogger(GetFactoidOperation::class.java)

    override val priority: Int = 90
    override val addressed: Boolean = true

    override fun translate(payload: String, message: Message<*>): FactoidQueryRequest? {
        return parseQuery(payload)
    }

    override fun handle(payload: FactoidQueryRequest, message: Message<*>): OperationOutcome? {
        return try {
            val searchResult =
                factoidService.findSelectorWithArguments(payload.selector) ?: return null
            val (selector, argument) = searchResult
            val attributes = factoidService.findBySelector(selector)
            if (attributes.isEmpty()) return null

            when (payload.attribute) {
                FactoidAttributeType.FORGET -> handleForget(selector, message)
                FactoidAttributeType.UNKNOWN -> handleSummary(selector, attributes, argument)
                FactoidAttributeType.INFO -> handleInfo(selector, attributes)
                FactoidAttributeType.LOCK -> handleLock(selector, true, message)
                FactoidAttributeType.UNLOCK -> handleLock(selector, false, message)
                else -> handleSpecificAttribute(selector, payload.attribute, attributes, argument)
            }
        } catch (e: NotEnoughArgumentsException) {
            OperationResult.Error(e.message!!)
        } catch (_: TooManyArgumentsException) {
            null
        }
    }

    /** Deletes the factoid and confirms */
    private fun handleForget(selector: String, message: Message<*>): OperationOutcome? {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val senderNick =
            message.headers["nick"] as? String ?: provenance?.user?.username ?: "unknown"
        factoidService.deleteSelector(selector)
        logger.debug("Factoid '{}' forgotten by {}", selector, senderNick)
        return OperationResult.Success("ok, forgot $selector.")
    }

    /** Renders all includeInSummary attributes in ordinal order */
    private fun handleSummary(
        selector: String,
        attributes: List<FactoidAttribute>,
        argument: String,
    ): OperationOutcome {
        val summary = attributes.summarize(selector, argument)
        return OperationResult.Success(summary)
    }

    /** Lists available attribute types and last modification info */
    private fun handleInfo(selector: String, attributes: List<FactoidAttribute>): OperationOutcome {
        val lastAttribute = attributes.sortedByDescending { it.updatedAt }.first()
        val attributeList = attributes.buildAvailableAttributeList()
        val response = buildString {
            append("The factoid for $selector has the following ")
            append("attribute".pluralize(attributes))
            append(": $attributeList")
            append(", and was last modified at ${lastAttribute.updatedAt}")
            if (lastAttribute.updatedBy != null) {
                append(" by ${lastAttribute.updatedBy}")
            }
        }
        return OperationResult.Success(response)
    }

    /** Admin-only lock/unlock toggle */
    private fun handleLock(
        selector: String,
        locked: Boolean,
        message: Message<*>,
    ): OperationOutcome {
        val provenance = message.headers[Provenance.HEADER] as? Provenance
        val role = provenance?.user?.role
        if (role != Role.ADMIN && role != Role.SUPER_ADMIN) {
            return OperationResult.Error("Lock/unlock requires admin privileges.")
        }
        val action = if (locked) "locked" else "unlocked"
        return if (factoidService.setLocked(selector, locked)) {
            OperationResult.Success("ok, $selector is now $action.")
        } else {
            OperationResult.Error("Factoid '$selector' not found.")
        }
    }

    /** Dispatches to the specific attribute type's renderer */
    private fun handleSpecificAttribute(
        selector: String,
        type: FactoidAttributeType,
        attributes: List<FactoidAttribute>,
        argument: String,
    ): OperationOutcome? {
        val attribute = attributes.firstOrNull { it.attributeType == type } ?: return null
        if (attribute.attributeValue.isNullOrEmpty()) return null

        val value =
            when (type) {
                FactoidAttributeType.TEXT -> renderTextAttribute(selector, attribute, argument)
                FactoidAttributeType.SEEALSO -> renderSeeAlso(selector, attribute)
                else -> attribute.attributeValue
            }
        return OperationResult.Success(type.render(selector, value))
    }

    /** Renders TEXT with <reply> prefix handling and $1 interpolation */
    private fun renderTextAttribute(
        selector: String,
        attribute: FactoidAttribute,
        argument: String,
    ): String {
        val value = attribute.attributeValue!!
        if (!hasPlaceholder(value) && argument.isNotEmpty()) {
            throw TooManyArgumentsException()
        }
        return replaceParameters(selector, value, argument)
    }

    /** Decorates see-also values with ~ prefix for existing factoids */
    private fun renderSeeAlso(selector: String, attribute: FactoidAttribute): String {
        return attribute.attributeValue!!
            .split(",")
            .map { it.trim() }
            .filterNot { it.equals(selector, ignoreCase = true) }
            .map {
                val clean = it.removePrefix("~")
                if (factoidService.findBySelector(clean).isNotEmpty()) {
                    "~$clean"
                } else {
                    clean
                }
            }
            .joinToString(",")
    }

    companion object {
        /** Parses a query string into a FactoidQueryRequest */
        fun parseQuery(input: String): FactoidQueryRequest {
            val compressed = input.compress()
            val lastDotIndex = compressed.lastIndexOf('.')
            return if (lastDotIndex != -1) {
                val potentialAttribute = compressed.substring(lastDotIndex + 1).trim().lowercase()
                if (potentialAttribute in FactoidAttributeType.knownAttributes) {
                    FactoidQueryRequest(
                        compressed.substring(0, lastDotIndex).trim(),
                        FactoidAttributeType.knownAttributes[potentialAttribute]!!,
                    )
                } else {
                    FactoidQueryRequest(compressed, FactoidAttributeType.UNKNOWN)
                }
            } else {
                FactoidQueryRequest(compressed, FactoidAttributeType.UNKNOWN)
            }
        }

        fun hasPlaceholder(value: String): Boolean = "\$1" in value

        fun replaceParameters(selector: String, value: String, argument: String): String {
            if (hasPlaceholder(value) && argument.isEmpty()) {
                throw NotEnoughArgumentsException(
                    "$selector: Not enough arguments to replace placeholder."
                )
            }
            return value.replace("\$1", argument)
        }
    }
}

/** Summarizes all includeInSummary attributes in enum ordinal order */
fun List<FactoidAttribute>.summarize(selector: String, argument: String): String {
    return this.filter { it.attributeType.includeInSummary }
        .sortedBy { it.attributeType.ordinal }
        .filter { !it.attributeValue.isNullOrEmpty() }
        .mapNotNull { attr ->
            val value =
                when (attr.attributeType) {
                    FactoidAttributeType.TEXT -> {
                        if (
                            !GetFactoidOperation.hasPlaceholder(attr.attributeValue!!) &&
                                argument.isNotEmpty()
                        ) {
                            throw TooManyArgumentsException()
                        }
                        GetFactoidOperation.replaceParameters(
                            selector,
                            attr.attributeValue,
                            argument,
                        )
                    }
                    else -> attr.attributeValue
                }
            val rendered = attr.attributeType.render(selector, value)
            rendered.ifEmpty { null }
        }
        .joinToString(" ")
        .compress()
}

/** Builds a human-readable list of available attribute types */
fun List<FactoidAttribute>.buildAvailableAttributeList(): String {
    return this.filter { !it.attributeValue.isNullOrEmpty() }
        .map { attr ->
            val values =
                when (attr.attributeType) {
                    FactoidAttributeType.URLS,
                    FactoidAttributeType.TAGS,
                    FactoidAttributeType.LANGUAGES -> attr.attributeValue!!.split(",")
                    else -> listOf(attr.attributeValue)
                }
            attr.attributeType.toString().lowercase().pluralize(values)
        }
        .joinToStringWithAnd()
}

class NotEnoughArgumentsException(message: String) : Exception(message)

class TooManyArgumentsException : Exception()
