# Nevet User Guide

Nevet is a multi-protocol bot that connects IRC, Slack, Discord, and console channels.
This guide covers every command available to users and administrators.

## How Commands Work

### Addressing

Most commands require "addressing" the bot - telling it you're talking to it, not the channel.
There are two ways to address:

- **Signal character** (default `!`): `!calc 2+3`
- **Bot mention**: `nevet: calc 2+3` (IRC) or `@Nevet calc 2+3` (Slack/Discord)

The signal character may vary per network or workspace.
Admins can configure it with `irc signal` or `slack signal`.

### Unaddressed Commands

Some commands fire on ambient conversation without any prefix.
You just type normally:

- Karma: `kotlin++` or `xml--`
- Calculator: `calc 2+3`
- Calendar: `today`, `tomorrow`
- URL titles: paste a link and the bot reports its title

### Direct Messages

DMs to the bot bypass addressing entirely.
Everything you send in a DM is treated as an addressed command.

### Role Requirements

Commands are available based on your role:

| Role | Who |
|------|-----|
| Guest | Unauthenticated users |
| User | Authenticated users (email verified) |
| Admin | Channel moderators |
| Super-Admin | System operators |

Most commands are available to everyone.
Commands that require elevated roles are marked below.

---

## Knowledge Base

### Factoids

Factoids are the bot's long-term memory.
Anyone can store and retrieve key-value knowledge.

**Query a factoid** (addressed):

```
spring
```

Returns the stored value for "spring", including its text, URLs, tags, and see-also references.

**Query with arguments** (addressed):

```
javadoc Map
```

If the factoid for "javadoc" contains `$1`, the argument replaces it.

**Set a factoid** (addressed):

```
spring=A popular Java application framework
spring is A popular Java application framework
```

Both `=` and ` is ` work as delimiters.
Use `<reply>` prefix to suppress the "selector is" preamble: `spring=<reply>Spring is a framework`.

**Set an attribute** (addressed):

```
spring.url=https://spring.io
spring.tags=java,framework
spring.languages=java,kotlin
spring.seealso=spring-boot,spring-data
spring.see=spring-framework
spring.type=framework
spring.maven=org.springframework:spring-core
```

Available mutable attributes: `text`, `url`/`urls`, `tag`/`tags`, `language`/`languages`, `type`, `seealso`, `see`, `maven`.

The `set` verb also works: `set spring.tags java,framework`.

**Query an attribute** (addressed):

```
spring.info        metadata: available attributes, last modified
spring.literal     raw text value without rendering
spring.url         URL attribute
spring.tags        tag attribute
spring.seealso     see-also references
```

**Forget a factoid** (addressed):

```
spring.forget
```

Deletes the factoid entirely.

**Lock/Unlock** (addressed, Admin):

```
spring.lock
spring.unlock
```

Locked factoids cannot be modified by non-admins.

**Search** (addressed):

```
search framework
```

Returns factoid selectors matching the search term (up to 10 results).

**Tag search** (addressed):

```
tag java
```

Returns factoid selectors that have the specified tag.

### Spec Lookup

Looks up RFC, JEP, JSR, and PEP specifications by number.
On first lookup, fetches the title from the source website and caches it as a factoid.

**Syntax** (addressed):

```
rfc 2616
jep 456
jsr 330
pep 8
```

**Response**: `rfc 2616: Hypertext Transfer Protocol -- HTTP/1.1 (https://www.rfc-editor.org/rfc/rfc2616)`

### Dictionary

Looks up word definitions from the Free Dictionary API.
On first lookup, caches the definition as a factoid.

**Syntax** (addressed):

```
define ubiquitous
```

**Response**: `define ubiquitous: ubiquitous (adjective): being present everywhere at once`

---

## Reputation

### Set Karma

Adjusts karma for a subject by +1 or -1.
This is **unaddressed** - just type it in conversation.

**Syntax**:

```
kotlin++        increment karma for "kotlin"
xml--           decrement karma for "xml"
spring boot++   multi-word subjects work
```

