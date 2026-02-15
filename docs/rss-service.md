# RSS Service Configuration Guide

The RSS service lets Nevet consume external RSS/Atom feeds and route new-entry notifications to any protocol destination (IRC channels, console, eventually Discord/Slack).

Feed registration and subscription are separate operations.
You add a feed to tell Nevet about it; you subscribe a channel to tell Nevet where to send notifications when new entries appear.

## Activation

The RSS service is always active when `service-rss` is on the classpath.
There is no on/off switch - if the module is included in the app POM, it runs.

Polling interval is configurable:

```yaml
streampack:
  rss:
    poll-interval: PT60M          # ISO-8601 duration, default 60 minutes
    connect-timeout-seconds: 5   # HTTP connect timeout for feed fetching
    read-timeout-seconds: 10     # HTTP read timeout for feed fetching
```

The poll interval can also be set via environment variable:

```
RSS_POLL_INTERVAL=PT10M
```

## How It Works

### Feed lifecycle

1. **Add** a feed with `feed add <url>` - Nevet discovers the feed (direct RSS/Atom URL or HTML autodiscovery), stores it, and seeds all current entries as a baseline.
2. **Subscribe** a channel with `feed subscribe <url>` - links the feed to the channel where the command was issued.
3. **Poll** - a scheduled service fetches all active feeds at the configured interval, detects new entries by GUID comparison, and stores them.
4. **Notify** - for each new entry on a subscribed feed, a notification is sent to the egress channel with the subscriber's provenance. Existing protocol adapters (IRC, console) pick it up and deliver it.

Feeds without subscribers are still polled and their entries stored.
This is deliberate - a future "interesting threshold" analyzer will need the entry history.

### Notification format

```
[Feed Title] Entry Title - https://example.com/entry/123
```

Notifications are published directly to the egress channel, not through ingress/operations.
They are system-generated output, not inbound commands.

## Commands

All commands require addressing (signal character or bot nick prefix).

### Feed registration

| Command | Effect |
|---------|--------|
| `feed add <url>` | Discover and register a feed. URL can be a direct feed URL or a website (HTML autodiscovery). Seeds current entries as baseline. |
| `feed remove <url>` | Deactivate a feed and all its subscriptions. Stops polling. |
| `feed list` | Show all registered feeds with titles and URLs |

### Subscription management

| Command | Effect |
|---------|--------|
| `feed subscribe <url>` | Subscribe the current channel to a feed. The feed must already exist (use `feed add` first). |
| `feed subscribe <url> to <provenance-uri>` | Subscribe an explicit channel to a feed. Use this from the console or to target a different channel. |
| `feed unsubscribe <url>` | Unsubscribe the current channel from a feed |
| `feed unsubscribe <url> to <provenance-uri>` | Unsubscribe an explicit channel from a feed |
| `feed subscriptions` | Show what the current channel is subscribed to |
| `feed subscriptions for <provenance-uri>` | Show subscriptions for an explicit channel |

Without an explicit target, the destination is the channel where the command was issued.
With `to <provenance-uri>` or `for <provenance-uri>`, you can manage subscriptions for any channel from anywhere - including the console.
The URL can be the exact feed URL or the site URL if discovery can resolve it to a registered feed.

Provenance URIs follow the standard encoding: `irc://libera/%23java`, `discord://guild/%23channel`, etc.

### URL resolution

When you provide a URL to `feed subscribe`, `feed unsubscribe`, or `feed remove`, the service resolves it in order:

1. Exact match against stored `feedUrl`
2. Discovery fallback - fetch the URL, find the feed link, match against stored feeds

This means if you added a feed via `feed add https://example.com` (which discovered `https://example.com/feed.xml`), you can subscribe with either URL.

## Typical Setup Workflow

### From an IRC channel

When you issue the commands from the channel you want to subscribe:

