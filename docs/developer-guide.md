# Developer Guide

This is the architecture guide for Streampack, the codebase behind Nevet.
It covers how the system works, how to build new capabilities, and what conventions to follow.
If you read one document about building for Streampack, make it this one.

For getting a development instance running, see [getting-started.md](getting-started.md).
For the event system internals in detail, see [event-system.md](event-system.md).

## Design Philosophy

Streampack is built on a single idea: protocols are interchangeable, behavior is not.

A factoid lookup means the same thing whether it comes from IRC, Discord, a REST call, or the console.
The system normalizes all input into a common message format, routes it through a shared pool of operations, and delivers output back through the originating protocol.

Three principles follow from this:

**Operations never know where a message came from.**
They receive typed payloads and Provenance headers.
They return results.
The protocol adapter built the message on the way in; a different adapter delivers the result on the way out.

**Services contain domain logic; operations contain routing logic.**
A `FactoidService` knows how to store and retrieve factoids.
A `FactoidGetOperation` knows that `"spring"` is a factoid query and that the result should be `"spring is A Java framework."`.
The service is reusable from REST controllers, tests, or other operations.
The operation is the bridge between the messaging world and the service world.

**Make it work, make it pretty, make it fast -- in that order.**
Correctness first.
Clean code second, because clean code makes bugs visible.
Performance last, because you can not optimize what you can not read.

## The Messaging Pipeline

Every message flows through three stages:

```
Protocol Adapter  -->  ingressChannel  -->  OperationService  -->  egressChannel  -->  Protocol Adapter
     (input)          (ExecutorChannel,       (operation chain)    (PublishSubscribe)       (output)
                       virtual threads)
```

**Ingress**: a protocol adapter (IRC listener, HTTP controller, console reader) constructs a `Message<*>` with a payload and a `Provenance` header, then sends it to the `EventGateway`.

**Processing**: the `OperationService` runs the message through every registered operation in priority order.
The first operation to return a terminal result (`Success` or `Error`) wins.
If no operation handles the message, the result is `NotHandled`.

**Egress**: the result is published to the egress channel with the original `Provenance` header intact.
Every `EgressSubscriber` sees the message; each one checks whether the provenance matches its protocol and delivers accordingly.

The ingress channel uses virtual threads, so each message gets its own lightweight thread.
The egress channel is synchronous -- subscribers run on the operation chain's thread to preserve ordering.

## Module Taxonomy

Streampack modules fall into three categories:

**Library modules** (`lib-core`, `lib-blog`, `lib-irc`, `lib-test`) provide shared types, base classes, and infrastructure.
They have no operations or protocol adapters.
Everything depends on `lib-core`.

**Operation modules** (`operation-calc`, `operation-karma`, `operation-factoid`) contain operations and their supporting services.
They depend on `lib-core` and nothing else.
They are protocol-agnostic -- an operation module has no idea IRC exists.

**Service modules** (`service-irc`, `service-console`, `service-blog`, `service-rss`) contain protocol adapters, egress subscribers, and protocol-specific logic.
They depend on `lib-core` and may depend on library modules for shared types.
They may also contain operations when the operation is tightly coupled to the service (like RSS feed management commands).

The `app` module assembles everything.
Including a module in `app/pom.xml` activates it; removing it deactivates it.
No configuration changes needed -- Spring component scanning handles discovery.

## Building an Operation

### Choosing a Base Class

Three base classes exist, and the choice matters:

**`TypedOperation<T>`** is the default choice.
Use it when your operation handles a single payload type.
The outer `canHandle` rejects wrong types automatically; you override the inner `canHandle` for content-based filtering.
Most operations use this.

**`TranslatingOperation<T>`** is for operations that need dual-input access.
When the payload is a `String`, the `translate()` method parses it into a typed request.
When the payload is already the correct type (from a REST controller or another operation), `translate()` is never called.
Use this when your operation needs to work from both interactive text (IRC, console) and structured API calls.