**Behavior**:

- Trailing text is discarded: `kotlin++ is great` increments "kotlin"
- IRC nick-completion suffixes (`:` `,` `;`) are stripped: `jreicher: ++` increments "jreicher"
- The `c++` language is handled correctly: `c++--` decrements "c++", `c+++` increments "c+"
- Arrow sequences (`-->`, `<--`) are normalized to avoid false matches
- Prose em-dashes (` -- ` with spaces on both sides) are neutralized to avoid false matches on sentences like "foo says Y -- and I don't like that"
- Program flags like `--verbose` or `--no-verify` are not treated as karma operations
- Multi-word subjects where the last word is "C" or "J" (case-insensitive) are rejected to avoid false matches on language references like "I really like C++"
- Subjects longer than 45 characters are ignored (configurable via `streampack.karma.max-subject-length`)

**Self-karma protection**: If you increment your own karma, it is silently flipped to a decrement.

**Immune subjects**: Subjects listed in the `KARMA_IMMUNE_SUBJECTS` configuration are silently ignored.

**Per-channel configuration**:

| Key | Default | Effect |
|-----|---------|--------|
| `ignoreEmdash` | `true` | When true, prose em-dashes (` -- `) are neutralized before matching. Set to `false` with `channel set karma ignoreEmdash false` if a channel wants `foo -- bar` to decrement "foo". |

### Query Karma

**Syntax** (addressed):

```
karma kotlin
```

If you ask about yourself, the response is personalized ("you have karma of 12" instead of "kotlin has karma of 12").

### Karma Rankings

**Syntax** (addressed):

```
top karma           top 5 subjects (default)
top 3 karma         top 3 subjects
top karma 3         alternate syntax
bottom karma        bottom 5 subjects
bottom 3 karma      bottom 3 subjects
```

Default count is 5, maximum is 10.
Subjects with exactly zero karma are excluded.

### Scoring Model

Karma uses time-decayed scoring.
Recent activity counts more than old activity.
Records older than one year are purged.

---

## Utilities

### Calculator

Evaluates mathematical expressions.
This is **unaddressed**.

**Syntax**:

```
calc 2+3
calc sin(pi/4)
```

**Response**: `The result of 2+3 is: 5.0`

### Calendar

Reports today's or tomorrow's date in various calendar systems.
These are **unaddressed**.

**Syntax**:

```
today                    default format
today hebrew             Hebrew calendar
today list               list available calendars
tomorrow                 default format
tomorrow hijri           Hijri calendar
```

Available calendars: Gregorian, Hebrew, Hijri, Japanese, Minguo, Thai Buddhist.

### Weather

Gets current weather for a location.

**Syntax** (addressed):

```
weather London
weather New York, NY
weather Tokyo, Japan
```

**Response**: `The weather for London, GB is 12.5C (54.5F), and is described as "overcast clouds"`

Geocoding is done via Nominatim (rate-limited to 1 request/sec for TOS compliance).
Weather data comes from OpenWeatherMap.

### URL Titles

When someone posts a URL, the bot automatically fetches and displays the page title.
This is **unaddressed** and automatic.

**Response example**:

```
joe mentioned a url: https://example.com/article ("The Actual Article Title")
joe mentioned 2 urls: https://example.com/one ("First") and https://example.com/two ("Second")
```

Titles are extracted from `og:title`, `twitter:title`, or `<title>` tags (in that priority order).
Titles too similar to the URL itself are suppressed.

**Manage ignored hosts** (addressed, Admin):

```
url ignore list                 show ignored hosts
url ignore add <hostname>       add a host to the ignore list
url ignore delete <hostname>    remove a host from the ignore list
```

### Version

Reports the bot's build identity.

**Syntax** (addressed):

```
version
```

**Response**: `nevet 1.0.0 | abc1234 (main) | Built 2026-02-25 10:30:00 EST`

---

## AI Features

These commands require the AI subsystem to be enabled (`AI_ENABLED=true` with a valid API key).
See [Deployment Guide](deployment.md) for configuration.

