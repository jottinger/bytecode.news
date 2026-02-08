# Event System Developer Guide

This document describes the core event system that powers all message processing in Streampack.
Every service - blog, IRC, Discord, whatever comes next - uses this system to process incoming requests and produce responses.

## Architecture

The event system is built on Spring Integration with a gateway pattern that scales via virtual threads.

```
  Service Adapter                lib-core                        Global Operation Pool
  (e.g., blog)                                                   (all registered beans)

  HTTP request ──►  EventGateway  ──►  ingressChannel  ──►  OperationService
                                       (ExecutorChannel)         │
                                       (virtual threads)         ├─ canHandle? ──► Operation A (skip)
                                                                 ├─ canHandle? ──► Operation B (execute)
                                                                 │                     │
  HTTP response ◄── (gateway reply) ◄── OperationResult ◄────────┘                     │
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
    val priority: Int
    fun canHandle(message: Message<*>): Boolean  // defaults to true
    fun execute(message: Message<*>): OperationResult?
}
```

**priority** - lower values run first.
Convention: 0-20 for high priority, 50 for normal, 80+ for catch-alls.

**canHandle** - a cheap pre-flight check.
Inspect the Provenance header, payload type, or message metadata to decide if this operation is relevant.
Return false to skip entirely.
The default implementation returns true, so operations that want to handle everything can omit this method.

**execute** - do the actual work.
Return an `OperationResult` to produce a definitive answer, or null to pass to the next operation in the chain.
Returning null means "I thought I could handle this but couldn't - let someone else try."

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
A blog operation and an IRC command operation live in the same pool.
They self-select via `canHandle` - typically by inspecting the Provenance header for protocol and serviceId, or by checking the payload type.

## Implementing an Operation

Here is a complete example of an operation that handles greeting requests:

```kotlin
@Component
class GreetingOperation : Operation {
    override val priority = 50

    override fun canHandle(message: Message<*>): Boolean {
        // Only handle messages from the HTTP protocol addressed to the blog service
        val provenance = message.headers[Provenance.HEADER] as? Provenance ?: return false
        return provenance.protocol == Protocol.HTTP && provenance.serviceId == "blog-service"
    }

    override fun execute(message: Message<*>): OperationResult? {
        val payload = message.payload as? String ?: return null
        if (!payload.startsWith("greet:")) return null
        val name = payload.removePrefix("greet:")
        return OperationResult.Success("Hello, $name!")
    }
}
```

Key patterns demonstrated:
- **canHandle checks Provenance** to filter by protocol/service.
  This is how "blog operations" stay out of the way of "IRC operations."
- **execute can return null** when the payload doesn't match expectations.
  This lets the chain continue to other operations.
- **execute returns Success** with a typed payload when it handles the message.

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

Write integration tests with `@SpringBootTest`.
Define test operations in a `@TestConfiguration` class:

```kotlin
@SpringBootTest
class MyOperationTests {

    @TestConfiguration
    class Config {
        @Bean
        fun myOperation() = object : Operation {
            override val priority = 50
            override fun canHandle(message: Message<*>) = true
            override fun execute(message: Message<*>) =
                OperationResult.Success("handled: ${message.payload}")
        }
    }

    @Autowired lateinit var eventGateway: EventGateway

    @Test
    fun `my operation handles messages`() {
        val message = MessageBuilder.withPayload("test")
            .setHeader(Provenance.HEADER, Provenance(
                protocol = Protocol.HTTP,
                serviceId = "test-service",
                replyTo = "test",
            ))
            .build()

        val result = eventGateway.process(message)
        assertEquals(OperationResult.Success("handled: test"), result)
    }
}
```

See `EventSystemTests` in lib-core for the canonical examples covering all paths:
success, error, not-handled, priority ordering, pass-through (execute returns null), and canHandle filtering.
