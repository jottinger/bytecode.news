# Event System Developer Guide

This document describes the core event system that powers all message processing in Streampack.
Every service - blog, IRC, Discord, whatever comes next - uses this system to process incoming requests and produce responses.

## Architecture

The event system is built on Spring Integration with a gateway pattern that scales via virtual threads.

```
  Service Adapter                lib-core                        Global Operation Pool
  (e.g., blog)                                                   (all registered beans)

  HTTP request -->  EventGateway  -->  ingressChannel  -->  OperationService
                                       (ExecutorChannel)         |
                                       (virtual threads)         +- canHandle? --> Operation A (skip)
                                                                 +- canHandle? --> Operation B (execute)
                                                                 |                     |
  HTTP response <-- (gateway reply) <-- OperationResult <--------+                     |
                                                                                  result or null
```

The calling thread parks (cheaply, via Loom virtual threads) while the operation chain runs on its own virtual thread.
This means HTTP request handling scales without blocking platform threads.

## Core Types

### OperationResult

Every message produces exactly one `OperationResult`.
This is a sealed class with three variants:

- **Success(payload)** - an operation handled the message and produced a response.
  The payload is whatever the operation wants to return (a DTO, a string, a rendered page, etc.).
- **Error(message)** - an operation handled the message but encountered a failure.
  This is still a definitive answer - it short-circuits the chain.
- **NotHandled** - the entire operation chain was exhausted without any operation producing a result.

Both Success and Error stop the chain immediately.
NotHandled is only returned when every operation has been tried and none produced a result.

### Operation

The `Operation` interface is what you implement to add behavior to the system.
Operations are Spring beans - register them with `@Component` or define them as `@Bean` methods.

```kotlin
interface Operation {
    val priority: Int       // defaults to 50
    fun canHandle(message: Message<*>): Boolean  // defaults to true
    fun execute(message: Message<*>): OperationResult?
}
```

**priority** - lower values run first.
Defaults to 50 (normal).
Convention: 0-20 for high priority, 80+ for catch-alls.
Most operations should leave the default and not specify a priority at all.

**canHandle** - a cheap pre-flight check.
Inspect the Provenance header, payload type, or message metadata to decide if this operation is relevant.
Return false to skip entirely.
The default implementation returns true, so operations that want to handle everything can omit this method.

**execute** - do the actual work.
Return an `OperationResult` to produce a definitive answer, or null to pass to the next operation in the chain.
Returning null means "I thought I could handle this but couldn't - let someone else try."

### TypedOperation

Most operations care about a specific payload type.
`TypedOperation<T>` eliminates the type-check and cast boilerplate:

```kotlin
abstract class TypedOperation<T : Any>(payloadType: KClass<T>) : Operation {
    // Type check happens automatically - wrong type means canHandle returns false
    final override fun canHandle(message: Message<*>): Boolean  // sealed, not overridable

    // Override this for additional filtering after the type check passes
    open fun canHandle(payload: T, message: Message<*>): Boolean = true

    // Implement this instead of execute - payload is already cast
    abstract fun handle(payload: T, message: Message<*>): OperationOutcome?
}
```

The two-tier `canHandle` flow works like this:

1. The outer `canHandle(message)` checks whether the payload is an instance of `T`. If not, returns false immediately.
2. If the type matches, it calls the inner `canHandle(payload, message)` with the already-cast payload.
3. Subclasses override the inner method to add content-based filtering.

This is particularly useful for string-based operations where multiple operations share the same payload type (`String`) but handle different commands:

```kotlin
@Component
class CalculatorOperation(val service: CalculatorService) :
    TypedOperation<String>(String::class) {

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        return payload.trim().startsWith("calc ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val expression = payload.trim().substringAfter("calc ").trim()
        return service.evaluate(expression)?.let {
            OperationResult.Success("The result of $expression is: $it")
        } ?: OperationResult.Error("Invalid expression: $expression")
    }
}
```

The calculator only sees messages with `String` payloads that start with `"calc "`.
A karma operation on the same `TypedOperation<String>` could check for `"++"` or `"--"` and they'd coexist peacefully.

Operations that accept typed request objects (like `CreateUserRequest`) typically don't need to override the inner `canHandle` at all - the type check alone is sufficient to route to the correct operation.