### Ask

General-purpose question answering.
The bot assembles recent channel context (last 10 messages within a 5-minute window) and forwards your question to the AI.
Throttled to 5 requests per hour per channel.

**Syntax** (addressed):

```
ask what is dependency injection?
ask why is the sky blue?
```

### Poems

Generates a poem about a topic, then automatically analyzes it.

**Syntax** (addressed):

```
poem roses
poem the inevitability of entropy
```

The bot generates a poem and sends it to the channel, then a separate analysis operation evaluates the poem's meter, rhyme scheme, and form.

### Poem Analysis

Analyzes any poem text for meter, rhyme scheme, and poetic form.
Triggers automatically on the `a poem:` prefix (including output from the poem command).
Can also be used manually.

**Syntax** (addressed):

```
a poem: shall I compare thee to a summer's day?/thou art more lovely and more temperate
```

Verses are separated by `/`.

### Sentiment Analysis

Analyzes the sentiment of recent conversation in a target channel.

**Syntax** (addressed, Admin):

```
sentiment #java
sentiment irc://libera/%23metal
```

Pulls the last 4 hours of messages (up to 100) and sends them to the AI for scoring.
When the target differs from the source channel, the response is sent via DM.

---

## Communication

### Tell

Delivers a one-shot message to any destination.

**Syntax** (addressed):

```
tell blue meeting in 5 minutes
tell #metal new album dropped
tell discord://123456/%23general server maintenance tonight
```

**Target resolution** inherits context from the channel where the command was issued:

| Input | Resolved target |
|-------|----------------|
| `tell blue ...` | Private message to "blue" on the same network |
| `tell #metal ...` | Channel #metal on the same network |
| `tell discord://123456/%23general ...` | Full cross-protocol URI |

Messages include the sender's nick as attribution.

### Channel Bridging

Bridges copy messages between channels, even across protocols.
Each direction is configured separately.

**Discover your provenance** (addressed):

```
bridge provenance
```

Returns the provenance URI for the current channel (e.g., `irc://libera/%23java`).
No special permissions required.

**Bridge commands** (addressed, Admin):

| Command | Effect |
|---------|--------|
| `bridge copy <source-uri> <target-uri>` | Copy content from source to target |
| `bridge remove <source-uri> <target-uri>` | Remove a directional copy |
| `bridge list` | Show all configured bridge pairs |
| `bridge` | Show available commands |

**Setup example** - mirror IRC and Discord:

```
!bridge provenance                          (in IRC: get irc://libera/%23metal)
!bridge provenance                          (in Discord: get discord://123456/%23irc)
!bridge copy irc://libera/%23metal discord://123456/%23irc
!bridge copy discord://123456/%23irc irc://libera/%23metal
```

A channel can only participate in one bridge pair (exclusive pairing).

---

## Games

### 21 Matches

A take-turns game.
Players remove 1-3 matches from a pile of 21 each turn.
Whoever is forced to take the last match loses.
The bot uses the mathematically unbeatable `(4 - N)` strategy.

**Commands** (addressed):

```
21 matches        start a new game
21 take 2         take 1, 2, or 3 matches
21 concede        forfeit the current game
```

Games are scoped to the current channel - independent games can run in different channels simultaneously.

### Hangman

Classic word guessing game.
Players guess letters one at a time before running out of lives.

**Commands** (addressed):

```
hangman            start a new game (or show current game state)
hangman e          guess a letter
hangman solve cat  attempt to solve the whole word
hangman concede    give up and reveal the word
```

**Admin commands** (addressed, Admin):

```
hangman block <word>      prevent a word from being used in games
hangman unblock <word>    allow a previously blocked word
```

---

## Markov Impersonation

### Be

Generates a sentence in the style of a given user by building a Markov chain from their message history on the current protocol.

The output is obviously machine-generated nonsense - the humor comes from the uncanny valley between recognizable speech patterns and gibberish.

**Command** (addressed):

```
be <username>       generate a sentence in the style of that user
```

