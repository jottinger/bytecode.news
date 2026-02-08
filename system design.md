# Nevet System Design

> **Document Purpose**: This design document describes the architecture for a JVM-focused content and knowledge management platform. It is intended for both human developers and LLM assistants working on the system.

## Overview

**Nevet** is a content hub and knowledge management system built on Enterprise Application Integration (EAI) principles using Spring Integration. The initial public-facing instance will be **jvm.news**, a community-driven news and information site for the JVM ecosystem.

### Core Philosophy

- **Protocol-agnostic messaging**: All interactions (HTTP, IRC, Slack, Discord, RSS) normalize to a common message format
- **API-first architecture**: A REST backend with pluggable frontends; the UI is decoupled from the core
- **Content as events**: Inbound content (user submissions, RSS feeds, chat messages) flows through a unified message bus
- **Subscription-based routing**: Outbound notifications route based on subscriber interest, not source protocol
- **Knowledge linking**: Content attaches to factoids (knowledge entities), enabling rich queries and subscriptions

---

## Architecture

### Design Principles

1. **Protocol adapters are dumb**: They know only how to receive/send in their protocol. They do not interpret content.
2. **Translation is centralized**: A shared translation layer converts raw input to typed operations.
3. **Operations are protocol-agnostic**: They receive typed requests, return typed responses. They never know where the request originated.
4. **Provenance flows through**: Every message carries its origin metadata from input to output.
5. **Output matching is passive**: Protocol adapters watch the output channel and claim messages that match their provenance.

### Message Flow Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PROTOCOL ADAPTERS                                 │
│   (Discord, IRC, Slack, HTTP, RSS, etc.)                                    │
│                                                                             │
│   Responsibilities:                                                         │
│   - Receive raw input from protocol                                         │
│   - Call translation service with raw input                                 │
│   - Annotate with provenance (protocol, user, channel/endpoint, etc.)       │
│   - Dispatch typed request to operations channel                            │
│   - Watch output channel for matching provenance                            │
│   - Format and deliver response via protocol                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TRANSLATION LAYER                                   │
│                                                                             │
│   Input: Raw string or structured request                                   │
│   Output: Typed operation request                                           │
│                                                                             │
│   Examples:                                                                 │
│   - "!spring" → FactoidRequest("spring")                                    │
│   - HTTP GET "/" → PostsRequest(page=1)                                     │
│   - "!search kotlin coroutines" → SearchRequest("kotlin coroutines")        │
│                                                                             │
│   Translators are registered and matched by pattern.                        │
│   HTTP requests may bypass string translation (already structured).         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OPERATIONS CHANNEL                                  │
│                                                                             │
│   Receives: Typed request + provenance                                      │
│   Behavior:                                                                 │
│   - Iterates registered operations                                          │
│   - Matches operation to request type                                       │
│   - Executes matching operation(s)                                          │
│   - If no match or resource not found → terminal message                    │
│   - Posts result (or terminal) to output channel with provenance preserved  │
│                                                                             │
│   Operations never timeout silently; they always produce output.            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          OUTPUT CHANNEL                                     │
│                                                                             │
│   All responses flow here with full provenance.                             │
│   Protocol adapters subscribe and filter:                                   │
│   - "Is this provenance mine?" (protocol match)                             │
│   - "Who requested this?" (user/channel/endpoint)                           │
│   - Format response for protocol                                            │
│   - Deliver via protocol                                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Concrete Examples

### Example 1: Discord Factoid Request

**Scenario**: User in Discord channel types `!spring`

```
1. DISCORD ADAPTER receives message
   Raw input: "!spring"
   Context: user=@joe, channel=#java-help, guild=jvm-community

2. DISCORD ADAPTER calls TRANSLATION LAYER → FactoidRequest(slug="spring")

3. DISCORD ADAPTER creates message with provenance
   Provenance: protocol=DISCORD, replyTo=discord://jvm-community/%23java-help

4. DISCORD ADAPTER posts to OPERATIONS CHANNEL

5. OPERATIONS CHANNEL → FactoidOperation executes
   Return: FactoidResponse(name="Spring Framework", summary="...", recentPosts=[...])

6. OPERATIONS CHANNEL posts to OUTPUT CHANNEL (provenance preserved)

7. DISCORD ADAPTER watches OUTPUT CHANNEL, matches provenance, formats and sends
```