Operations that need raw message access or want to accept multiple payload types should implement `Operation` directly instead.

### EventGateway

The entry point for sending messages into the event system.
Services inject this and call `process()`:

```kotlin
@Autowired lateinit var eventGateway: EventGateway

fun handleRequest(content: String, provenance: Provenance): OperationResult {
    val message = MessageBuilder.withPayload(content)
        .setHeader(Provenance.HEADER, provenance)
        .build()
    return eventGateway.process(message)
}
```

The gateway sends to the ingress channel and waits for the reply.
With virtual threads, "waits" means the virtual thread parks - no platform thread is consumed.

## How Operations Are Discovered and Ordered

The `OperationService` collects all `Operation` beans at startup and sorts them by priority.
When a message arrives:

1. Iterate operations in priority order (lowest number first)
2. For each operation, call `canHandle(message)`
3. If `canHandle` returns false, skip to the next operation
4. If `canHandle` returns true, call `execute(message)`
5. If `execute` returns non-null, that is the result - stop the chain
6. If `execute` returns null, continue to the next operation
7. If no operation produces a result, return `OperationResult.NotHandled`

Operations are global.
A blog operation and a calculator operation live in the same pool.
They self-select via `canHandle` - typically by checking the payload type (via `TypedOperation`), inspecting the Provenance header for protocol and serviceId, or examining the payload content.

## Building an Operation Module

This walkthrough uses `operation-calc` as a concrete example.
The pattern applies to any new operation module.

### 1. Create the module

Create a new directory with a `pom.xml` that depends on `lib-core` and `lib-test`:

```xml
<parent>
    <groupId>com.enigmastation</groupId>
    <artifactId>streampack</artifactId>
    <version>1.0</version>
</parent>

<artifactId>operation-calc</artifactId>

<dependencies>
    <dependency>
        <groupId>com.enigmastation</groupId>
        <artifactId>lib-core</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.enigmastation</groupId>
        <artifactId>lib-test</artifactId>
        <version>${project.version}</version>
        <scope>test</scope>
    </dependency>
    <!-- module-specific dependencies -->
</dependencies>
```

Register the module in the parent POM's `<modules>` section.

### 2. Write the service

The service contains the domain logic, independent of the messaging system.
It knows nothing about operations, messages, or provenance:

```kotlin
@Component
class CalculatorService {
    private val logger = LoggerFactory.getLogger(CalculatorService::class.java)

    fun evaluate(expression: String): String? {
        if (expression.isBlank()) return null
        return try {
            val result = MathExpression(expression).solve()?.toString()
            if (result == null || result == "SYNTAX ERROR") {
                logger.debug("Invalid expression: {}", expression)
                null
            } else {
                result
            }
        } catch (e: Exception) {
            logger.debug("Expression evaluation failed for '{}': {}", expression, e.message)
            null
        }
    }
}
```

Key points:
- The service returns domain values (a result string) or null for failures.
- It does not throw exceptions for invalid input - it normalizes errors.
- It uses SLF4J for logging, not println.

### 3. Write the operation

The operation bridges the messaging system to the service.
It decides which messages to handle and translates between message payloads and service calls:

```kotlin
@Component
class CalculatorOperation(val service: CalculatorService) :
    TypedOperation<String>(String::class) {

    override fun canHandle(payload: String, message: Message<*>): Boolean {
        return payload.trim().startsWith("calc ")
    }

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        val expression = payload.trim().substringAfter("calc ").trim()
        if (expression.isBlank()) {
            return OperationResult.Error("No expression provided")
        }
        return service.evaluate(expression)?.let {
            OperationResult.Success("The result of $expression is: $it")
        } ?: OperationResult.Error("Invalid expression: $expression")
    }
}
```

Key points:
- `@Component` is required - operations must be Spring beans for `OperationService` to discover them.
- `TypedOperation<String>` means the outer `canHandle` rejects any non-String payload automatically.
- The inner `canHandle` checks for the `"calc "` trigger prefix.
- Priority is not specified, so it defaults to 50.
- The operation strips the trigger prefix before passing the expression to the service.

### 4. Create the test application

Each module needs a `@SpringBootApplication` in its test sources as a scan root.
Place it in `src/test/kotlin`:

```kotlin
@SpringBootApplication(scanBasePackages = ["com.enigmastation.streampack"])
class CalculatorApplication
```

The `scanBasePackages` is important - it tells Spring to scan the full `com.enigmastation.streampack` package tree so it finds `EventGateway`, `OperationService`, and other lib-core beans in addition to the module's own beans.

### 5. Write the tests

Test the service in isolation and the operation through the full event pipeline.

**Service tests** validate the domain logic directly:

```kotlin
@SpringBootTest
class CalculatorServiceTests {
    @Autowired lateinit var service: CalculatorService

    @Test
    fun `evaluates addition`() {
        assertEquals("4.0", service.evaluate("2+2"))
    }

    @Test
    fun `returns null for garbage input`() {
        assertNull(service.evaluate("hello world"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(service.evaluate(""))
    }
}
```

**Operation tests** exercise the full pipeline via `EventGateway`:

```kotlin
@SpringBootTest
class CalculatorOperationTests {
    @Autowired lateinit var eventGateway: EventGateway

    private fun calcMessage(text: String) =
        MessageBuilder.withPayload(text)
            .setHeader(
                Provenance.HEADER,
                Provenance(protocol = Protocol.CONSOLE, serviceId = "", replyTo = "local"),
            )
            .build()

    @Test
    fun `valid expression returns success`() {
        val result = eventGateway.process(calcMessage("calc 2+3"))
        assertInstanceOf(OperationResult.Success::class.java, result)
        val payload = (result as OperationResult.Success).payload as String
        assertEquals("The result of 2+3 is: 5.0", payload)
    }

    @Test
    fun `non-calc message is not handled`() {
        val result = eventGateway.process(calcMessage("karma foo++"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }

    @Test
    fun `invalid expression returns error`() {
        val result = eventGateway.process(calcMessage("calc hello world"))
        assertInstanceOf(OperationResult.Error::class.java, result)
    }
}
```

Key testing patterns:
- Service tests use `@SpringBootTest` and assert return values directly.
- Operation tests send messages through `EventGateway` and assert the `OperationResult` type and content.
- Include positive cases, error cases, and "not my problem" cases (messages the operation should ignore).
- `lib-test` provides the test `application.yml` and `TestChannelConfiguration` automatically via classpath.

### 6. Wire it into the app

Add the module as a dependency in `app/pom.xml`:

```xml
<dependency>
    <groupId>com.enigmastation</groupId>
    <artifactId>operation-calc</artifactId>
    <version>${project.version}</version>
</dependency>
```

Because the `app` module scans `com.enigmastation.streampack`, the operation's `@Component` beans are discovered automatically.
No additional configuration is needed.

To exclude an operation module from a deployment, simply remove its dependency from the app POM.

## Advanced Result Patterns

Beyond the basic `Success`/`Error`/`NotHandled` flow, the event system supports three mechanisms for operations that need more than simple request/response.

### Provenance Override

By default, `publishToEgress` routes the result back to wherever the message came from - the provenance on the input message.
Sometimes an operation needs to send its result somewhere else entirely.

`OperationResult.Success` has an optional `provenance` field.
When set, `publishToEgress` uses the override provenance instead of the input message's provenance:

```kotlin
// Deliver this result to a different destination
return OperationResult.Success(
    payload = "<alice> hello",
    provenance = Provenance(protocol = Protocol.IRC, serviceId = "libera", replyTo = "blue"),
)
```

The result flows through the standard egress path - logging, transformation, and protocol delivery all work normally.
The only difference is *where* the result goes.

**Use provenance override when:**
- An operation needs to deliver its result to a different channel or user than where the command was issued
- Examples: `tell` (delivers a message to another user), `sentiment` (responds via DM for cross-channel queries)

**Do not use provenance override when:**
- The result should go back to the sender (the default, no override needed)
- You need to send multiple results to multiple destinations (use FanOut instead)

### FanOut

FanOut lets an operation produce multiple independently-addressed messages that each run through the full operation chain.
This is for spawning new work, not for delivering results directly.

```kotlin
return FanOut(listOf(childMessage1, childMessage2))
```

Each child message is dispatched through `eventGateway.process()` with an incremented hop count.
The original caller receives `Success("Dispatched N messages")`.