**Examples:**

```
!be jsmith
* channeling jsmith: the problem with gradle is that turtles never actually compile on tuesdays

!be ghostuser
No message history found for ghostuser.
```

The corpus is protocol-scoped: on IRC, only IRC messages are used; on Discord, only Discord messages.
The chain is built on the fly from the user's most recent messages and discarded after generation.

**Technical note:** This is the first polyglot operation in Nevet.
The Kotlin operation handles Spring integration and message log access; the actual Markov chain logic is implemented in Clojure, called through a thin Kotlin shim.

---

## Content Feeds

### RSS Feeds

Register and subscribe to RSS/Atom feeds.
New entries are announced in subscribed channels.

**Commands** (addressed, Admin for add/remove):

| Command | Effect |
|---------|--------|
| `feed add <url>` | Register a feed (URL can be a direct feed or website for autodiscovery) |
| `feed remove <url>` | Deactivate a feed and all subscriptions |
| `feed list` | Show all registered feeds |
| `feed subscribe <url>` | Subscribe the current channel |
| `feed subscribe <url> to <provenance-uri>` | Subscribe a specific channel |
| `feed unsubscribe <url>` | Unsubscribe the current channel |
| `feed unsubscribe <url> to <provenance-uri>` | Unsubscribe a specific channel |
| `feed subscriptions` | Show current channel's subscriptions |
| `feed subscriptions for <provenance-uri>` | Show a specific channel's subscriptions |

**Notification format**: `[Feed Title] Entry Title - https://example.com/entry/123`

**Typical setup**:

```
!feed add https://blog.jetbrains.com
!feed subscribe https://blog.jetbrains.com
```

### GitHub Repositories

Watch GitHub repositories for new issues, pull requests, and releases.

**Commands** (addressed, Admin for add/remove):

| Command | Effect |
|---------|--------|
| `github add <owner/repo> [token]` | Register a repository (token needed for private repos) |
| `github remove <owner/repo>` | Deactivate a repo and all subscriptions |
| `github list` | Show all watched repositories |
| `github subscribe <owner/repo>` | Subscribe the current channel |
| `github subscribe <owner/repo> to <provenance-uri>` | Subscribe a specific channel |
| `github unsubscribe <owner/repo>` | Unsubscribe the current channel |
| `github unsubscribe <owner/repo> to <provenance-uri>` | Unsubscribe a specific channel |
| `github subscriptions` | Show current channel's subscriptions |
| `github subscriptions for <provenance-uri>` | Show a specific channel's subscriptions |
| `github webhook <owner/repo>` | Generate a webhook secret and switch the repo to webhook delivery |

**Notification format**:

```
[owner/repo] New issue #42: Fix login timeout - https://github.com/owner/repo/issues/42
[owner/repo] New PR #43: Add retry logic - https://github.com/owner/repo/pull/43
[owner/repo] New release v1.2.3 - https://github.com/owner/repo/releases/tag/v1.2.3
```

**Webhook mode**:

- Run `github webhook owner/repo`. If the repository is not registered yet, Nevet registers it first, then enables webhook delivery.
- The bot replies with setup instructions and sends the generated secret via a direct message (never logged in-channel).
- `GITHUB_WEBHOOK_SECRET_KEY` must be configured in `.env` so webhook secrets can be encrypted at rest.
- Configure a GitHub repository webhook pointing at `https://<api-domain>/webhooks/github` with **Content type** = JSON and the provided secret.
- When a repo is webhook-enabled, it is removed from the polling schedule; new events flow in real time via the webhook.
- Re-running `github webhook owner/repo` rotates the secret.
- Supported webhook events: `issues` (opened), `pull_request` (opened), `release` (published), and `ping`.
- `ping` is treated as setup verification and is propagated to active subscriptions for the repository.
- Unsupported GitHub event types are accepted and ignored; Nevet logs them as warnings to help decide future support.

---

## Article Ideas

Capture article ideas through a conversational flow from any protocol.
Ideas are saved as draft blog posts tagged `_idea` for later review.

