# Architecture Guide

This is the architecture guide for Streampack, the codebase behind Nevet.
It covers how the system works, how to build new capabilities, and what conventions to follow.

For command reference, see [User Guide](user-guide.md).
For REST API endpoints, see [API Reference](api-reference.md).
For running a development instance, see [Deployment Guide](deployment.md).

## Design Philosophy

Streampack is built on a single idea: protocols are interchangeable, behavior is not.

A factoid lookup means the same thing whether it comes from IRC, Discord, a REST call, or the console.
The system normalizes all input into a common message format, routes it through a shared pool of operations, and delivers output back through the originating protocol.

Five principles follow from this:

1. **Protocol adapters are dumb.**
   They know only how to receive and send in their protocol.
   They do not interpret content.

2. **Translation is centralized.**
   A shared translation layer converts raw input to typed operations.
   HTTP requests may bypass string translation when already structured.

3. **Operations are protocol-agnostic.**
   They receive typed payloads and Provenance headers.
   They return results.
   They never know where a message came from.

4. **Services contain domain logic; operations contain routing logic.**
   A `FactoidService` knows how to store and retrieve factoids.
   A `GetFactoidOperation` knows that `"spring"` is a factoid query and that the result should be rendered as `"spring is A Java framework."`.
   The service is reusable from REST controllers, tests, or other operations.
   The operation is the bridge between the messaging world and the service world.

5. **Make it work, make it pretty, make it fast - in that order.**
   Correctness first.
   Clean code second, because clean code makes bugs visible.
   Performance last, because you cannot optimize what you cannot read.

## The Messaging Pipeline

Every message flows through three stages:

```
Protocol Adapter  -->  ingressChannel  -->  OperationService  -->  egressChannel  -->  Protocol Adapter
     (input)          (ExecutorChannel,       (operation chain)    (PublishSubscribe)       (output)
                       virtual threads)
```

**Ingress**: a protocol adapter (IRC listener, HTTP controller, console reader) constructs a `Message<*>` with a payload and a `Provenance` header, then sends it to the `EventGateway`.
The calling thread parks cheaply via virtual threads while the operation chain runs.

**Processing**: the `OperationService` runs the message through every registered operation in priority order.
The first operation to return a terminal result (`Success` or `Error`) wins.
If no operation handles the message, the result is `NotHandled`.

**Egress**: the result is published to the egress channel with the original `Provenance` header intact.
Every `EgressSubscriber` sees the message; each one checks whether the provenance matches its protocol and delivers accordingly.

The ingress channel uses virtual threads, so each message gets its own lightweight thread.
The egress channel is synchronous - subscribers run on the operation chain's thread to preserve ordering.

## Operation Base Classes

Three base classes exist, and the choice matters:

**`TypedOperation<T>`** is the default choice.
Use it when your operation handles a single payload type.
The outer `canHandle` rejects wrong types automatically; you override the inner `canHandle` for content-based filtering.

**`TranslatingOperation<T>`** is for operations that need dual-input access.
When the payload is a `String`, the `translate()` method parses it into a typed request.
When the payload is already the correct type (from a REST controller or another operation), `translate()` is never called.
Use this when your operation needs to work from both interactive text (IRC, console) and structured API calls.

**`Operation`** (the raw interface) is for unusual cases - multiple payload types, raw message header inspection, or routing logic that does not fit the typed pattern.

The decision tree:

```
Does the operation accept input from both text commands AND typed requests?
  Yes --> TranslatingOperation<T>
  No  --> Does it accept a single payload type?
            Yes --> TypedOperation<T>
            No  --> Operation (raw interface)
```

### The Two-Tier canHandle Pattern

Both `TypedOperation` and `TranslatingOperation` use a two-tier `canHandle` design:

1. The **outer** `canHandle(message)` is `final`.
   It handles type resolution - checking whether the payload is the right type (TypedOperation) or translating a string into the typed request (TranslatingOperation).

2. The **inner** `canHandle(payload, message)` is `open`.
   It receives the already-resolved typed payload.
   Override this for content-based filtering: checking command prefixes, verifying authorization, or any other pre-flight logic.

This separation means you never write type-checking boilerplate.

## Building an Operation

### Priority