```
> feed add https://blog.jetbrains.com
Added feed "JetBrains Blog" with 20 entries

> feed subscribe https://blog.jetbrains.com
Subscribed to "JetBrains Blog"

> feed subscriptions
JetBrains Blog - https://blog.jetbrains.com/feed/
```

### From the console

When you want to subscribe an IRC channel from the console, use the explicit target syntax:

```
> feed add https://inside.java/feed.xml
Added feed "Inside Java" with 15 entries

> feed subscribe https://inside.java/feed.xml to irc://libera/%23java
Subscribed to "Inside Java"

> feed subscriptions for irc://libera/%23java
Inside Java - https://inside.java/feed.xml
```

When a new entry appears in either feed, the channel receives:

```
[JetBrains Blog] Kotlin 2.3 Released - https://blog.jetbrains.com/kotlin/2026/02/kotlin-2-3/
```

## Database Schema

### rss_feeds

One row per registered feed.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (v7) | Primary key |
| `feed_url` | VARCHAR(2048) | The resolved feed URL (unique) |
| `site_url` | VARCHAR(2048) | The site's main URL, if known |
| `title` | VARCHAR(500) | Feed title from the feed metadata |
| `description` | VARCHAR(2000) | Feed description |
| `last_fetched_at` | TIMESTAMPTZ | Last successful poll time |
| `created_at` | TIMESTAMPTZ | When the feed was registered |
| `active` | BOOLEAN | False after `feed remove` |

### rss_entries

One row per feed entry, used for new-entry detection.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (v7) | Primary key |
| `feed_id` | UUID | FK to rss_feeds |
| `guid` | VARCHAR(2048) | Entry GUID (from `<guid>` or `<id>`, falls back to link) |
| `link` | VARCHAR(2048) | Entry link |
| `title` | VARCHAR(500) | Entry title |
| `published_at` | TIMESTAMPTZ | Publication date from the feed |
| `created_at` | TIMESTAMPTZ | When we first saw this entry |

Unique constraint on `(feed_id, guid)` prevents duplicate entries.

### rss_feed_subscriptions

Maps a feed to a notification destination.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (v7) | Primary key |
| `feed_id` | UUID | FK to rss_feeds (cascade delete) |
| `destination_uri` | VARCHAR(2048) | Provenance URI (e.g., `irc://libera/%23java`) |
| `created_at` | TIMESTAMPTZ | When the subscription was created |
| `active` | BOOLEAN | False after unsubscribe or feed removal |

Unique constraint on `(feed_id, destination_uri)` prevents duplicate subscriptions.
The destination URI is protocol-agnostic - it uses the standard Provenance encoding, so the same subscription model works for IRC, Discord, Slack, or any future protocol.

## Architecture

### Operations

Two operations handle feed commands:

- **AddFeedOperation** (TranslatingOperation<AddFeedRequest>) - handles `feed add <url>`. Accepts both string commands and typed `AddFeedRequest` payloads.
- **FeedManagementOperation** (TypedOperation<String>) - handles `feed list`, `feed subscribe`, `feed unsubscribe`, `feed subscriptions`, `feed remove`.

### Services

- **FeedDiscoveryService** - fetches URLs and parses RSS/Atom feeds, with HTML autodiscovery fallback. Also provides `fetchFeed()` for direct feed URL fetching during polling.
- **RssSubscriptionService** - orchestrates feed registration, subscription management, and feed resolution.
- **RssFeedPollingService** - scheduled service that polls all active feeds, stores new entries, and dispatches notifications to the egress channel.

### Egress-only notifications

The polling service publishes notifications directly to the egress channel rather than through ingress/operations.
This is because RSS notifications are system-generated output, not inbound user commands.
Each notification carries the subscriber's decoded provenance, so the correct protocol adapter picks it up automatically.

## Module Dependencies

```
service-rss (operations, services, entities, migrations)
  depends on: lib-core, rome (RSS/Atom parsing), jsoup (HTML autodiscovery)
```

Removing `service-rss` from the app POM disables all RSS functionality and removes the database tables from Flyway migrations.
