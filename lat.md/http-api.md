# HTTP API

The HTTP API exposes site features, admin workflows, authentication, and machine-readable integrations over JSON contracts.

## API Conventions

The web contract uses consistent JSON structures so frontends and tools can reuse shared client behavior.

Request and response bodies are JSON, field names match Kotlin property names, IDs are UUIDv7 strings, and errors use RFC 9457 `ProblemDetail` responses. Protected endpoints expect Bearer tokens unless a flow explicitly passes tokens in the body.

## Authentication Endpoints

Authentication routes cover OTP request and verification, token refresh, logout, account erasure, and user export flows.

These endpoints reflect the identity model in [[authentication#Authentication and Identity]] and are the primary web entry point for end-user sessions.

## Admin User Management

Administrative user endpoints support moderation, lifecycle control, role changes, and data export.

These routes let admins suspend users, erase accounts, purge erased sentinels, filter by status, export a target user's data, and adjust roles when policy allows it.

## Content and Comment Endpoints

Blog routes expose published content, author workflows, moderation queues, and threaded discussion behavior.

The API includes post listing, slug-based post lookup, search, creation and editing flows, pending-post review, approval, comment retrieval, comment creation, and comment moderation.

These endpoints form the primary contract for the web frontends and public content surfaces.

Author-facing edit forms hydrate draft content from the API once per target post and then preserve local unsaved edits during normal client rerenders. This prevents transient auth or preview updates from reloading server state over in-progress draft changes.

## Rendered Markdown Admonitions

Rendered blog HTML may include Flexmark admonition structure, and the UIs are expected to style that emitted structure directly.

`MarkdownRenderingService` enables Flexmark admonitions and emits the `.adm-*` class structure plus inline SVG symbol definitions needed for note, warning, tip, and related callouts. The read surfaces in `ui-nextjs`, `ui-reference`, and `ui-basic` provide static CSS for that structure and intentionally do not ship the Flexmark collapse JS, so admonitions always render as readable expanded blocks.

The `ui-nextjs` author preview also adapts Flexmark admonition syntax locally so draft previews can show admonition blocks before the backend render path runs. This preview support is scoped to the Next.js authoring UI; `ui-reference` remains lower-priority and is not treated as the canonical authoring surface.

Authors write admonitions with Flexmark syntax such as `!!! note "Remember"` followed by an indented body. Collapsible markers like `???` and `???+` are accepted, but current web UIs render them as normal expanded callouts instead of interactive disclosure widgets.

## Taxonomy and Category Endpoints

Taxonomy routes provide structured discovery metadata for tags, categories, and category administration.

This allows UIs and tools to navigate published content, build filters, and manage controlled category sets.

## Factoid and Feature Endpoints

The web contract also exposes bot-oriented data surfaces such as factoids, karma views, webhook ingestion, and feature discovery.

These endpoints bridge the blog application with the broader bot runtime so the same deployment can serve content, automation, and auxiliary integrations.

## MCP Endpoint

The API includes a read-only JSON-RPC MCP endpoint for LLM and tool clients.

`POST /mcp` exposes public search and retrieval tools for posts, factoids, and taxonomy without exposing admin or mutating actions. The integration contract is described in [[integrations#Integrations#MCP Access]].

## Feed Canonicalization

Frontend-hosted feeds must preserve the public site origin even when they fetch feed data from the API host.

The backend uses `X-Forwarded-Host` when a frontend proxy provides it and otherwise falls back to the configured canonical blog base URL. This prevents RSS readers from seeing API-host links when the public feed was requested from a frontend domain.

## Public Metadata

Public frontend routes should emit complete share metadata so previews stay consistent outside the site itself.

The Next.js app uses the canonical `BLOG_BASE_URL` as its metadata base and supplies `openGraph` plus `twitter` metadata for public content surfaces including article detail, search, taxonomy indexes, taxonomy detail pages, factoid pages, and karma views. These routes emit canonical URLs, `og:url`, and share-image references from that same base so link previews do not drift across mirrors or internal hosts.

## API Version Signaling

Header-based version signaling exists in advisory mode so clients can prepare for future explicit contract routing.

Clients may send `Accept-Version`, and responses echo the resolved version via `Content-Version` and `Accept-Version`. Current behavior does not yet gate endpoint logic by version.