Priority determines execution order.
Lower numbers run first.

- **0-20**: high priority, runs before most things (e.g., poem analysis at 20)
- **40-50**: normal range, appropriate for most operations
- **65-75**: mid-range, factoid search and set operations
- **90+**: catch-all territory, runs last (e.g., factoid get at 90, specs and dictionary at 95)

Priorities do not need to be unique.
Most operations should leave the default (50) unless there is a specific reason to run earlier or later.

### The addressed Flag

When `addressed` is `true` (the default), protocol adapters only send messages that were explicitly directed at the bot - prefixed with the signal character (`!`) or the bot's nick.

When `addressed` is `false`, the adapter checks all messages against the operation.
This is for operations that monitor ambient conversation, like karma tracking (`foo++` does not require addressing the bot).

### Authorization

Roles are hierarchical: `GUEST < USER < ADMIN < SUPER_ADMIN`.

The `Operation` interface provides two utility methods for role checks:

- **`hasRole(message, role): Boolean`** - returns true if the sender has at least the given role.
  Use in `canHandle` when unauthorized users should silently fall through to the next operation.
- **`requireRole(message, role): OperationResult.Error?`** - returns an error if unauthorized, null if OK.
  Use in `handle` when unauthorized users should receive an explicit error.

For simple uniform gates where the entire operation requires a role:

```kotlin
// In canHandle: silently skip if unauthorized (chain continues)
override fun canHandle(payload: AddFeedRequest, message: Message<*>): Boolean {
    return hasRole(message, Role.ADMIN)
}

// In handle: return an error if unauthorized (chain stops)
override fun handle(payload: String, message: Message<*>): OperationOutcome {
    requireRole(message, Role.ADMIN)?.let { return it }
    // ... business logic
}
```

For operations with mixed access (some subcommands public, some admin-only), use `requireRole` after dispatching public subcommands:

```kotlin
override fun handle(payload: String, message: Message<*>): OperationOutcome {
    val subcommand = parseSubcommand(payload)
    if (subcommand == "list") return handleList() // public
    requireRole(message, Role.ADMIN)?.let { return it }
    // ... admin-only subcommands
}
```

When `canHandle` returns `false`, the operation is skipped and the chain continues.
This means an unauthorized user's message falls through to the next operation rather than producing an error.

### The handle Method

`handle` contains the business logic.
It receives the typed payload and returns an `OperationOutcome`:

- **`OperationResult.Success(payload)`** - definitive answer. Stops the chain.
- **`OperationResult.Error(message)`** - definitive failure. Stops the chain.
- **`null`** - silent pass. Chain continues. (e.g., factoid lookup found no match)
- **`Declined(reason)`** - pass with diagnostic logging. Chain continues.

### Redacting Secrets from Logs

All inbound messages are captured to the message log.
If your operation accepts secrets via text commands, override `redactionRules` to declare which token positions contain secrets:

```kotlin
override val redactionRules = listOf(
    RedactionRule("irc connect", setOf(5, 6))
)
```

This replaces tokens at positions 5 and 6 with `[REDACTED]` before logging when a message starts with `"irc connect"`.

## Advanced Result Patterns

Beyond the basic `Success`/`Error`/`NotHandled` flow, the event system supports three mechanisms for operations that need more than simple request/response.

### Provenance Override

By default, results route back to wherever the message came from.
`OperationResult.Success` has an optional `provenance` field.
When set, the result is delivered to the override provenance instead of the input message's provenance:

```kotlin
return OperationResult.Success(
    payload = "<alice> hello",
    provenance = Provenance(protocol = Protocol.IRC, serviceId = "libera", replyTo = "blue"),
)
```

Used by: `tell` (delivers to another user), `sentiment` (responds via DM for cross-channel queries).

### FanOut

FanOut lets an operation produce multiple independently-addressed messages that each run through the full operation chain:

```kotlin
return FanOut(listOf(childMessage1, childMessage2))
```

Each child message is dispatched through `eventGateway.process()` with an incremented hop count.
FanOut is for spawning new work, not for delivering results directly.

Used by: bridge copy operation (injects attributed messages for each bridge target).

### Loopback