FanOut is *not* a multi-destination delivery mechanism.
It re-enters the operation chain, meaning each child message is processed as a new input - other operations will handle it, not egress subscribers.

**Use FanOut when:**
- An operation needs to trigger other operations with new messages
- The bridge copy operation uses this pattern to inject attributed messages into the chain for each bridge target

**Do not use FanOut when:**
- You want to send a result to a specific destination (use provenance override instead)
- You want to send the same result to multiple destinations (not currently supported as a single operation result; use direct egress writes or multiple operations)

### Loopback

Loopback re-injects the result payload as a new addressed message after the original result has been delivered to egress.
The result reaches the sender normally, *and then* the payload goes back through the operation chain as new input.

```kotlin
return OperationResult.Success("a poem: roses are red/violets are blue", loopback = true)
```

The loopback message carries the same provenance as the original, with `addressed = true` and an incremented hop count.
The `maxHops` property (default 3) prevents infinite loops.

**Use loopback when:**
- An operation produces output that another operation should process
- Example: the poem generator emits a poem, which the poem analysis operation should evaluate

**Do not use loopback when:**
- You need the result to go to a different destination (use provenance override)
- You need to trigger multiple independent operations (use FanOut)

### Choosing the right mechanism

| Need | Mechanism |
|------|-----------|
| Send result back to sender | Default (no flags) |
| Send result to a different destination | Provenance override |
| Send result to sender, then feed it back through the chain | Loopback |
| Spawn multiple new messages for other operations to process | FanOut |

## Choosing Between Operation and TypedOperation

Use **`TypedOperation<T>`** when:
- Your operation handles a single payload type
- You want automatic type-checking in `canHandle`
- You want to work with a pre-cast payload in both `canHandle` and `handle`
- This is the common case for most operations

Use **`Operation` directly** when:
- Your operation handles multiple payload types
- You need raw `Message<*>` access in `canHandle`
- You have unusual routing logic that doesn't fit the type-check pattern

## Interactive vs. Typed Request Operations

There are two styles of operation in the system:

**Typed request operations** accept domain-specific request objects (like `CreateUserRequest`, `LoginRequest`).
The payload type alone is enough to route to the correct operation.
These are used by protocol adapters (like the HTTP controllers) that construct typed requests from structured input.

**String-based operations** accept raw `String` payloads from interactive protocols (console, IRC, Discord).
Multiple string operations share the same payload type, so they use the inner `canHandle` to match on trigger prefixes.
The protocol adapter strips protocol-specific decoration (like `!` prefixes or bot name mentions) before submitting the string.

Both styles coexist in the same operation pool.
A message with a `CreateUserRequest` payload will never match a `TypedOperation<String>`, and vice versa.

## Message Construction

Messages always carry a Provenance header.
The Provenance identifies where the message came from and where the response should go:

```kotlin
val provenance = Provenance(
    protocol = Protocol.HTTP,
    serviceId = "blog-service",
    replyTo = "posts",
    user = userPrincipal,  // resolved by the service adapter, may be null
)

val message = MessageBuilder.withPayload(requestBody)
    .setHeader(Provenance.HEADER, provenance)
    .build()
```

The service adapter is responsible for:
1. Resolving user identity (via `UserResolutionService`)
2. Building the Provenance
3. Constructing the message
4. Calling the gateway
5. Translating the `OperationResult` back to a protocol-specific response

## Channel Configuration

The ingress channel is an `ExecutorChannel` backed by a virtual-thread-per-task executor.
This is configured automatically via `EventChannelConfiguration` in lib-core.
Service modules that depend on lib-core get the event system for free.

## Testing Operations

The `lib-test` module provides shared test infrastructure.
Add it as a test-scope dependency and you get:

- **`application.yml`** with Testcontainers PostgreSQL, Flyway, and other test defaults
- **`TestChannelConfiguration`** that overrides the ingress channel with a `DirectChannel`, forcing operations to run on the test thread so `@Transactional` rollback works correctly

For simple operation tests, `@SpringBootTest` with `EventGateway` injection is all you need (see the calculator example above).

For operations that interact with the database, add `@Transactional` to your test class so test data is rolled back automatically.

See `EventSystemTests` in lib-core for canonical examples covering all paths:
success, error, not-handled, priority ordering, pass-through (execute returns null), and canHandle filtering.
