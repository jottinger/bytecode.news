# Slack Service Configuration Guide

The Slack service connects Nevet to Slack workspaces as a bot.
It uses Socket Mode (persistent WebSocket) so no public HTTP endpoint is required.
Multiple workspaces are supported, each with its own credentials and connection.

## Creating a Slack App

Before connecting Nevet to a Slack workspace, you need to create a Slack App and obtain two tokens: a **Bot Token** and an **App Token**.

### Step 1: Create the App

1. Go to [https://api.slack.com/apps](https://api.slack.com/apps) and click **Create New App**.
2. Choose **From scratch**.
3. Name the app (e.g., "Nevet") and select the workspace you want to install it in.
4. Click **Create App**.

You are now on the app's **Basic Information** page.
Keep this page open -- you will return to it.

### Step 2: Enable Socket Mode

1. In the left sidebar, click **Socket Mode**.
2. Toggle **Enable Socket Mode** to on.
3. Slack will prompt you to create an **App-Level Token**.
   Name it something like "nevet-socket" and add the scope `connections:write`.
4. Click **Generate**.
5. Copy the token that starts with `xapp-`.
   This is your **App Token**.
   Store it securely -- Slack will not show it again.

### Step 3: Subscribe to Events

1. In the left sidebar, click **Event Subscriptions**.
2. Toggle **Enable Events** to on.
3. Under **Subscribe to bot events**, add:
   - `message.channels` -- messages in public channels the bot is in
   - `message.groups` -- messages in private channels the bot is invited to
   - `message.im` -- direct messages to the bot
4. Click **Save Changes**.

### Step 4: Set Bot Token Scopes

1. In the left sidebar, click **OAuth & Permissions**.
2. Under **Scopes > Bot Token Scopes**, add:
   - `chat:write` -- send messages
   - `channels:history` -- read messages in public channels
   - `groups:history` -- read messages in private channels
   - `im:history` -- read direct messages
   - `channels:read` -- list and discover channels
   - `channels:join` -- join public channels programmatically
   - `im:read` -- list DM conversations
   - `users:read` -- resolve user display names
3. Click **Save Changes**.

### Step 5: Install the App to the Workspace

1. Still on the **OAuth & Permissions** page, click **Install to Workspace** (or **Reinstall to Workspace** if you have already installed it before adding scopes).
2. Review the permissions and click **Allow**.
3. Copy the **Bot User OAuth Token** that starts with `xoxb-`.
   This is your **Bot Token**.

### Step 6: Invite the Bot to Channels

The bot can only see messages in channels it has joined.
In Slack, type `/invite @Nevet` in any channel you want the bot to monitor.
Alternatively, the `slack join` admin command can join public channels programmatically (see Admin Commands below).

### Summary of Tokens

| Token | Prefix | Purpose | Where to Find |
|-------|--------|---------|---------------|
| Bot Token | `xoxb-` | API calls (send messages, read info) | OAuth & Permissions page |
| App Token | `xapp-` | Socket Mode connection (receive events) | Basic Information > App-Level Tokens |

These are the only two tokens Nevet needs.
The App ID, Client ID, Client Secret, Signing Secret, and Verification Token are not used.

## Activation

The Slack runtime is controlled by a single property:

```yaml
streampack:
  slack:
    enabled: true   # false by default
```

When `enabled` is `false` (the default), the `SlackConnectionManager` bean is not created.
Admin commands (`slack connect`, `slack join`, etc.) still work and persist configuration to the database, but no Slack connections are made.

When `enabled` is `true`, the connection manager starts on application boot, reads all workspaces with `autoconnect = true` from the database, and connects to them via Socket Mode.

### Environment Variable

In the production `application.yml`:

```yaml
streampack:
  slack:
    enabled: ${SLACK_ENABLED:false}
    signal-character: ${SLACK_SIGNAL:!}
```

Set `SLACK_ENABLED=true` in your `.env` to activate Slack at deploy time.

## Database as Startup Manifest

Like IRC, all Slack configuration lives in the database.
There are no YAML blocks for workspace tokens or channel lists.

- **slack_workspaces** -- one row per Slack workspace (name, bot token, app token, autoconnect flag, optional signal character override)
- **slack_channels** -- one row per channel per workspace (channel ID, channel name, autojoin, automute, visible, logged flags)

The database is the source of truth for what to connect to on startup.
Runtime state (muted channels, logging state) is held in memory and initialized from database flags when the adapter connects.

### Credential Storage

Bot tokens and app tokens are stored in the `slack_workspaces` table.
They are currently stored as plaintext, consistent with how IRC SASL credentials are handled.
Encrypting stored credentials is a known TODO that applies to both IRC and Slack.

## Admin Commands

All Slack configuration is done through the `slack` admin command, which requires `SUPER_ADMIN` role.
These commands work through any protocol adapter (console, HTTP, IRC, Discord, Slack itself).

### Workspace Management

| Command | Effect |
|---------|--------|
| `slack connect <name> <bot-token> <app-token>` | Register workspace in DB and connect (if Slack runtime is active) |
| `slack disconnect <name>` | Disconnect from workspace (runtime only, entity remains) |
| `slack autoconnect <name> <true\|false>` | Set whether this workspace connects on startup |
| `slack signal <name> [character]` | Set per-workspace signal character (omit to reset to global default) |
| `slack status [name]` | Show connection state for one or all workspaces |

The `name` is a short identifier you choose (e.g., "jvm-news", "my-team").
It must be unique and is used in all subsequent commands.

The bot token and app token are the `xoxb-` and `xapp-` values from the Slack app setup.

### Channel Management

| Command | Effect |
|---------|--------|
| `slack join <workspace> <#channel>` | Register channel in DB, join if connected, discover channel ID automatically |
| `slack leave <workspace> <#channel>` | Leave channel (runtime only, entity remains) |
| `slack autojoin <workspace> <#channel> <true\|false>` | Set whether this channel is joined on connect |
| `slack automute <workspace> <#channel> <true\|false>` | Set whether this channel starts muted |
| `slack visible <workspace> <#channel> <true\|false>` | Set whether this channel appears in UI |
| `slack logged <workspace> <#channel> <true\|false>` | Set whether messages are stored to the database |

Channel names use the `#` prefix by convention.
The adapter resolves channel names to Slack's internal channel IDs automatically via the API.

### Runtime Controls

| Command | Effect |
|---------|--------|
| `slack mute <workspace> <#channel>` | Mute channel now (bot stops responding, still logs if logging is on) |
| `slack unmute <workspace> <#channel>` | Unmute channel now |

Mute/unmute only affect the current session.
Use `automute` to persist the muted state across restarts.

### Help

| Command | Effect |
|---------|--------|
| `slack` | Show all available subcommands |

## Typical Setup Workflow

Starting from a fresh database with the console adapter enabled:

```
> slack connect jvm-news xoxb-1234567890-abcdef xapp-1-ABCDEF123456
Workspace 'jvm-news' registered. Connecting...

> slack join jvm-news #general
Joined '#general' on 'jvm-news'.

> slack join jvm-news #java
Joined '#java' on 'jvm-news'.

> slack autojoin jvm-news #general true
Channel '#general' on 'jvm-news' autojoin set to true

> slack autojoin jvm-news #java true
Channel '#java' on 'jvm-news' autojoin set to true

> slack autoconnect jvm-news true
Workspace 'jvm-news' autoconnect set to true

> slack status
  jvm-news: 2 channel(s) [#general, #java]
```

After this, the bot will automatically connect to the workspace and join both channels on every startup (as long as `streampack.slack.enabled=true`).

## Message Addressing

The same addressing model used by IRC and Discord applies to Slack.

### Addressed Messages

A message is "addressed" if it starts with either:

- The **signal character** (default `!`): `!calc 2+3`
- An **@mention** of the bot: `@Nevet calc 2+3`

When a message is addressed, the prefix is stripped and the remaining text is dispatched through the EventGateway.
So `!calc 2+3` and `@Nevet calc 2+3` both arrive at operations as `calc 2+3`.

### Unaddressed Messages

Messages without a signal character or bot mention are "unaddressed."
They are dispatched with the `addressed` header set to `false`.
Only operations with `addressed = false` (like karma tracking) will process them.

### Direct Messages

Direct messages to the bot bypass the gate entirely.
They are always dispatched as addressed, without prefix stripping.

### Signal Character Configuration

The signal character has two levels:

1. **Global default** in `application.yml`: `streampack.slack.signal-character` (default `!`)
2. **Per-workspace override** in the database: set via `slack signal <name> <character>`

The effective signal character for a workspace is the per-workspace override if set, otherwise the global default.

## Architecture Layers

The Slack service mirrors the IRC service's four-layer architecture.

### SlackAdminOperation

A `TypedOperation<String>` that matches the `"slack"` prefix.
Parses subcommands and delegates to SlackService.
Requires SUPER_ADMIN role.

### SlackService

A `@Component` that handles entity CRUD (persist workspaces, channels, update flags).
For runtime operations (connect, join, mute), it delegates to `SlackConnectionManager` via `ObjectProvider`.
Entity changes are persisted regardless of whether Slack is active.

### SlackConnectionManager

A `@Component` with `@ConditionalOnProperty("streampack.slack.enabled", havingValue = "true")`.
Manages a `ConcurrentHashMap<String, SlackAdapter>` mapping workspace names to live adapters.
Implements `InitializingBean` (auto-connect on startup) and `DisposableBean` (clean shutdown).

### SlackAdapter

Not a Spring bean.
Created dynamically by the connection manager, one per workspace.
Wraps the Slack SDK's Socket Mode client (`SocketModeApp`) and Bolt app.
Handles Slack events and dispatches them to the EventGateway on virtual threads.
Maintains in-memory sets for muted and logged channels, initialized from entity state on connect.

### Message Flow

When someone types in a Slack channel:

```
Slack user types "!calc 2+3" in #java
  -> Socket Mode delivers MessageEvent via WebSocket
  -> SlackAdapter.onMessage() starts a virtual thread
  -> Virtual thread:
       1. Log message to DB (if channel is logged)
       2. Build Provenance(protocol=SLACK, serviceId="jvm-news", replyTo="#java")
       3. Addressing gate:
          a. "!calc 2+3" starts with "!" -> addressed, strip to "calc 2+3"
          b. Dispatch "calc 2+3" through eventGateway.process()
       4. CalculatorOperation handles it, returns Success("The result of 2+3 is: 5.0")
       5. If channel is not muted: post reply via Slack API (chat.postMessage)
```

For an unaddressed message like `eclipse++`:

```
Slack user types "eclipse++" in #java
  -> Socket Mode delivers MessageEvent
  -> Virtual thread:
       1. Log message to DB (if channel is logged)
       2. "eclipse++" has no signal prefix and no bot mention -> unaddressed
       3. Dispatch raw "eclipse++" with addressed=false
       4. SetKarmaOperation (addressed=false) handles it
       5. If channel is not muted: reply to channel
```

## Provenance URI Format

Slack messages use the provenance URI scheme:

```
slack://jvm-news/%23java       -- workspace "jvm-news", channel #java
slack://jvm-news/U0123ABCDEF   -- workspace "jvm-news", DM to user ID
```

These URIs are used by the subscription system (RSS feed delivery, cross-protocol notifications).

## SDK Dependencies

The Slack service uses the official Slack SDK for Java:

- `com.slack.api:bolt-socket-mode` -- Socket Mode event handling
- `com.slack.api:slack-api-client` -- REST API calls (chat.postMessage, conversations.list, etc.)

Socket Mode uses a WebSocket connection initiated from the bot to Slack's servers.
No inbound HTTP traffic is needed, so no public URL, no ngrok, no reverse proxy configuration for Slack specifically.

## Testing Without Slack

The same pattern as IRC: all entity operations and admin commands are testable against a real database without any Slack connection.

In tests, `streampack.slack.enabled` is `false`, so `SlackConnectionManager` is never created.
`SlackService` receives an empty `ObjectProvider<SlackConnectionManager>` and `ifAvailable` calls are no-ops.
Admin commands persist entities and return success messages, but no connections are attempted.

## Module Dependencies

```
lib-slack (entities, repositories, migration)
  depends on: lib-core

service-slack (adapter, connection manager, service, operation)
  depends on: lib-core, lib-slack, com.slack.api:bolt-socket-mode
```

Both are included in the `app` module's POM.
Removing `service-slack` from the app POM disables all Slack functionality.
Removing `lib-slack` as well removes the database tables from Flyway migrations.

## Differences from Discord

The existing Discord adapter uses a single-connection model (one bot token, one JDA instance, all guilds).
The Slack service uses a multi-connection model (one adapter per workspace), matching the IRC pattern.

This is because:
- Discord bots are added to guilds via OAuth invite; one token covers all guilds.
- Slack apps are installed per-workspace; each workspace needs its own bot token and app token pair.

The operational model (admin commands, database entities, connection lifecycle) follows IRC, not Discord.
