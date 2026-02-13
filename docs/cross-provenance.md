# Cross-Provenance Redirection

Nevet can copy messages between channels across protocols, deliver point-to-point messages, and detect command loops before they happen.
These features share a foundation: provenance URIs as universal channel addresses.

## Provenance URIs

Every channel, DM, or destination in the system has a provenance URI.
This is the address you use for bridge commands and tell commands.

The format is `protocol://serviceId/replyTo`, where special characters are percent-encoded.

### Examples

| Environment | Provenance URI |
|-------------|---------------|
| IRC channel #java on Libera | `irc://libera/%23java` |
| IRC channel #metal on OFTC | `irc://oftc/%23metal` |
| IRC private message to user "blue" on Libera | `irc://libera/blue` |
| Discord channel #general in guild 123456 | `discord://123456/%23general` |
| Slack channel C0ABC123 in workspace "team" | `slack://team/C0ABC123` |
| Console | `console:///local` |

The `#` in channel names becomes `%23` because `#` is a reserved URI character.

### Discovering your provenance

From any channel, type:

```
!bridge provenance
```

This returns the provenance URI for the channel you're in.
No special permissions are required.

**Typical workflow for setting up a bridge:**

1. Go to the IRC channel, type `!bridge provenance` - get `irc://libera/%23metal`
2. Go to the Discord channel, type `!bridge provenance` - get `discord://123456/%23irc`
3. From either channel (as an admin): `!bridge copy irc://libera/%23metal discord://123456/%23irc`

## Channel Bridging

Bridging copies messages from one channel to another.
Each bridge direction is configured separately - one command creates a one-way copy, two commands create a full mirror.

### Core concepts

**Directional.**
`bridge copy A B` means "copy content from A to B."
Messages in A appear in B.
Messages in B do not appear in A unless you also run `bridge copy B A`.

**Exclusive pairing.**
A channel can only participate in one bridge pair.
If `#metal` and `#irc` are paired, no third channel can bridge to either of them.
This prevents multiplex fan-out situations where a single message cascades through multiple bridges.

**Provenance-addressed.**
Bridge endpoints are identified by provenance URI, which means they work across protocols.
A bridge target is also a valid destination for `tell` commands.

### Commands

All bridge mutation commands require ADMIN role.
The `bridge provenance` command is available to all users.

| Command | Effect |
|---------|--------|
| `bridge provenance` | Show the provenance URI for the current channel |
| `bridge copy <source-uri> <target-uri>` | Copy content from source to target |
| `bridge remove <source-uri> <target-uri>` | Remove a directional copy |
| `bridge list` | Show all configured bridge pairs |
| `bridge` | Show available commands |

### Setup examples

**One-way copy** - IRC content appears in Discord, but not the reverse:

```
!bridge copy irc://libera/%23metal discord://123456/%23irc
Bridge established: irc://libera/%23metal -> discord://123456/%23irc
```

**Full mirror** - both channels see each other's content:

```
!bridge copy irc://libera/%23metal discord://123456/%23irc
Bridge established: irc://libera/%23metal -> discord://123456/%23irc

!bridge copy discord://123456/%23irc irc://libera/%23metal
Bridge established: discord://123456/%23irc -> irc://libera/%23metal
```

**Adding reverse to existing pair** - since the two URIs are already paired, the second command just enables the reverse direction.

**Rejected - third party joining an existing pair:**

```
!bridge copy slack://team/C0ABC123 discord://123456/%23irc
Error: discord://123456/%23irc is already paired with irc://libera/%23metal
```

### Removing bridges

Remove one direction:

```
!bridge remove irc://libera/%23metal discord://123456/%23irc
Bridge removed: irc://libera/%23metal -> discord://123456/%23irc
```

If the pair was a full mirror, the reverse direction remains active.
If this was the only direction, the pair is dissolved and both URIs become free for new bridges.

### How bridged messages appear

User messages are attributed with the sender's nick and protocol:

```
<alice@irc> anyone tried the new JDK build?
```

Bot responses (operation results like factoid lookups or karma changes) are copied as-is, without additional attribution.

### Loop prevention

Several layers prevent messages from bouncing infinitely:

1. **Bridged flag** - copied messages carry a `streampack_bridged` metadata flag. Messages with this flag are never re-copied.
2. **Egress loop detection** - before sending any reply, each adapter checks whether the text would be re-ingested as a command (e.g., a factoid whose value starts with `!`). If so, the reply is suppressed with a log warning.
3. **Ingress self-filter** - the IRC adapter ignores its own messages. Slack and Discord skip bot messages at the API level.
4. **Hop count** - the OperationService enforces a maximum hop count (default 3) on re-entrant messages, catching runaway fan-outs.

## Tell Operation

The `tell` command delivers a one-shot message to any destination.
It requires addressing (signal character or bot nick).

### Syntax

```
!tell <target> <message>
```

### Target resolution

The target inherits context from the channel where the command was issued.

| From `irc://libera/#java` | Input | Resolved target |
|---------------------------|-------|----------------|
| Just a name | `!tell blue go away` | `irc://libera/blue` (private message) |
| Channel name | `!tell #metal check this out` | `irc://libera/#metal` |
| Full URI | `!tell discord://123456/%23general hello from IRC` | `discord://123456/%23general` |

When the target is a bare name, it resolves to a private message on the same protocol and service.
When the target is a channel name (starts with `#`), it resolves to that channel on the same protocol and service.
When the target contains `://`, it is used as a full provenance URI - this is how you send cross-protocol messages.

### Attribution

Messages delivered via `tell` include the sender's nick:

```
<alice> check this out
```

### Examples

Send a private message to someone on the same IRC network:

```
!tell blue meeting in 5 minutes
Message delivered to blue
```

Send a message to another IRC channel:

```
!tell #metal new album dropped
Message delivered to #metal
```

Send a cross-protocol message from IRC to Discord:

```
!tell discord://123456/%23general server maintenance tonight
Message delivered to #general
```

## Database Schema

### bridge_pair

One row per exclusive channel pair.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (v7) | Primary key |
| `first_uri` | VARCHAR(500) | First provenance URI (unique among active rows) |
| `second_uri` | VARCHAR(500) | Second provenance URI (unique among active rows) |
| `copy_first_to_second` | BOOLEAN | Whether content flows from first to second |
| `copy_second_to_first` | BOOLEAN | Whether content flows from second to first |
| `deleted` | BOOLEAN | Soft delete flag |
| `created_at` | TIMESTAMP | When the pair was created |

Partial unique indexes on `first_uri` and `second_uri` (where `deleted = false`) enforce that each URI appears in at most one active pair.
The cross-column uniqueness constraint (a URI cannot appear as `first_uri` in one row and `second_uri` in another) is enforced in the service layer.

## Architecture

### Module structure

```
service-bridge (operations, service, entities, migration)
  depends on: lib-core

operation-tell (operation, model)
  depends on: lib-core
```

Both are included in the `app` module POM.
Removing either from the app POM disables its functionality.

### Bridge message flow

When a user types a message in a bridged channel:

```
User types "hello everyone" in irc://libera/#metal
  -> IrcAdapter dispatches through EventGateway
  -> BridgeCopyOperation (priority 1, unaddressed) runs first:
       1. Looks up copy targets for irc://libera/#metal
       2. Finds discord://123456/#irc
       3. Publishes "<alice@irc> hello everyone" to egress with target provenance
       4. Returns null (chain continues for normal processing)
  -> Remaining operations process the message normally
  -> If an operation produces a result (e.g., karma change):
       BridgeEgressSubscriber copies the result to bridge targets
```

### Tell message flow

```
User types "!tell discord://123456/#general hello" in irc://libera/#java
  -> IrcAdapter strips signal character, dispatches "tell discord://123456/#general hello"
  -> TellOperation:
       1. Parses target URI and message
       2. Publishes "<alice> hello" to egress with target provenance
       3. Returns Success("Message delivered to #general") to sender
```

### ProtocolAdapter interface

All adapters (IRC, Slack, Discord) implement a common interface that supports loop detection:

```kotlin
interface ProtocolAdapter {
    val protocol: Protocol
    val serviceName: String
    fun wouldTriggerIngress(text: String): Boolean
    fun sendReply(provenance: Provenance, text: String)
}
```

`wouldTriggerIngress` mirrors each adapter's command detection logic - it returns true if the text starts with the signal character or a bot mention pattern.
Egress subscribers call this before sending to prevent command loops.

`sendReply` routes a text message to the destination described by the provenance.
This centralizes the routing logic that was previously duplicated across egress subscribers.
