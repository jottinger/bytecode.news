/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.core.operation

import com.enigmastation.streampack.core.model.OperationOutcome
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.service.TypedOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/** Reports build identity: commit, branch, and build time. */
@Component
class VersionOperation(
    @Autowired(required = false) private val gitProperties: GitProperties?,
    @Autowired(required = false) private val buildProperties: BuildProperties?,
) : TypedOperation<String>(String::class) {

    override val operationGroup: String = "version"

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        return payload.trim().equals("version", ignoreCase = true)
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        return OperationResult.Success(buildVersionString())
    }

    /** Assemble a human-readable version string from available build metadata */
    fun buildVersionString(): String {
        val parts = mutableListOf<String>()

        val name = buildProperties?.name ?: "Nevet"
        val version = buildProperties?.version
        parts.add(if (version != null) "$name $version" else name)

        val commit = gitProperties?.shortCommitId
        val branch = gitProperties?.branch
        if (commit != null) {
            parts.add(if (branch != null) "$commit ($branch)" else commit)
        }

        val buildTime = buildProperties?.time ?: gitProperties?.commitTime
        if (buildTime != null) {
            parts.add("Built $buildTime")
        }

        if (parts.size == 1 && version == null) {
            parts.add("development build")
        }

        return parts.joinToString(" | ")
    }
}