**`Operation`** (the raw interface) is for unusual cases -- multiple payload types, raw message header inspection, or routing logic that does not fit the typed pattern.
You probably do not need this.

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
   It handles type resolution -- checking whether the payload is the right type (TypedOperation) or translating a string into the typed request (TranslatingOperation).
   You cannot override this.

2. The **inner** `canHandle(payload, message)` is `open`.
   It receives the already-resolved typed payload.
   Override this for content-based filtering: checking command prefixes, verifying authorization, or any other pre-flight logic.

This separation means you never write type-checking boilerplate.
The framework handles type resolution; you handle business logic.

### Priority

Priority determines execution order.
Lower numbers run first.

- **0-20**: high priority, runs before most things
- **50**: the default, appropriate for most operations
- **65-75**: mid-range, used when you want to run after common operations
- **90+**: catch-all territory, runs last

Priorities do not need to be unique.
Operations at the same priority run in an undefined but stable order.
Most operations should leave the default (50) unless there is a specific reason to run earlier or later.

A catch-all operation (like factoid get, which tries to match any addressed text) should use a high number so that more specific operations get first shot.

### The addressed Flag

When `addressed` is `true` (the default), protocol adapters only send messages that were explicitly directed at the bot -- prefixed with the signal character (`!`) or the bot's nick.

When `addressed` is `false`, the adapter checks all messages against the operation.
This is for operations that monitor ambient conversation, like karma tracking (`foo++` does not require addressing the bot).

An operation's `addressed` flag is a hint to protocol adapters, not a hard gate.
Console and HTTP input is always treated as addressed regardless of this flag.

### Authorization

Authorization checks belong in `canHandle`, not in `handle`.

When `canHandle` returns `false`, the operation is skipped and the chain continues.
This means an unauthorized user's message falls through to the next operation rather than producing an error.
The message might be handled by a different operation, or it might reach `NotHandled` -- either way, the unauthorized operation is invisible to the user.

The pattern:

```kotlin
override fun canHandle(payload: MyRequest, message: Message<*>): Boolean {
    val provenance = message.headers[Provenance.HEADER] as? Provenance
    val role = provenance?.user?.role ?: Role.GUEST
    return role >= Role.ADMIN
}
```

Roles are hierarchical: `GUEST < USER < ADMIN < SUPER_ADMIN`.
Comparing with `>=` means "this role or higher."

For operations with mixed access -- some commands public, some admin-only -- check the specific command in `canHandle`:

```kotlin
override fun canHandle(payload: String, message: Message<*>): Boolean {
    val trimmed = payload.trim().lowercase()
    // Read commands are open to everyone
    if (trimmed == "feed list" || trimmed == "feed subscriptions") return true
    // Mutation commands require ADMIN
    val isMutation = trimmed.startsWith("feed subscribe ") ||
        trimmed.startsWith("feed remove ")
    if (!isMutation) return false
    val provenance = message.headers[Provenance.HEADER] as? Provenance
    val role = provenance?.user?.role ?: Role.GUEST
    return role >= Role.ADMIN
}
```

### The handle Method

`handle` contains the business logic.
It receives the typed payload and returns an `OperationOutcome`:

- **`OperationResult.Success(payload)`** -- the operation handled the message and produced a response.
  Stops the chain.
- **`OperationResult.Error(message)`** -- the operation handled the message but encountered a failure.
  Stops the chain.
- **`null`** -- the operation thought it could handle the message but could not (e.g., factoid lookup found no match).
  Chain continues silently.
- **`Declined(reason)`** -- the operation recognized the message but chose not to handle it, with a diagnostic reason that gets logged.
  Chain continues.

The distinction between `null` and `Declined` is logging: `null` is silent, `Declined` leaves a trace for debugging.

## Inter-Operation Communication

Operations can communicate by sending messages through the `EventGateway`.
Inject the gateway and call `process()` to get a synchronous result:

```kotlin
@Component
class MyOperation(
    private val eventGateway: EventGateway,
) : TypedOperation<String>(String::class) {

    override fun handle(payload: String, message: Message<*>): OperationOutcome {
        // Build a typed request for another operation
        val request = SomeOtherRequest(data = payload)
        val msg = MessageBuilder.withPayload(request)
            .setHeader(Provenance.HEADER, message.headers[Provenance.HEADER])
            .build()
        val result = eventGateway.process(msg)
        // Use the result or ignore it
        return OperationResult.Success("Done")
    }
}
```