### Example 2: HTTP Posts Listing

**Scenario**: Browser requests `GET /`

```
1. HTTP ADAPTER receives request (GET /, anonymous)

2. HTTP ADAPTER translates directly → PostsRequest(page=1, limit=20)

3. HTTP ADAPTER creates message with provenance (protocol=HTTP, correlationId=uuid)

4. OPERATIONS CHANNEL → PostsOperation executes
   Return: PostsResponse(posts=[...], page=1, totalPages=5)

5. HTTP ADAPTER matches provenance, formats response
   - Accept: text/html → render via template
   - Accept: application/json → serialize as JSON
```

### Example 3: Cross-Protocol Notification

**Scenario**: Slack user requests `!spring notify:discord:#announcements`

```
1. SLACK ADAPTER parses message, extracts alsoNotify

2. Provenance includes:
   replyTo: slack://workspace/channel
   alsoNotify: ["discord://server/%23announcements"]

3. Operations execute, return FactoidResponse

4. SLACK ADAPTER matches replyTo, delivers to Slack

5. DISCORD ADAPTER sees alsoNotify includes its address → posts to #announcements
```

---

## Domain Model

### Primary Deliverable: Blog Platform

The initial target is a blog with user authentication, registration, and content management. The architecture supports future expansion to IRC, Discord, Slack, and RSS, but the MVP focuses on HTTP/web access.

### Identity

All entity IDs use **UUIDv7** - time-ordered UUIDs that provide distributed generation while maintaining index locality.

---

### User Model

#### Roles (Hierarchical)
```
User < Admin < Super-Admin
```

| Role | Capabilities |
|------|--------------|
| **User** | Create posts, comment on posts, view own drafts, edit profile |
| **Admin** | All User + approve/reject posts, edit any post, soft/hard delete content, manage tags/categories |
| **Super-Admin** | All Admin + system configuration (IRC/Discord/Slack service control, future) |

#### User Entity
```java
public record User(
    UUID id,                      // UUIDv7
    String username,              // Unique, used for login
    String email,                 // Required, verified
    boolean emailVerified,        // Must be true to post/comment
    String passwordHash,          // For username/password auth
    String oauthProvider,         // Optional: "google", "github", etc.
    String oauthId,               // External provider ID
    String displayName,           // Shown publicly
    String realName,              // Optional, not verified
    Role role,                    // USER, ADMIN, SUPER_ADMIN
    Instant createdAt,
    Instant lastLoginAt,
    boolean deleted               // Soft delete
) {}

public enum Role { USER, ADMIN, SUPER_ADMIN }
```

#### Registration & Authentication
- Open registration with email verification
- Username/password (bcrypt) + OAuth (Google, GitHub)
- OAuth links to existing account by email match or creates new account
- Password reset via email token

---

### Post Model

```java
public record Post(
    UUID id,                        // UUIDv7
    String slug,                    // URL-friendly, user-specified, admin-editable
    String title,
    String summary,                 // Short description for listings/previews
    String contentSource,           // Raw markdown
    String contentRendered,         // Pre-rendered HTML
    UUID authorId,                  // null for anonymous submissions
    PostStatus status,              // DRAFT, APPROVED
    Instant createdAt,
    Instant publishedAt,            // Scheduled publication date
    Instant updatedAt,
    List<UUID> categoryIds,
    List<UUID> tagIds,
    long readCount,                 // Raw hit counter
    boolean deleted                 // Soft delete
) {}

public enum PostStatus { DRAFT, APPROVED }
```

#### Visibility Rules
| Condition | Visible To |
|-----------|------------|
| `status = DRAFT` | Author only (if logged in), Admins |
| `status = APPROVED, publishedAt > now` | Author, Admins (scheduled) |
| `status = APPROVED, publishedAt <= now` | Everyone |
| `deleted = true` | Admins only |