Loopback re-injects the result payload as a new addressed message after the original result has been delivered to egress.
The result reaches the sender normally, and then the payload goes back through the operation chain as new input.

```kotlin
return OperationResult.Success("a poem: roses are red/violets are blue", loopback = true)
```

The `maxHops` property (default 3) prevents infinite loops.

Used by: poem generator (emits a poem that the analysis operation evaluates).

### Choosing the Right Mechanism

| Need | Mechanism |
|------|-----------|
| Send result back to sender | Default (no flags) |
| Send result to a different destination | Provenance override |
| Send result to sender, then feed it back through the chain | Loopback |
| Spawn multiple new messages for other operations to process | FanOut |

## Inter-Operation Communication

Operations can communicate by sending messages through the `EventGateway`.
Inject the gateway and call `process()`:

```kotlin
val request = SomeOtherRequest(data = payload)
val msg = MessageBuilder.withPayload(request)
    .setHeader(Provenance.HEADER, message.headers[Provenance.HEADER])
    .build()
val result = eventGateway.process(msg)
```

This keeps operations decoupled.
If the target operation is not on the classpath, the message returns `NotHandled` and nothing breaks.

### The Cache Pattern

A powerful pattern emerges when one operation populates another's data store.

A user types `rfc 2616`.
The factoid get operation (priority 90) checks first - no factoid exists, so it returns `null` and the chain continues.
The specs operation (priority 95) catches the miss, looks up the RFC, creates a factoid via `EventGateway.process()`, and returns the result.

Next time someone types `rfc 2616`, the factoid already exists and the factoid get operation handles it at priority 90.
The spec lookup becomes a cache-miss handler that populates the factoid system.

The dictionary operation uses the same pattern.

## Provenance System

Provenance tracks where a message came from and where the response should go.
It flows through the entire pipeline as a message header.

```kotlin
data class Provenance(
    val protocol: Protocol,
    val serviceId: String? = null,
    val user: UserPrincipal? = null,
    val replyTo: String,
    val alsoNotify: List<String> = emptyList(),
)
```

### URI Encoding

Provenance encodes to and decodes from URIs for storage and cross-protocol routing:

- `irc://libera/%23java` - IRC, Libera network, #java channel
- `discord://jvm-community/%23java-help` - Discord guild and channel
- `slack://jvm-news/%23general` - Slack workspace and channel
- `console:///local` - console adapter
- `mailto:///user@example.com` - email, no service intermediary (triple-slash)

The subscription system uses these URIs as destination addresses.
An RSS subscription stores `irc://libera/%23java` as its destination, and the polling service decodes it back to a Provenance for egress delivery.

### User Resolution

Protocol adapters resolve user identity before building the Provenance.
An IRC adapter maps the nick on a network to a `UserPrincipal` via the `ServiceBinding` table.
An HTTP adapter resolves the JWT token to a `UserPrincipal`.

If no binding exists, `user` is null and the effective role is `GUEST`.

## Egress and Loop Prevention

### EgressSubscriber

Egress subscribers deliver operation results to their respective protocols.
Each subscriber implements two methods:

- **`matches(provenance)`** - does this subscriber handle this protocol?
- **`deliver(result, provenance)`** - send the result to the destination

All subscribers see every egress message; they self-select via `matches`.

### System-Generated Output

Not all output originates from the operation chain.
System-generated notifications (like RSS feed updates) publish directly to the egress channel:

```kotlin
val provenance = Provenance.decode(subscription.destinationUri)
val message = MessageBuilder
    .withPayload(OperationResult.Success(notificationText))
    .setHeader(Provenance.HEADER, provenance)
    .build()
egressChannel.send(message)
```

This bypasses the operation chain entirely - the notification is output, not input.

### Loop Prevention

Several layers prevent messages from bouncing infinitely:

1. **Bridged flag** - copied messages carry a `streampack_bridged` metadata flag. Messages with this flag are never re-copied.
2. **Egress loop detection** - before sending any reply, each adapter checks whether the text would be re-ingested as a command. If so, the reply is suppressed.
3. **Ingress self-filter** - IRC ignores its own messages. Slack and Discord skip bot messages at the API level.
4. **Hop count** - the OperationService enforces a maximum hop count (default 3) on re-entrant messages.

