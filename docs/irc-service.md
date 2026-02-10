# IRC Service Configuration Guide

The IRC service connects Nevet to IRC networks as a channel bot.
All network and channel configuration lives in the database.
The only application properties are the on/off switch and the default signal character.

## Activation

The IRC runtime (connecting to servers, joining channels, relaying messages) is controlled by two properties:

```yaml
streampack:
  irc:
    enabled: true             # false by default
    signal-character: "!"     # default trigger prefix
```

When `enabled` is `false` (the default), the `IrcConnectionManager` bean is not created.
Admin commands (`irc connect`, `irc join`, etc.) still work and persist configuration to the database, but no actual IRC connections are made.
This is deliberate - it means you can pre-configure networks and channels in a test or staging environment, then flip the switch to `true` in production.

When `enabled` is `true`, the connection manager starts on application boot, reads all networks with `autoconnect = true` from the database, and connects to them.
For each connected network, it joins all channels with `autojoin = true`.

### Environment variable

In the production `application.yml`, the property is:

```yaml
streampack:
  irc:
    enabled: ${IRC_ENABLED:false}
    signal-character: ${IRC_SIGNAL:!}
```

Set the `IRC_ENABLED` environment variable to `true` to activate IRC at deploy time.
The `IRC_SIGNAL` variable overrides the global signal character (see Message Addressing below).

## Database as Startup Manifest

There are no YAML blocks for IRC server addresses, channels, or credentials.
Everything is stored in three database tables:

- **irc_networks** - one row per IRC server (host, port, TLS, nick, SASL credentials, autoconnect flag, optional signal character override)
- **irc_channels** - one row per channel per network (autojoin, automute, visible, logged flags)
- **irc_messages** - immutable log of channel activity (when logging is enabled for a channel)

The database is the source of truth for what to connect to on startup.
Runtime state (which channels are currently muted, which are currently being logged) is held in memory and initialized from the database flags when the adapter connects.

## Admin Commands

All IRC configuration is done through the `irc` admin command, which requires `SUPER_ADMIN` role.
These commands work through any protocol adapter (console, HTTP, IRC itself, eventually Discord/Slack).

### Network Management

| Command | Effect |
|---------|--------|
| `irc connect <name> <host> <nick> [saslAccount] [saslPassword]` | Register network in DB and connect (if IRC runtime is active) |
| `irc disconnect <name>` | Disconnect from network (runtime only, entity remains) |
| `irc autoconnect <name> <true\|false>` | Set whether this network connects on startup |
| `irc signal <name> [character]` | Set per-network signal character (omit character to reset to global default) |
| `irc status [name]` | Show connection state for one or all networks |

The `name` is a short identifier used in all subsequent commands (e.g., "libera", "oftc").
It must be unique.

Port defaults to 6697 and TLS defaults to true.
These can be changed by editing the entity directly (no admin command yet - this is a TODO for future work).

### Channel Management

| Command | Effect |
|---------|--------|
| `irc join <network> <#channel>` | Register channel in DB and join (if connected) |
| `irc leave <network> <#channel>` | Leave channel (runtime only, entity remains) |
| `irc autojoin <network> <#channel> <true\|false>` | Set whether this channel is joined on connect |
| `irc automute <network> <#channel> <true\|false>` | Set whether this channel starts muted |
| `irc visible <network> <#channel> <true\|false>` | Set whether this channel appears in UI |
| `irc logged <network> <#channel> <true\|false>` | Set whether messages are stored to the database |

### Runtime Controls

| Command | Effect |
|---------|--------|
| `irc mute <network> <#channel>` | Mute channel now (bot stops responding, still logs if logging is on) |
| `irc unmute <network> <#channel>` | Unmute channel now |

Mute/unmute only affect the current session.
Use `automute` to persist the muted state across restarts.

### Help

| Command | Effect |
|---------|--------|
| `irc` | Show all available subcommands |

## Typical Setup Workflow

Starting from a fresh database with the console adapter enabled:

```
> irc connect libera irc.libera.chat nevet
Network 'libera' registered. Connecting...

> irc join libera #java
Channel '#java' registered on 'libera'. Joining...

> irc autojoin libera #java true
Channel '#java' on 'libera' autojoin set to true

> irc autoconnect libera true
Network 'libera' autoconnect set to true

> irc logged libera #java true
Channel '#java' on 'libera' logged set to true

> irc status
  libera: 1 channel(s) [#java]
```

After this, the bot will automatically connect to Libera and join #java on every startup (as long as `streampack.irc.enabled=true`).

## Message Addressing

Not every message in a channel is intended for the bot.
The IRC adapter uses a gate to decide which messages to process.

### Addressed messages

A message is "addressed" if it starts with either:

- The **signal character** (default `!`): `!calc 2+3`
- The **bot's nick** followed by `: ` or `, `: `nevet: calc 2+3` or `nevet, calc 2+3`

When a message is addressed, the prefix is stripped and the remaining text is dispatched through the EventGateway.
So `!calc 2+3` and `nevet: calc 2+3` both arrive at operations as `calc 2+3`.

### Unaddressed messages

Messages without a signal character or bot nick prefix are "unaddressed."
Most operations (like `calc`) require addressing - they set `addressed = true` (the default on the `Operation` interface).