#### Workflow
1. Anyone (including anonymous) submits post → `status = DRAFT`
2. Anonymous submitters cannot see their draft (no account)
3. Admin reviews, approves → `status = APPROVED`, sets `publishedAt`
4. Post becomes public when `publishedAt <= now`

#### Editing
- **Draft**: Author can edit freely
- **Approved**: Only Admin can edit
- Edits re-render HTML from source

---

### Comment Model

```java
public record Comment(
    UUID id,                        // UUIDv7
    UUID postId,
    UUID parentCommentId,           // null for top-level
    UUID authorId,                  // Required (no anonymous comments)
    String contentSource,           // Raw markdown
    String contentRendered,
    Instant createdAt,
    Instant updatedAt,
    boolean deleted
) {}
```

#### Rules
- **Who**: Registered users only (email verified)
- **Nesting**: Unlimited depth (Reddit-style collapse)
- **Moderation**: None (immediate visibility)
- **Editing**: Author can edit within 5 minutes
- **Deletion**: Users cannot delete; Admins can soft-delete or hard-delete

---

### Taxonomy

#### Category (Hierarchical)
```java
public record Category(
    UUID id, String slug, String name, String description,
    UUID parentId, int sortOrder, boolean deleted
) {}
```

#### Tag (Flat)
```java
public record Tag(
    UUID id, String slug, String name, boolean deleted
) {}
```

Posts can have multiple categories and multiple tags. Both are admin-managed.

---

### Deletion

**Soft delete** (`deleted = true`) is the default:
- **User**: Cannot log in, content remains attributed
- **Post**: Hidden from public, visible to admins
- **Comment**: Shows as "[deleted]" to preserve thread structure
- **Category/Tag**: Hidden from selection, existing associations preserved

**Hard delete** is available to Admins for:
- GDPR/legal compliance
- Truly toxic or illegal content
- Spam cleanup

Hard delete permanently removes the record and is not recoverable.

---

### Content

#### Format: Markdown Only

- Storage: Raw source + pre-rendered HTML
- Library: CommonMark or flexmark-java
- Extensions: Tables, fenced code blocks, autolinks. No raw HTML.

#### Images: Not Supported

The platform is text-only. No image upload, storage, or hosting. Users may link to externally-hosted images via markdown if needed, but the platform does not manage image assets.

Rationale: Image handling (storage, CDN, moderation, abuse prevention, resizing, format conversion) is a separate problem domain that adds significant complexity without core value for a text-focused news/discussion site.

#### Licensing

All user-submitted content is licensed under **Creative Commons CC BY-SA 4.0**. This will be made clear during submission.

---

## Messaging Types

### Provenance
```java

enum Protocol {
	DISCORD,
	SLACK,
	IRC,
	HTTP,
	MAILTO,
	MATTERMOST,
	RSS // whatever
}

public record Provenance(
    Protocol protocol,              // DISCORD, SLACK, IRC, HTTP, RSS
    String serviceId,               // the specific implementation of the protocol (nullable)
    String user,                    // nullable
    String replyTo,                 // address path (decoded); encode() produces full URI
    List<String> alsoNotify,        // full URI-format addresses
    String correlationId,           // nullable
    Instant timestamp,
    Map<String, String> metadata
) {}
```

### Request/Response Types
```java
public sealed interface OperationRequest permits
    PostsRequest, PostRequest, SearchRequest,
    SubmitPostRequest, SubmitCommentRequest,
    FactoidRequest { }

public sealed interface OperationResponse permits
    PostsResponse, PostResponse, SearchResponse,
    SubmissionResponse, FactoidResponse, TerminalResponse { }

public record TerminalResponse(TerminalType type, String message) {}
public enum TerminalType { NOT_FOUND, UNAUTHORIZED, INVALID_REQUEST, ERROR }
```

An address is a valid URI: `protocol://serviceId/address-path`, where special characters like `#` are percent-encoded (e.g., `%23`). The serviceId is the authority component - it identifies the specific service instance handling that protocol. When there is no meaningful service intermediary (e.g., email), the serviceId is omitted and the URI uses triple-slash syntax: `mailto:///dreamreal@gmail.com`.