This keeps operations decoupled.
The calling operation does not depend on the target operation's module.
If the target operation is not on the classpath, the message returns `NotHandled` and nothing breaks.

### The Cache Pattern: Specs and Factoids

A powerful pattern emerges when one operation populates another's data store.

Consider the planned specs operation.
A user types `~jsr 52`.
The factoid get operation (priority 90) checks first -- no factoid exists for `"jsr 52"`, so it returns `null` and the chain continues.
The specs operation (priority 95 or similar) catches the miss, looks up JSR 52 from jcp.org, creates a factoid via `EventGateway.process()`, and returns the result.

Next time someone types `~jsr 52`, the factoid already exists.
The factoid get operation handles it at priority 90 and the specs operation never fires.
The spec lookup becomes a cache-miss handler that populates the factoid system.

This also means users can enrich spec entries with `seealso`, custom descriptions, and other factoid attributes -- because they are real factoids, they get the full factoid feature set for free.

The pattern works for any operation that can populate another operation's backing store on miss.

## Provenance

Provenance tracks where a message came from and where the response should go.
It flows through the entire pipeline as a message header.

```kotlin
data class Provenance(
    val protocol: Protocol,        // IRC, CONSOLE, HTTP, DISCORD, etc.
    val serviceId: String? = null,  // "libera", "discord-guild-id", etc.
    val user: UserPrincipal? = null, // resolved user identity, may be null
    val replyTo: String,            // "#java", "local", "posts", etc.
    val alsoNotify: List<String> = emptyList(), // cross-protocol notification URIs
)
```

### URI Encoding

Provenance encodes to and decodes from URIs for storage and cross-protocol routing:

- `irc://libera/%23java` -- IRC, Libera network, #java channel
- `discord://jvm-community/%23java-help` -- Discord guild and channel
- `mailto:///dreamreal@gmail.com` -- email, no service intermediary (triple-slash)
- `console:///local` -- console adapter

The subscription system uses these URIs as destination addresses.
An RSS subscription stores `irc://libera/%23java` as its destination, and the polling service decodes it back to a Provenance for egress delivery.

### User Resolution

Protocol adapters resolve user identity before building the Provenance.
An IRC adapter maps the nick `dreamreal` on `libera` to a `UserPrincipal` with `role = SUPER_ADMIN` via the `ServiceBinding` table.
An HTTP adapter resolves the JWT token to a `UserPrincipal`.

If no binding exists, `user` is null and the effective role is `GUEST`.

## Egress and Notifications

### EgressSubscriber

Egress subscribers deliver operation results to their respective protocols.
Each subscriber extends `EgressSubscriber` and implements two methods:

- **`matches(provenance)`** -- does this subscriber handle this protocol? (e.g., `provenance.protocol == Protocol.IRC`)
- **`deliver(result, provenance)`** -- send the result to the destination specified in the provenance

All subscribers see every egress message; they self-select via `matches`.
If no subscriber matches, the message is silently dropped.

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

This bypasses the operation chain entirely.
The notification is output, not input -- it does not need to be processed by operations.
The provenance header tells the egress subscribers where to deliver it.

## Building a Service Module

Service modules are more complex than operation modules because they bridge external protocols to the messaging system.
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
This means the app module can include the dependency in its POM without the service activating by default.

### The Adapter Pattern

A protocol adapter's job is simple:

1. Receive protocol-specific input (HTTP request, IRC message, stdin line)
2. Resolve user identity
3. Build a `Provenance`
4. Construct a `Message<*>` with the appropriate payload
5. Send through `EventGateway`
6. Translate the `OperationResult` back to a protocol-specific response (for synchronous protocols like HTTP)

The adapter strips protocol-specific decoration (IRC signal characters, HTTP routing) and normalizes input before sending it through the gateway.

## Testing

### Integration Tests Over Mocks