### Capture Flow

Start an idea session, add body content, then save or discard.
The session is scoped to the current channel - other channels are unaffected.

**Start a session** (addressed):

```
article "My Article Title"
article My Article Title
```

Quoted or unquoted titles both work.
Only one idea session can be active per channel at a time.

**Add body content** (addressed):

```
content This is the first paragraph of my idea.
content Here is another paragraph with more detail.
```

Each `content` command appends a new paragraph.
You can add as many blocks as you like.

**Save the idea** (addressed):

```
done
```

Creates a draft blog post with the title and collected body text.
An attribution footer records who submitted the idea and from which channel.

**Discard without saving** (addressed):

```
cancel
```

Clears the session without creating a draft.

**Timeout behavior**: if no interaction occurs for 5 minutes (configurable), the session auto-saves as a draft and notifies the channel.
Every `content`, `done`, or `cancel` command resets the inactivity timer.

### Browse Ideas (Admin)

Admins can list, search, and remove captured ideas.

**Commands** (addressed, Admin):

| Command | Effect |
|---------|--------|
| `ideas` | List all draft ideas |
| `ideas search <term>` | Filter ideas by title |
| `ideas remove #N` | Soft-delete the Nth idea from the list |

---

## User Management

### Edit Profile

Self-service profile editing for authenticated users.
Available through the REST API only (not text commands).
Users can update their display name and email.

### Create User (Super-Admin)

Provisions a new user account.

**Syntax** (addressed):

```
create user jsmith jsmith@example.com "John Smith" [user|admin|super-admin]
```

Role defaults to `user` if omitted.

### Alter User (Admin)

Modifies a user's fields.

**Syntax** (addressed):

```
alter user jsmith role admin
alter user jsmith email newemail@example.com
alter user jsmith displayname "John Q. Smith"
alter user jsmith username jqsmith
```

**Authorization hierarchy**:

- Admin: can modify users with role below Admin, can set role to values below Admin
- Super-Admin: can modify any user, can set any role
- Neither can change their own role

### Link Protocol (Super-Admin)

Binds a protocol identity to a user.
This is how you connect an IRC or Slack identity to a Nevet account.

**Syntax** (addressed):

```
link user jsmith irc libera jsmith
link user jsmith slack jvm-news U0123ABCDEF
```

Format: `link user <username> <protocol> <serviceId> <externalIdentifier>`

### Unlink Protocol (Super-Admin)

Removes a protocol identity binding from a user.

**Syntax** (addressed):

```
unlink user jsmith irc libera jsmith
```

Format: `unlink user <username> <protocol> <serviceId> <externalIdentifier>`

---

## Platform Administration

### IRC (Super-Admin)