Examples:
- `irc://ircservice/oftc/%23java` - IRC protocol, service named "ircservice", server "oftc", channel "#java"
- `discord://jvm-community/%23java-help` - Discord protocol, guild "jvm-community", channel "#java-help"
- `mailto:///dreamreal@gmail.com` - email with no service intermediary
- `slack://workspace/channel` - Slack protocol, workspace as service, channel as address

If an IRC service can connect to multiple servers, the server name is part of the address path. If it connects to only one server, the server can be omitted from the address.

The `Provenance` class provides `encode()` to produce the URI from its fields, and `Provenance.decode(uri)` to parse a URI back into a Provenance. Encoding and decoding use `java.net.URI` for proper percent-encoding and validation.

---

## API Design

### Public Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Create account, sends verification email |
| POST | `/auth/verify` | Verify email with token |
| POST | `/auth/login` | Authenticate |
| POST | `/auth/logout` | Invalidate session |
| POST | `/auth/forgot-password` | Request password reset email |
| POST | `/auth/reset-password` | Reset password with token |
| GET | `/posts` | List posts (paginated) |
| GET | `/posts/{slug}` | Get single post |
| GET | `/posts/search?q=` | Search posts |
| POST | `/posts` | Submit new post (draft) |
| PUT | `/posts/{slug}` | Edit own draft |
| GET | `/posts/{slug}/comments` | Get comments |
| POST | `/posts/{slug}/comments` | Add comment |
| PUT | `/comments/{id}` | Edit comment (5 min window) |

### Admin Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/posts/pending` | List drafts awaiting approval |
| PUT | `/admin/posts/{id}/approve` | Approve and set publishedAt |
| PUT | `/admin/posts/{id}/reject` | Reject post |
| PUT | `/admin/posts/{id}` | Edit any post |
| DELETE | `/admin/posts/{id}` | Soft delete |
| DELETE | `/admin/posts/{id}?hard=true` | Hard delete (permanent) |
| DELETE | `/admin/comments/{id}` | Soft delete |
| DELETE | `/admin/comments/{id}?hard=true` | Hard delete (permanent) |
| POST | `/admin/categories` | Create category |
| POST | `/admin/tags` | Create tag |

### RSS Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/feed` | Full RSS feed |
| GET | `/feed/category/{slug}` | Filtered by category |
| GET | `/feed/tag/{slug}` | Filtered by tag |

---

## Build System

### Maven Multi-Module Structure

```
streampack/
├── pom.xml                          # Parent POM
├── model/                           # Shared domain model, DTOs
├── router/                          # Core SI infrastructure
├── operations-blog/                 # Post, Comment, Category, Tag
├── operations-user/                 # Auth, registration, profile
├── operations-factoid/              # Factoid (future)
├── service-http/                    # HTTP/REST adapter
├── service-irc/                     # IRC adapter (future)
├── service-discord/                 # Discord adapter (future)
├── service-slack/                   # Slack adapter (future)
├── service-rss/                     # RSS consumer (future)
└── app/                             # Deployable application
```

### Coordinates