The project prefers integration tests that exercise the full pipeline.
When an internal service provides behavior, use the internal service -- do not mock it.

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

    @Test
    fun `unrecognized input is not handled`() {
        val result = eventGateway.process(message("something else"))
        assertInstanceOf(OperationResult.NotHandled::class.java, result)
    }
}
```

### Test Infrastructure

The `lib-test` module provides:

- **`application.yml`** with Testcontainers PostgreSQL and Flyway configuration
- **`TestChannelConfiguration`** that replaces the `ExecutorChannel` with a `DirectChannel`, forcing operations to run on the test thread so `@Transactional` rollback works

Add `lib-test` as a test-scoped dependency and these are activated automatically.

### Local HTTP Servers

For operations that make HTTP calls (RSS, specs, weather), use `com.sun.net.httpserver.HttpServer` in tests to serve canned responses:

```kotlin
private lateinit var httpServer: HttpServer

@BeforeEach
fun setUp() {
    httpServer = HttpServer.create(InetSocketAddress(0), 0)
    httpServer.start()
    baseUrl = "http://localhost:${httpServer.address.port}"
}

@AfterEach
fun tearDown() {
    httpServer.stop(0)
}
```

Port 0 lets the OS assign a free port, avoiding conflicts in parallel test runs.
This avoids external dependencies in tests and gives full control over responses.

### What to Test

Cover well:
- Domain logic (operations, validation, state transitions)
- The translation layer (string parsing in `translate()`)
- Authorization rules (admin vs guest vs user)
- Positive cases, error cases, and "not my problem" cases

Acceptable to skip:
- Exception handlers for infrastructure failures
- Framework integration boilerplate
- Trivial getters and DTOs

### A Note on @Transactional in Tests

`@Transactional` on a test class rolls back all database changes after each test.
This works well for operations that run on the test thread (which `TestChannelConfiguration` ensures for the operation chain).

However, if an operation spawns its own transaction (like a `@Scheduled` polling method), the test's transaction boundary does not cover it.
In those cases, use explicit cleanup in `@AfterEach` instead of relying on `@Transactional` rollback.

## Conventions

### File Organization

One public class per file.
Inner classes and sealed class variants can live with their parent.
But when a type is reusable across classes, give it its own file.

This makes classes findable by filename and gives the compiler clean dependency signals when refactoring.

### Logging

Always SLF4J.
Never `println()` or `System.out.println()`.

Use parameterized messages to avoid string concatenation:

```kotlin
logger.debug("Processing feed {}: {} entries", feed.title, entries.size)
```

Not:

```kotlin
logger.debug("Processing feed ${feed.title}: ${entries.size} entries")
```

SLF4J parameterization skips string building when the log level is disabled.

### Comments

Comments explain *what* and *why*, not *how*.
The code explains how.

Good: `/** Catch-all factoid lookup; returns null when no match so chain continues */`
Bad: `// Loop through the list and check each item`

### Formatting

Spotless with ktfmt (KOTLINLANG style) runs automatically at compile time.
Do not fight the formatter.
If Spotless reformats your code, that is the correct formatting.

### No Unicode in Messages

Status messages, log output, and user-facing text must not contain emoji or special Unicode characters.
Unicode in test data or string literals is fine when deliberately needed.

### Error Handling

Operations return `OperationResult.Error` for user-facing errors.
Services return sealed result types (not exceptions) for expected outcomes.
Exceptions are for unexpected infrastructure failures, not business logic.

## Where to Find Things

| Topic | Document |
|-------|----------|
| Running a dev instance | [getting-started.md](getting-started.md) |
| Event system internals | [event-system.md](event-system.md) |
| REST API endpoints | [api-reference.md](api-reference.md) |
| User operations API | [api-user-operations.md](api-user-operations.md) |
| IRC service commands | [irc-service.md](irc-service.md) |
| RSS service commands | [rss-service.md](rss-service.md) |
| JWT and security | [jwt-security-integration.md](jwt-security-integration.md) |
| Project overview and module map | [CLAUDE.md](../CLAUDE.md) (the project root) |