## Channel Bridging Architecture

Bridging copies messages between channels across protocols.
Each bridge direction is configured separately - one command creates one-way copy, two commands create a full mirror.

### Exclusive Pairing

A channel can only participate in one bridge pair.
This prevents multiplex fan-out situations where a single message cascades through multiple bridges.
The constraint is enforced by partial unique indexes on the `bridge_pair` table.

### Bridge Message Flow

```
User types "hello" in irc://libera/#metal
  -> IrcAdapter dispatches through EventGateway
  -> BridgeCopyOperation (priority 1, unaddressed) runs first:
       1. Looks up copy targets for irc://libera/#metal
       2. Finds discord://123456/#irc
       3. Publishes "<alice@irc> hello" to egress with target provenance
       4. Returns null (chain continues for normal processing)
  -> Remaining operations process the message normally
  -> If an operation produces a result (e.g., karma change):
       BridgeEgressSubscriber copies the result to bridge targets
```

User messages are attributed with the sender's nick and protocol.
Bot responses are copied as-is without additional attribution.

## Tick Channel Infrastructure

The tick channel is a 1-second heartbeat that any component can listen to.
It provides the system with a sense of time - without it, everything is purely reactive.

### Why a Channel

The tick is a Spring Integration `PublishSubscribeChannel`, not a direct callback loop.
This preserves the upgrade path to AMQP in a horizontally-scaled deployment.

### How It Works

`TickService` fires once per second, publishing `Instant.now()` to the `tickChannel`.
Every bean implementing `TickListener` receives the tick.

```
TickService (@Scheduled, 1s)
    |
    v
tickChannel (PublishSubscribeChannel)
    +---> RssFeedPollingService.onTick(now)   -- "has 60 minutes passed? poll feeds"
    +---> GitHubPollingService.onTick(now)    -- "has 60 minutes passed? poll repos"
```

### Implementing a TickListener

```kotlin
@Service
class MyPollingService(
    private val myProperties: MyProperties,
) : TickListener {
    private var lastPollTime: Instant = Instant.EPOCH

    override fun onTick(now: Instant) {
        if (Duration.between(lastPollTime, now) >= myProperties.pollInterval) {
            lastPollTime = now
            doWork()
        }
    }
}
```

Key points:

- The listener decides its own interval. The tick is always 1 second.
- Initialize `lastPollTime` to `Instant.EPOCH` so the first tick triggers immediately.
- Keep `onTick` fast. Slow work blocks the tick channel for other listeners.

### Testing with Ticks

The tick scheduler is disabled in tests via `streampack.tick.scheduler.enabled=false`.
The `tickChannel` bean itself is always present.
Tests publish ticks manually:

```kotlin
@Autowired @Qualifier("tickChannel") lateinit var tickChannel: MessageChannel
tickChannel.send(MessageBuilder.withPayload(Instant.now()).build())
```

### Components

| Class | Purpose |
|-------|---------|
| `TickListener` | Interface with `onTick(now: Instant)` |
| `TickListenerWiring` | Discovers `TickListener` beans, subscribes them to `tickChannel` |
| `TickSchedulingConfiguration` | Enables `@EnableScheduling`, gated on property |
| `TickService` | Publishes `Instant.now()` every second, gated on property |

## Module Taxonomy

Streampack modules fall into three categories:

**Library modules** (`lib-core`, `lib-blog`, `lib-irc`, `lib-slack`, `lib-test`, `lib-polling`, `lib-ai`) provide shared types, base classes, and infrastructure.
They have no operations or protocol adapters.
Everything depends on `lib-core`.

**Operation modules** (`operation-calc`, `operation-karma`, `operation-factoid`, `operation-specs`, `operation-dictionary`, `operation-weather`, `operation-poetry`, `operation-ask`, `operation-sentiment`, `operation-hangman`, `operation-21`, `operation-cal`, `operation-tell`, `operation-markov`) contain operations and their supporting services.
They depend on `lib-core` and nothing else.
They are protocol-agnostic - an operation module has no idea IRC exists.
`operation-markov` is the first polyglot module: a Kotlin shim delegates chain building and generation to a Clojure namespace, demonstrating JVM language interop without framework coupling.

