# Messaging Architecture

Streampack is built around protocol-independent behavior, so each adapter translates into shared operations instead of owning domain logic.

## Design Philosophy

The architecture treats protocols as replaceable shells around stable business behavior.

Adapters are intentionally thin. They accept protocol-native input, convert it into a common message form, and delegate to shared operations and services. Domain rules stay reusable across IRC, Slack, Discord, console, and HTTP.

This keeps behavior aligned across surfaces and reduces duplicated logic when new adapters or APIs are added.

## Message Pipeline

Every inbound message passes through a common ingress, operation, and egress flow that preserves provenance.

Protocol adapters construct `Message<*>` objects with a payload and provenance, send them into the ingress channel, and let the operation chain evaluate handlers in priority order. The first terminal success or error wins. Results are then published on the egress channel so matching subscribers can deliver them through the right protocol.

This model lets interactive chat messages, console commands, and HTTP-backed operations reuse the same processing path.

## Operations

Operations are the routing layer between incoming messages and reusable domain services.

They decide whether a message is relevant, translate text into typed requests when needed, enforce preconditions, and return outcomes. Services hold durable business logic; operations decide how that logic is invoked from the messaging world.

### Base Classes

Three operation styles cover the common handling patterns without forcing every module into the same shape.

- `TypedOperation<T>` is the default for one typed payload
- `TranslatingOperation<T>` handles both string commands and typed requests
- `Operation` exists for unusual routing or multi-payload cases

The two-tier `canHandle` pattern keeps type resolution separate from content checks so operations stay concise.

### Priority and Addressing

Operation order and addressing flags control which handlers see a message and when they run.

Lower priority values run earlier. Most addressed commands only activate when a user explicitly targets the bot, while ambient features like karma can opt into unaddressed traffic. This allows catch-all handlers and conversational listeners to coexist without constant conflicts.

### Authorization

Role checks are hierarchical and are usually enforced at the operation boundary rather than in protocol adapters.

Roles progress from `GUEST` to `SUPER_ADMIN`. Operations can silently fall through when a user lacks access, or they can return explicit authorization errors when a guarded command should stop the chain.

Authentication and identity rules are described in [[authentication#Authentication and Identity]].

## Advanced Routing

The event system supports patterns beyond a single reply to the caller when a feature needs fan-out or redirected delivery.

### Provenance Override

Operations can direct output to a different destination than the source message when the business flow requires it.

This pattern supports cases like telling another user something later or replying in a private context even if the trigger came from a shared channel.

### FanOut and Loopback

Some operations need to publish multiple outputs or feed generated content back into the pipeline for further handling.

`FanOut` supports multi-recipient behavior, while loopback allows an operation to emit follow-up input for other operations. These mechanisms exist so complex workflows can stay in the same messaging model instead of bypassing it.

## Channel Infrastructure

The runtime uses specialized channels to isolate ingress processing, egress delivery, bridging, and scheduled behavior.

### Egress and Loop Prevention

Egress subscribers deliver only the messages meant for their protocol and avoid reprocessing their own output.

Loop prevention matters because the same logical message may cross multiple adapters or be echoed by external systems. The provenance system and egress filtering keep those flows from becoming infinite cycles.

### Channel Bridging

Bridge features pair channels explicitly so messages can flow between communities without opening arbitrary relay paths.

Exclusive pairing prevents accidental cross-network leakage and keeps bridge behavior predictable for administrators.

### Tick Channel

Periodic work is modeled as channel traffic so scheduled behavior uses the same composition style as regular message handling.

Tick listeners can trigger polling, cleanup, or maintenance work without inventing a separate execution model.

## Module Taxonomy

Modules are split by responsibility so shared logic can be reused without dragging every adapter into every runtime.

Core libraries define shared domain and infrastructure, operation modules define message handling, and service modules expose transport-specific adapters. The assembly module produces the bootable application, while UI modules provide end-user frontends.

This structure supports adding new operations or adapters without collapsing responsibilities into one large service.

## Testing Strategy

The preferred testing model exercises real integration paths instead of hiding behavior behind heavy mocking.

The project favors Spring integration tests, shared test infrastructure, local HTTP doubles for outbound integrations, and realistic persistence flows with Testcontainers. The goal is to verify actual runtime wiring, not just isolated method calls.

Deployment and environment constraints for these tests are summarized in [[deployment#Deployment and Operations#Local Development]].
