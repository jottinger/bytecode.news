/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.ai.service

import com.enigmastation.streampack.ai.config.AiProperties
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel

/** Thin wrapper around Spring AI ChatModel for prompt-based text generation */
open class AiService(private val chatModel: ChatModel, private val properties: AiProperties) {
    private val logger = LoggerFactory.getLogger(AiService::class.java)

    /** Sends a system instruction and user prompt to the model, returns the response text */
    open fun prompt(systemInstruction: String, userPrompt: String): String? {
        return try {
            chatModel.call(SystemMessage(systemInstruction), UserMessage(userPrompt))
        } catch (e: Exception) {
            logger.error("AI prompt failed: {}", e.message)
            null
        }
    }
}