Some operations don't require addressing.
For example, a future karma operation would match `eclipse++` without any prefix.
These operations set `addressed = false`.

When an unaddressed message arrives, the adapter performs a **pre-scan**: it asks `OperationService.hasUnaddressedInterest(message)` whether any non-addressed operation claims interest via `canHandle`.
If at least one does, the raw message (no stripping) is dispatched through the EventGateway.
If none are interested, the message is dropped silently.

This means the full operation chain only runs when there's a reason to run it.

### Signal character configuration

The signal character has two levels:

1. **Global default** in `application.yml`: `streampack.irc.signal-character` (default `!`)
2. **Per-network override** in the database: set via `irc signal <name> <character>`

The effective signal character for a network is the per-network override if set, otherwise the global default.
To reset a network back to the global default: `irc signal <name>` (omit the character).

### Private messages

Private messages (DMs to the bot) bypass the gate entirely.
They are always dispatched through the EventGateway without any prefix stripping.

## Architecture Layers

The IRC service has four layers, from outermost to innermost:

### IrcAdminOperation

A `TypedOperation<String>` that matches the `"irc"` prefix.
Parses subcommands and delegates to IrcService.
Requires SUPER_ADMIN role.
This is a Spring bean registered in the global operation chain.

### IrcService

A `@Component` that handles entity CRUD (persist networks, channels, update flags).
For runtime operations (connect, join, mute), it delegates to `IrcConnectionManager` via `ObjectProvider`.
The `ObjectProvider` pattern means IrcService works correctly even when the connection manager doesn't exist (i.e., when `streampack.irc.enabled=false`).
Entity changes are persisted regardless of whether IRC is active.

### IrcConnectionManager

A `@Component` with `@ConditionalOnProperty("streampack.irc.enabled", havingValue = "true")`.
Manages a `ConcurrentHashMap<String, IrcAdapter>` mapping network names to live adapters.
Implements `InitializingBean` (auto-connect on startup) and `DisposableBean` (clean shutdown).

### IrcAdapter

Not a Spring bean.
Created dynamically by the connection manager, one per network.
Wraps a single [Kitteh IRC Client Library](https://github.com/KittehOrg/KittehIRCClientLib) `Client` instance.
Handles Kitteh events (`@Handler` annotation from MBassador) and dispatches them to the EventGateway on virtual threads to avoid blocking the Kitteh event thread.
Maintains in-memory sets for muted and logged channels, initialized from entity state on connect.

### Message Flow

When someone types in an IRC channel:

```
IRC user types "!calc 2+3" in #java
  -> Kitteh fires ChannelMessageEvent on its event thread
  -> IrcAdapter.onChannelMessage() starts a virtual thread
  -> Virtual thread:
       1. Log message to DB (if channel is logged)
       2. Build Provenance(protocol=IRC, serviceId="libera", replyTo="#java")
       3. Addressing gate:
          a. "!calc 2+3" starts with "!" -> addressed, strip to "calc 2+3"
          b. Dispatch "calc 2+3" through eventGateway.process()
       4. CalculatorOperation handles it, returns Success("The result of 2+3 is: 5.0")
       5. If channel is not muted: client.sendMessage("#java", "The result of 2+3 is: 5.0")
```

For an unaddressed message like `eclipse++` (assuming a karma operation with `addressed = false`):

```
IRC user types "eclipse++" in #java
  -> Kitteh fires ChannelMessageEvent
  -> Virtual thread:
       1. Log message to DB (if channel is logged)
       2. "eclipse++" has no signal prefix and no bot nick -> unaddressed
       3. Pre-scan: operationService.hasUnaddressedInterest(message) -> true (karma matches)
       4. Dispatch raw "eclipse++" through eventGateway.process()
       5. KarmaOperation handles it, returns Success("eclipse has 42 karma.")
       6. If channel is not muted: reply to channel
```

A message like `hello everyone` with no matching unaddressed operations is logged but not dispatched.

## Testing Without IRC

The service is designed so that all entity operations and admin commands are fully testable against a real database without any IRC connection.

In tests, `streampack.irc.enabled` is `false`, so `IrcConnectionManager` is never created.
`IrcService` receives an empty `ObjectProvider<IrcConnectionManager>` and the `ifAvailable` calls are simply no-ops.
Admin commands like `irc connect libera irc.libera.chat nevet` persist the network entity and return a success message, but no actual IRC connection is attempted.

This means the test suite validates:

- Entity persistence and querying (IrcRepositoryTests)
- Service CRUD logic and error handling (IrcServiceTests)
- Full admin command dispatch through the EventGateway, including auth checks (IrcAdminOperationTests)

All without needing a running IRC server.

## SASL Authentication

If SASL credentials are provided during `irc connect`, the adapter configures SASL PLAIN authentication via the Kitteh library's auth manager.
Credentials are currently stored in plaintext in the `irc_networks` table.
Encrypting stored credentials is a known TODO.

## Module Dependencies

```
lib-irc (entities, repositories, migration)
  depends on: lib-core

service-irc (adapter, connection manager, service, operation)
  depends on: lib-core, lib-irc, org.kitteh.irc:client-lib
```

Both are included in the `app` module's POM.
Removing `service-irc` from the app POM disables all IRC functionality.
Removing `lib-irc` as well removes the database tables from Flyway migrations.
