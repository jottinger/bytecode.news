/* Joseph B. Ottinger (C)2026 */
package com.enigmastation.streampack.factoid.operation

import com.enigmastation.streampack.core.integration.EventGateway
import com.enigmastation.streampack.core.model.OperationResult
import com.enigmastation.streampack.core.model.Protocol
import com.enigmastation.streampack.core.model.Provenance
import com.enigmastation.streampack.core.model.Role
import com.enigmastation.streampack.core.model.UserPrincipal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FactoidOperationTests {

    @Autowired lateinit var eventGateway: EventGateway

    private fun provenance(role: Role = Role.USER) =
        Provenance(
            protocol = Protocol.CONSOLE,
            serviceId = "",
            replyTo = "local",
            user =
                UserPrincipal(
                    id = UUID.randomUUID(),
                    username = "testuser",
                    displayName = "Test User",
                    role = role,
                ),
        )

    private fun msg(text: String, nick: String = "testuser", role: Role = Role.USER) =
        MessageBuilder.withPayload(text)
            .setHeader(Provenance.HEADER, provenance(role))
            .setHeader("nick", nick)
            .build()

    private fun assertSuccess(result: Any?, expected: String) {
        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals(expected, (result as OperationResult.Success).payload)
    }

    private fun assertError(result: Any?) {
        assertInstanceOf(OperationResult.Error::class.java, result)
    }

    // -- Set and Get TEXT --

    @Test
    fun `set TEXT factoid returns confirmation`() {
        val result = eventGateway.process(msg("spring=A Java framework"))
        assertSuccess(result, "ok, testuser: updated spring.")
    }

    @Test
    fun `get TEXT factoid returns formatted response`() {
        eventGateway.process(msg("spring=A Java framework"))
        val result = eventGateway.process(msg("spring"))
        assertSuccess(result, "spring is A Java framework.")
    }

    // -- Set and Get URL --

    @Test
    fun `set URL attribute returns confirmation`() {
        eventGateway.process(msg("spring=A Java framework"))
        val result = eventGateway.process(msg("spring.url=https://spring.io"))
        assertSuccess(result, "ok, testuser: updated spring.")
    }

    @Test
    fun `get URL attribute renders correctly`() {
        eventGateway.process(msg("spring=A Java framework"))
        eventGateway.process(msg("spring.url=https://spring.io"))
        val result = eventGateway.process(msg("spring.url"))
        assertSuccess(result, "URL: https://spring.io")
    }

    // -- MAVEN attribute --

    @Test
    fun `set MAVEN attribute returns confirmation`() {
        eventGateway.process(msg("spring=A Java framework"))
        val result = eventGateway.process(msg("spring.maven=org.springframework:spring-core"))
        assertSuccess(result, "ok, testuser: updated spring.")
    }

    @Test
    fun `get MAVEN attribute renders coordinates and URL`() {
        eventGateway.process(msg("spring=A Java framework"))
        eventGateway.process(msg("spring.maven=org.springframework:spring-core"))
        val result = eventGateway.process(msg("spring.maven"))
        assertSuccess(
            result,
            "Maven: org.springframework:spring-core https://mvnrepository.com/artifact/org.springframework/spring-core",
        )
    }

    @Test
    fun `default query excludes MAVEN from summary`() {
        eventGateway.process(msg("spring=A Java framework"))
        eventGateway.process(msg("spring.maven=org.springframework:spring-core"))
        val result = eventGateway.process(msg("spring"))
        assertSuccess(result, "spring is A Java framework.")
    }

    // -- INFO --

    @Test
    fun `info query lists available attributes`() {
        eventGateway.process(msg("spring=A Java framework"))
        eventGateway.process(msg("spring.url=https://spring.io"))
        val result = eventGateway.process(msg("spring.info"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("text"))
        assertTrue(payload.contains("url"))
        assertTrue(payload.contains("attribute"))
    }

    // -- FORGET --

    @Test
    fun `forget deletes factoid`() {
        eventGateway.process(msg("ephemeral=temporary"))
        val forgetResult = eventGateway.process(msg("ephemeral.forget"))
        assertSuccess(forgetResult, "ok, forgot ephemeral.")

        val lookupResult = eventGateway.process(msg("ephemeral"))
        assertInstanceOf(OperationResult.NotHandled::class.java, lookupResult)
    }

    // -- LOCK/UNLOCK --

    @Test
    fun `lock from non-admin returns error`() {
        eventGateway.process(msg("locktest=value"))
        val result = eventGateway.process(msg("locktest.lock", role = Role.USER))
        assertError(result)
    }

    @Test
    fun `lock from admin succeeds`() {
        eventGateway.process(msg("locktest=value"))
        val result = eventGateway.process(msg("locktest.lock", role = Role.ADMIN))
        assertSuccess(result, "ok, locktest is now locked.")
    }

    @Test
    fun `set locked factoid returns error`() {
        eventGateway.process(msg("guarded=protected value"))
        eventGateway.process(msg("guarded.lock", role = Role.ADMIN))
        val result = eventGateway.process(msg("guarded=new value"))
        assertError(result)
    }

    // -- Case insensitive --

    @Test
    fun `case insensitive query matches`() {
        eventGateway.process(msg("Kotlin=A modern JVM language"))
        val result = eventGateway.process(msg("kotlin"))
        assertSuccess(result, "kotlin is A modern JVM language.")
    }

    // -- <reply> prefix --

    @Test
    fun `reply prefix suppresses selector is framing`() {
        eventGateway.process(msg("greet=<reply>Hello there!"))
        val result = eventGateway.process(msg("greet"))
        assertSuccess(result, "Hello there!")
    }

    // -- $1 interpolation --

    @Test
    fun `parameter interpolation replaces $1`() {
        eventGateway.process(msg("ask=Please ask \$1 for help"))
        val result = eventGateway.process(msg("ask joe"))
        assertSuccess(result, "ask is Please ask joe for help.")
    }

    @Test
    fun `missing $1 argument returns error`() {
        eventGateway.process(msg("paramtest=Tell \$1 about it"))
        val result = eventGateway.process(msg("paramtest"))
        assertError(result)
    }

    @Test
    fun `extra arguments without $1 returns not handled`() {
        eventGateway.process(msg("simple=Just a value"))
        val result = eventGateway.process(msg("simple extra words"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    // -- Multi-word selector --

    @Test
    fun `multi-word selector set and get`() {
        eventGateway.process(msg("spring boot=An opinionated framework"))
        val result = eventGateway.process(msg("spring boot"))
        assertSuccess(result, "spring boot is An opinionated framework.")
    }

    // -- Search --

    @Test
    fun `search with results`() {
        eventGateway.process(msg("spring=A Java framework"))
        eventGateway.process(msg("spring boot=Opinionated Spring"))
        val result = eventGateway.process(msg("search spring"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("spring"))
    }

    @Test
    fun `search with no results`() {
        val result = eventGateway.process(msg("search zzzznonexistent"))
        assertSuccess(result, "No factoids found searching for 'zzzznonexistent'.")
    }

    // -- Non-factoid passthrough --

    @Test
    fun `non-factoid message returns not handled`() {
        val result = eventGateway.process(msg("just a random message that matches nothing"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    // -- Punctuation normalization --

    @Test
    fun `existing punctuation is preserved not doubled`() {
        eventGateway.process(msg("question=Is this a test?"))
        val result = eventGateway.process(msg("question"))
        assertSuccess(result, "question is Is this a test?")
    }

    // -- "is" delimiter --

    @Test
    fun `set TEXT factoid with is delimiter`() {
        val result = eventGateway.process(msg("kotlin is A modern JVM language"))
        assertSuccess(result, "ok, testuser: updated kotlin.")
    }

    @Test
    fun `get TEXT factoid set with is delimiter`() {
        eventGateway.process(msg("kotlin is A modern JVM language"))
        val result = eventGateway.process(msg("kotlin"))
        assertSuccess(result, "kotlin is A modern JVM language.")
    }

    @Test
    fun `set attribute with is delimiter`() {
        eventGateway.process(msg("kotlin is A modern JVM language"))
        val result = eventGateway.process(msg("kotlin.url is https://kotlinlang.org"))
        assertSuccess(result, "ok, testuser: updated kotlin.")
    }

    @Test
    fun `get attribute set with is delimiter`() {
        eventGateway.process(msg("kotlin is A modern JVM language"))
        eventGateway.process(msg("kotlin.url is https://kotlinlang.org"))
        val result = eventGateway.process(msg("kotlin.url"))
        assertSuccess(result, "URL: https://kotlinlang.org")
    }

    @Test
    fun `is delimiter with value containing equals`() {
        eventGateway.process(msg("expression is 2+2=4"))
        val result = eventGateway.process(msg("expression"))
        assertSuccess(result, "expression is 2+2=4.")
    }

    @Test
    fun `equals delimiter with value containing is`() {
        eventGateway.process(msg("motto=this is the way"))
        val result = eventGateway.process(msg("motto"))
        assertSuccess(result, "motto is this is the way.")
    }

    @Test
    fun `attribute-qualified split takes priority over simple is delimiter`() {
        // "foo is bar.text=baz" - .text= found, selector="foo is bar", value="baz"
        val result = eventGateway.process(msg("foo is bar.text=baz"))
        assertSuccess(result, "ok, testuser: updated foo is bar.")
    }

    // -- See also --

    @Test
    fun `see also renders with tilde for existing factoids`() {
        eventGateway.process(msg("java=A programming language"))
        eventGateway.process(msg("kotlin=A modern JVM language"))
        eventGateway.process(msg("java.seealso=kotlin,python"))

        val result = eventGateway.process(msg("java.seealso"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload.contains("~kotlin"))
        assertTrue(payload.contains("python"))
    }

    // -- SEE redirect --

    @Test
    fun `see redirect resolves to target factoid`() {
        eventGateway.process(msg("bar=hello"))
        eventGateway.process(msg("foo.see=bar"))
        val result = eventGateway.process(msg("foo"))
        assertSuccess(result, "bar is hello.")
    }

    @Test
    fun `see redirect to nonexistent factoid returns not handled`() {
        eventGateway.process(msg("foo=placeholder"))
        eventGateway.process(msg("foo.see=nonexistent"))
        val result = eventGateway.process(msg("foo"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `see cycle protection returns not handled`() {
        eventGateway.process(msg("foo=placeholder"))
        eventGateway.process(msg("bar=placeholder"))
        eventGateway.process(msg("foo.see=bar"))
        eventGateway.process(msg("bar.see=foo"))
        val result = eventGateway.process(msg("foo"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `see redirect with explicit attribute still queries original factoid`() {
        eventGateway.process(msg("foo=own text"))
        eventGateway.process(msg("foo.see=bar"))
        eventGateway.process(msg("bar=bar text"))
        val result = eventGateway.process(msg("foo.text"))
        assertSuccess(result, "foo is own text.")
    }

    @Test
    fun `see chain resolves through multiple hops`() {
        eventGateway.process(msg("a=placeholder"))
        eventGateway.process(msg("b=placeholder"))
        eventGateway.process(msg("c=final value"))
        eventGateway.process(msg("a.see=b"))
        eventGateway.process(msg("b.see=c"))
        val result = eventGateway.process(msg("a"))
        assertSuccess(result, "c is final value.")
    }

    // -- Random selection --

    @Test
    fun `selection group picks one option`() {
        eventGateway.process(msg("coin=<reply>(heads|tails)"))
        val validResults = setOf("heads.", "tails.")
        repeat(20) {
            val result = eventGateway.process(msg("coin"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    @Test
    fun `selection with selector-is framing`() {
        eventGateway.process(msg("greeting=(hello|world)"))
        val validResults = setOf("greeting is hello.", "greeting is world.")
        repeat(20) {
            val result = eventGateway.process(msg("greeting"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    @Test
    fun `multiple selection groups resolve independently`() {
        eventGateway.process(msg("combo=<reply>(a|b) and (c|d)"))
        val validResults = setOf("a and c.", "a and d.", "b and c.", "b and d.")
        repeat(20) {
            val result = eventGateway.process(msg("combo"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    @Test
    fun `selection with dollar-one interpolation`() {
        eventGateway.process(msg("greet=<reply>(hello|goodbye) \$1"))
        val result = eventGateway.process(msg("greet world"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertTrue(payload == "hello world." || payload == "goodbye world.")
    }

    // -- Tilde reference in selection --

    @Test
    fun `tilde reference resolves factoid in selection`() {
        eventGateway.process(msg("target=<reply>resolved"))
        eventGateway.process(msg("ref=<reply>(~target|literal)"))
        val validResults = setOf("resolved.", "literal.")
        repeat(20) {
            val result = eventGateway.process(msg("ref"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    @Test
    fun `tilde reference miss returns empty string`() {
        eventGateway.process(msg("ref=<reply>(~nonexistent|fallback)"))
        val validResults = setOf(".", "fallback.")
        repeat(20) {
            val result = eventGateway.process(msg("ref"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    @Test
    fun `parenthesized text without pipe is not treated as selection`() {
        eventGateway.process(msg("note=(just a note)"))
        val result = eventGateway.process(msg("note"))
        assertSuccess(result, "note is (just a note).")
    }

    // -- Forget verb command --

    @Test
    fun `forget verb deletes entire factoid`() {
        eventGateway.process(msg("ephemeral=temporary"))
        val result = eventGateway.process(msg("forget ephemeral"))
        assertSuccess(result, "ok, forgot ephemeral.")

        val lookupResult = eventGateway.process(msg("ephemeral"))
        assertInstanceOf(OperationResult.NotHandled::class.java, lookupResult)
    }

    @Test
    fun `forget verb deletes single attribute`() {
        eventGateway.process(msg("multi=some text"))
        eventGateway.process(msg("multi.see=other"))
        val result = eventGateway.process(msg("forget multi.see"))
        assertSuccess(result, "ok, forgot see on multi.")

        // TEXT should still be there
        val lookupResult = eventGateway.process(msg("multi"))
        assertSuccess(lookupResult, "multi is some text.")
    }

    @Test
    fun `forget verb on nonexistent factoid returns error`() {
        val result = eventGateway.process(msg("forget nonexistent"))
        assertError(result)
    }

    @Test
    fun `forget verb on nonexistent attribute returns error`() {
        eventGateway.process(msg("exists=value"))
        val result = eventGateway.process(msg("forget exists.see"))
        assertError(result)
    }

    @Test
    fun `forget verb on locked factoid returns error`() {
        eventGateway.process(msg("locked=value"))
        eventGateway.process(msg("locked.lock", role = Role.ADMIN))
        val result = eventGateway.process(msg("forget locked.text"))
        assertError(result)
    }

    // -- Set verb command --

    @Test
    fun `set verb sets attribute`() {
        eventGateway.process(msg("set target.text hello world"))
        val result = eventGateway.process(msg("target"))
        assertSuccess(result, "target is hello world.")
    }

    @Test
    fun `set verb sets see attribute`() {
        eventGateway.process(msg("destination=the real value"))
        eventGateway.process(msg("set alias.see destination"))
        val result = eventGateway.process(msg("alias"))
        assertSuccess(result, "destination is the real value.")
    }

    @Test
    fun `set verb with multi-word selector`() {
        eventGateway.process(msg("set spring boot.text An opinionated framework"))
        val result = eventGateway.process(msg("spring boot"))
        assertSuccess(result, "spring boot is An opinionated framework.")
    }

    @Test
    fun `set verb on locked factoid returns error`() {
        eventGateway.process(msg("guarded2=original"))
        eventGateway.process(msg("guarded2.lock", role = Role.ADMIN))
        val result = eventGateway.process(msg("set guarded2.text new value"))
        assertError(result)
    }

    @Test
    fun `set verb without attribute returns not handled`() {
        val result = eventGateway.process(msg("set noattr"))
        // Falls through to other operations since "set noattr" doesn't parse as a set command
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    // -- Forget + See integration: the original use case --

    @Test
    fun `see redirect removed by forget verb restores original text`() {
        eventGateway.process(msg("a=goodbye"))
        eventGateway.process(msg("b=hello"))
        eventGateway.process(msg("a.see=b"))

        // Verify redirect is active
        val redirected = eventGateway.process(msg("a"))
        assertSuccess(redirected, "b is hello.")

        // Remove the see redirect
        eventGateway.process(msg("forget a.see"))

        // Original text should be back
        val restored = eventGateway.process(msg("a"))
        assertSuccess(restored, "a is goodbye.")
    }

    // -- Nested selection (8ball) --

    @Test
    fun `nested selection groups resolve correctly end to end`() {
        eventGateway.process(
            msg(
                "8ball=<reply>(Yes|No|Maybe|Ask me( tomorrow| again| again tomorrow)" +
                    "|It's (unclear|unknowable))."
            )
        )
        val validResults =
            setOf(
                "Yes.",
                "No.",
                "Maybe.",
                "Ask me tomorrow.",
                "Ask me again.",
                "Ask me again tomorrow.",
                "It's unclear.",
                "It's unknowable.",
            )
        repeat(50) {
            val result = eventGateway.process(msg("8ball"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    @Test
    fun `empty option in selection can produce empty result`() {
        eventGateway.process(msg("maybe=<reply>(something|)"))
        val validResults = setOf("something.", ".")
        repeat(20) {
            val result = eventGateway.process(msg("maybe"))
            assertInstanceOf(OperationResult.Success::class.java, result)
            val payload = (result as OperationResult.Success).payload as String
            assertTrue(payload in validResults)
        }
    }

    // -- Helper --

    private fun assertTrue(condition: Boolean) {
        org.junit.jupiter.api.Assertions.assertTrue(condition)
    }
}