```xml
<groupId>com.enigmastation.streampack</groupId>
<artifactId>streampack-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

### Module Responsibilities

| Module | Purpose |
|--------|---------|
| **model** | Domain entities, request/response types, interfaces |
| **router** | SI channels, operations dispatcher, translation service |
| **operations-blog** | PostOperation, CommentOperation, CategoryOperation, TagOperation |
| **operations-user** | AuthOperation, RegistrationOperation, ProfileOperation, PasswordResetOperation |
| **service-http** | HTTP adapter, REST controllers, templates |
| **app** | Assembles modules, application.yml, main class |

### Classpath-Based Activation

Modules use Spring Boot autoconfiguration. Presence on classpath enables the feature:

```kotlin
@Configuration
@ConditionalOnProperty(prefix = "streampack.irc", name = ["enabled"], havingValue = "true")
class IrcAutoConfiguration
```

The `app` module's POM determines which features are included by uncommenting dependencies.

### MVP Module Set

For jvm.news Phase 1-2: `model`, `router`, `operations-user`, `operations-blog`, `service-http`, `app`

---

## Technical Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| Runtime | Java 21+ (JVM) |
| [[Hunted/Chapter 2/Framework|Framework]] | Spring Boot 3.2+ |
| Messaging | Spring Integration |
| Security | Spring Security (sessions + JWT) |
| Persistence | Spring Data JPA (PostgreSQL) |
| Migrations | Flyway |
| Search | PostgreSQL full-text (MVP) |
| Reference UI | Thymeleaf + HTMX |
| Email | Spring Mail |
| Build | Maven |
| Testing | JUnit 5, Testcontainers (PostgreSQL) |

### Testing Standards

**Coverage target: ~70%**

This is a pragmatic target, not a hard gate. Rationale:
- Exception handling for IO/network failures is difficult to test and rare in practice
- Chasing 100% leads to brittle tests that mock the world
- Focus coverage on business logic, not infrastructure glue

What to cover well:
- Domain logic (operations, validation, state transitions)
- Translation layer (input parsing)
- Authorization rules

What's acceptable to skip:
- Exception handlers for infrastructure failures
- [[Hunted/Chapter 2/Framework|Framework]] integration boilerplate
- Trivial getters/DTOs

### Kotlin Conventions

The codebase uses Kotlin idioms:
- Data classes for domain entities and DTOs
- Sealed classes for request/response hierarchies
- Extension functions where they improve readability
- Null safety enforced (no `!!` except in tests)

### Concurrency Model

The application uses **Java virtual threads** (Project Loom) rather than Kotlin coroutines. Virtual threads provide the same non-blocking IO benefits without function coloring (`suspend` everywhere) or coroutine scope management.

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

With this enabled, blocking calls (JDBC, HTTP clients, file IO) automatically yield their platform thread while waiting. Write straightforward blocking code - the runtime handles concurrency.

> **Note**: Code examples in this document use Java syntax for familiarity. Actual implementation uses Kotlin equivalents (e.g., `data class` instead of `record`).

---

## Infrastructure

### Rate Limiting

Rate limiting is handled externally via **nginx + fail2ban**, not within the application. The app is not throughput-bound; the primary attack vector is HTTP abuse, which is better handled at the reverse proxy layer.

### Spam Prevention

Spam prevention uses **honeypot fields + timing checks** - no external services, no data harvesting, no CAPTCHA puzzles.

**Honeypot field**: A hidden form field (CSS `display: none`) that humans never see but bots auto-fill. If populated, reject the submission silently.

**Timing check**: Record when the form was loaded. If submission arrives faster than a human could plausibly type (e.g., < 3 seconds), reject.

```kotlin
// Honeypot - hidden field filled = bot
if (request.honeypot.isNotBlank()) {
    log.info("Honeypot triggered, rejecting submission")
    return reject()
}

// Timing - too fast = bot
val elapsed = Duration.between(formLoaded, Instant.now())
if (elapsed.seconds < 3) {
    log.info("Timing check failed, rejecting submission")
    return reject()
}
```

This handles casual automated spam with zero external dependencies and zero privacy concerns. If sophisticated bots become a problem later, Cloudflare Turnstile is a clean escalation path.

---

## MVP Phases

### Phase 1: Core Blog
- [ ] User registration with email verification
- [ ] Username/password authentication
- [ ] Password reset flow
- [ ] Post submission (draft/moderation queue)
- [ ] Post approval/scheduling
- [ ] Categories and tags
- [ ] Honeypot spam prevention
- [ ] Reference frontend (Thymeleaf/HTMX)

### Phase 2: Community
- [ ] Nested comments (5 min edit window)
- [ ] RSS feed output
- [ ] Basic search
- [ ] OAuth (Google, GitHub)

### Phase 3: Multi-Protocol
- [ ] IRC, Discord, Slack adapters
- [ ] Cross-protocol notifications

### Phase 4: Knowledge
- [ ] Factoid CRUD
- [ ] Entity linking

### Phase 5: Federation
- [ ] RSS consumption
- [ ] Subscription routing

---

*Last updated: 2025-01-25*