See [Deployment Guide](deployment.md#irc-adapter-setup) for initial setup.

**Network management**:

| Command | Effect |
|---------|--------|
| `irc connect <name> <host> <nick> [saslAccount] [saslPassword]` | Register and connect to a network |
| `irc disconnect <name>` | Disconnect from a network |
| `irc autoconnect <name> <true\|false>` | Set whether to connect on startup |
| `irc signal <name> [character]` | Set per-network signal character (omit to reset) |
| `irc status [name]` | Show connection state |

**Channel management**:

| Command | Effect |
|---------|--------|
| `irc join <network> <#channel>` | Register and join a channel |
| `irc leave <network> <#channel>` | Leave a channel |
| `irc autojoin <network> <#channel> <true\|false>` | Auto-join on connect |
| `irc automute <network> <#channel> <true\|false>` | Start muted |
| `irc visible <network> <#channel> <true\|false>` | Show in UI |
| `irc logged <network> <#channel> <true\|false>` | Log messages to database |
| `irc allow-ops <network> <#channel> <true\|false>` | Allow bot to hold ops (default: false, auto-deops) |
| `irc mute <network> <#channel>` | Mute channel now (session only) |
| `irc unmute <network> <#channel>` | Unmute channel now (session only) |

### Slack (Super-Admin)

See [Deployment Guide](deployment.md#slack-adapter-setup) for initial setup and app creation.

**Workspace management**:

| Command | Effect |
|---------|--------|
| `slack connect <name> <bot-token> <app-token>` | Register and connect to a workspace |
| `slack disconnect <name>` | Disconnect from a workspace |
| `slack autoconnect <name> <true\|false>` | Set whether to connect on startup |
| `slack signal <name> [character]` | Set per-workspace signal character (omit to reset) |
| `slack status [name]` | Show connection state |

**Channel management**:

| Command | Effect |
|---------|--------|
| `slack join <workspace> <#channel>` | Register and join a channel |
| `slack leave <workspace> <#channel>` | Leave a channel |
| `slack autojoin <workspace> <#channel> <true\|false>` | Auto-join on connect |
| `slack automute <workspace> <#channel> <true\|false>` | Start muted |
| `slack visible <workspace> <#channel> <true\|false>` | Show in UI |
| `slack logged <workspace> <#channel> <true\|false>` | Log messages to database |
| `slack mute <workspace> <#channel>` | Mute channel now (session only) |
| `slack unmute <workspace> <#channel>` | Unmute channel now (session only) |

### Service Administration (Super-Admin)

Controls service enablement at the system level.
Changes require a restart to take effect.

**Commands** (addressed):

| Command | Effect |
|---------|--------|
| `service list` | Show service configurations (public) |
| `service enable <name>` | Mark a service as enabled |
| `service disable <name>` | Mark a service as disabled |

### Operation Administration (Admin)

Controls operation groups globally.

**Commands** (addressed):

| Command | Effect |
|---------|--------|
| `operation config` | Show global operation config (public) |
| `operation enable <group>` | Enable an operation group globally |
| `operation disable <group>` | Disable an operation group globally |
| `operation set <group> <key> <value>` | Set a config key for a group |

### Channel Configuration (Admin)

Controls operation groups per-channel.
When `for <pattern>` is omitted, the current channel's provenance is used.

**Commands** (addressed):

| Command | Effect |
|---------|--------|
| `channel config [for <pattern>]` | Show resolved config (public) |
| `channel enable <group> [for <pattern>]` | Enable a group for a channel |
| `channel disable <group> [for <pattern>]` | Disable a group for a channel |
| `channel set <group> <key> <value> [for <pattern>]` | Set config for a channel |

---

## Site Content Management

Admins can create system pages and sidebar navigation through the normal post workflow.
The key is **system categories** - categories whose names start with an underscore.
Posts assigned to system categories behave differently from regular blog posts:

- They don't appear in the public blog feed
- They get simple slugs without date prefixes (e.g., `about` instead of `2026/03/about`)
- They're accessed through dedicated URLs rather than the blog post routes

### System Categories

| Category | Purpose | How Content is Displayed |
|----------|---------|--------------------------|
| `_pages` | Static site pages (About, Terms, etc.) | No automatic link - referenced explicitly (footer, hardcoded routes) |
| `_sidebar` | Navigation links in the sidebar | Title shown as a clickable link in the sidebar |

All system category posts are viewable at `/pages/{slug}` regardless of which system category they belong to.
The category controls *where the link appears*, not whether the content is accessible.

Both categories are created automatically and cannot be deleted.

### Creating a System Page

To create an "About" page:

1. Go to **Submit Post** (or use the admin post creation flow)
2. Set the title to "About" (or whatever the page should be called)
3. Write the content in Markdown
4. Under **Categories**, check `_pages`
5. Submit the post
6. Go to **Admin > Pending Posts** and approve it

Once approved, the page is available at `/pages/about`.
The "About this site" link in the footer already points to `/pages/about`.

### Adding Sidebar Links

To add a link to the sidebar:

1. Create a post with the desired link title
2. Assign it to the `_sidebar` category
3. Approve and publish it

The post's title appears as a navigation link in the sidebar.
If the slug is bare (no date prefix), the link points to `/pages/{slug}`.
Sidebar items are ordered by the `sortOrder` field (ascending), then by publish date (newest first).

### Managing System Content

All system category content is accessible from **Admin > Content by Category**.
Select a category from the dropdown to see its posts.
Click a post title to edit it.

### Tips

- A post can belong to both `_pages` and `_sidebar` - this creates a sidebar link that points to the viewable page.
For example, an "About" page in both categories appears in the sidebar and is viewable at `/pages/about`.
- System pages support the full Markdown feature set (tables, fenced code, autolinks).
- The `sortOrder` field controls the display order of sidebar items.
Lower numbers appear first.
Posts with the same sort order are ordered by publish date.

## Quick Reference

| Command | Addressing | Role | Description |
|---------|-----------|------|-------------|
| `<selector>` | addressed | all | Query a factoid |
| `<selector>=<value>` | addressed | all | Set a factoid |
| `set <selector>.<attr> <value>` | addressed | all | Set a factoid attribute |
| `<selector>.forget` | addressed | all | Delete a factoid |
| `<selector>.lock` | addressed | admin | Lock a factoid |
| `search <term>` | addressed | all | Search factoids |
| `tag <tagname>` | addressed | all | Search factoids by tag |
| `rfc/jep/jsr/pep <number>` | addressed | all | Look up a specification |
| `define <word>` | addressed | all | Look up a word definition |
| `subject++` / `subject--` | unaddressed | all | Adjust karma |
| `karma <subject>` | addressed | all | Query karma |
| `top/bottom [N] karma` | addressed | all | Karma rankings |
| `calc <expression>` | unaddressed | all | Calculator |
| `today [calendar]` | unaddressed | all | Today's date |
| `tomorrow [calendar]` | unaddressed | all | Tomorrow's date |
| `weather <location>` | addressed | all | Current weather |
| `version` | addressed | all | Bot build info |
| `ask <question>` | addressed | all | AI question (needs AI enabled) |
| `poem <topic>` | addressed | all | Generate a poem (needs AI) |
| `a poem: <text>` | addressed | all | Analyze a poem (needs AI) |
| `sentiment <target>` | addressed | admin | Sentiment analysis (needs AI) |
| `tell <target> <message>` | addressed | all | Send a message |
| `bridge provenance` | addressed | all | Show channel provenance URI |
| `bridge copy/remove/list` | addressed | admin | Manage bridges |
| `21 matches/take/concede` | addressed | all | 21 Matches game |
| `hangman` / `hangman <letter>` | addressed | all | Hangman game |
| `hangman block/unblock` | addressed | admin | Hangman word blocklist |
| `be <username>` | addressed | all | Markov chain impersonation |
| `feed add/remove/list` | addressed | admin | Manage RSS feeds |
| `feed subscribe/unsubscribe` | addressed | admin | Manage feed subscriptions |
| `github add/remove/list` | addressed | admin | Manage GitHub repos |
| `github subscribe/unsubscribe` | addressed | admin | Manage repo subscriptions |
| `article "<title>"` | addressed | all | Start an idea capture session |
| `content <text>` | addressed | all | Add body to active idea session |
| `done` | addressed | all | Save idea as draft post |
| `cancel` | addressed | all | Discard active idea session |
| `ideas` / `ideas search` | addressed | admin | Browse/search ideas |
| `ideas remove #N` | addressed | admin | Soft-delete an idea |
| `url ignore add/delete/list` | addressed | admin | Manage URL title ignore list |
| `create user ...` | addressed | super-admin | Create a user |
| `alter user ...` | addressed | admin | Modify a user |
| `link/unlink user ...` | addressed | super-admin | Manage protocol bindings |
| `irc ...` | addressed | super-admin | IRC administration |
| `slack ...` | addressed | super-admin | Slack administration |
| `service list/enable/disable` | addressed | super-admin | Service administration |
| `operation config/enable/disable/set` | addressed | admin | Operation administration |
| `channel config/enable/disable/set` | addressed | admin | Channel configuration |