**Service modules** (`service-irc`, `service-slack`, `service-console`, `service-blog`, `service-factoid`, `service-rss`, `service-github`, `service-bridge`) contain protocol adapters, egress subscribers, and protocol-specific logic.
They may also contain operations when the operation is tightly coupled to the service (like RSS feed management commands or IRC admin commands).

The `app` module assembles everything.
Including a module in `app/pom.xml` activates it; removing it deactivates it.

## HTTP Security Layer

The blog service uses stateless JWT authentication:

- `JwtAuthenticationFilter` extracts and validates JWT tokens from the `Authorization` header
- `SecurityFilterChain` configures endpoint-level access rules (public vs. authenticated vs. admin)
- Tokens are issued during OTP verification and OIDC callback

See [Authentication](authentication.md) for the full auth flow.

## Building a Service Module

Service modules bridge external protocols to the messaging system.
A service module typically contains:

- A **protocol adapter** that listens for input and constructs messages
- An **egress subscriber** that delivers results back to the protocol
- **Configuration** for enabling/disabling the service
- Optionally, **operations** that are tightly coupled to the service

### Conditional Activation

Services use Spring Boot's conditional properties:

```kotlin
@Configuration
@ConditionalOnProperty(prefix = "streampack.myservice", name = ["enabled"], havingValue = "true")
class MyServiceAutoConfiguration
```

When the property is not set or is `false`, the entire service is absent from the application context.

### The Adapter Pattern

A protocol adapter's job:

1. Receive protocol-specific input (HTTP request, IRC message, stdin line)
2. Resolve user identity
3. Build a `Provenance`
4. Construct a `Message<*>` with the appropriate payload
5. Send through `EventGateway`
6. Translate the `OperationResult` back to a protocol-specific response (for synchronous protocols like HTTP)

## Testing Patterns

### Integration Tests Over Mocks

The project prefers integration tests that exercise the full pipeline.
When an internal service provides behavior, use the internal service - do not mock it.

Operation tests send messages through `EventGateway` and assert `OperationResult` types:

```kotlin
@SpringBootTest
@Transactional
class MyOperationTests {
    @Autowired lateinit var eventGateway: EventGateway

    @Test
    fun `valid input returns success`() {
        val result = eventGateway.process(message("my command"))
        assertInstanceOf(OperationResult.Success::class.java, result)
    }
}
```

### Test Infrastructure

The `lib-test` module provides:

- **`application.yml`** with Testcontainers PostgreSQL and Flyway configuration
- **`TestChannelConfiguration`** that replaces the `ExecutorChannel` with a `DirectChannel`, forcing operations to run on the test thread so `@Transactional` rollback works

### Local HTTP Servers for External APIs

For operations that make HTTP calls (RSS, specs, weather), use `com.sun.net.httpserver.HttpServer` in tests:

```kotlin
httpServer = HttpServer.create(InetSocketAddress(0), 0)
httpServer.start()
baseUrl = "http://localhost:${httpServer.address.port}"
```

Port 0 lets the OS assign a free port, avoiding conflicts in parallel test runs.

### What to Test

Cover well:
- Domain logic (operations, validation, state transitions)
- Translation layer (string parsing in `translate()`)
- Authorization rules
- Positive cases, error cases, and "not my problem" cases

Acceptable to skip:
- Exception handlers for infrastructure failures
- Framework integration boilerplate
- Trivial getters and DTOs

## Conventions

### File Organization

One public class per file.
Inner classes and sealed class variants can live with their parent.
When a type is reusable across classes, give it its own file.

### Logging

Always SLF4J. Never `println()`.

Use parameterized messages:

```kotlin
logger.debug("Processing feed {}: {} entries", feed.title, entries.size)
```

SLF4J parameterization skips string building when the log level is disabled.

### Comments

Comments explain *what* and *why*, not *how*.

### Formatting

Spotless with ktfmt (KOTLINLANG style) runs automatically at compile time.

### No Unicode in Messages

Status messages, log output, and user-facing text must not contain emoji or special Unicode characters.

### Error Handling

Operations return `OperationResult.Error` for user-facing errors.
Services return sealed result types (not exceptions) for expected outcomes.
Exceptions are for unexpected infrastructure failures, not business logic.
