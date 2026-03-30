# User-Facing Operations

User-visible behavior is expressed as operations that expose knowledge tools, moderation features, utilities, and chat workflows through a consistent command model.

## Command Model

Most commands run through the same addressing, role, and translation rules regardless of protocol.

Users can address the bot explicitly with a signal or mention, while selected features such as karma, calculator shortcuts, calendar shortcuts, and URL title lookups can listen to ambient traffic. Direct messages are always treated as addressed commands.

The runtime behavior behind this model is described in [[architecture#Messaging Architecture#Operations]].

## Knowledge Base

Knowledge features revolve around factoids, specification lookup, and dictionary-style retrieval.

Factoids act as long-term key-value memory with attributes such as text, URLs, tags, languages, and see-also links. Additional lookup features can fetch external reference data and cache it into the factoid system for later reuse.

## Reputation

Karma tracks lightweight community sentiment through unaddressed increment and decrement syntax.

The feature includes protections against self-awards, configurable subject filters, and leaderboard-style queries. It is intentionally conversational, so it tolerates noisy real-world text while avoiding common false positives.

## Utility Commands

General utilities provide lightweight information or calculation features without requiring full web UI flows.

Documented examples include calculator behavior, calendar queries, weather lookup, URL title extraction, and version reporting.

## AI Features

AI-backed commands expose model-assisted interactions while remaining part of the same shared operations runtime.

Ask-style prompts, poem generation, poem analysis, and sentiment analysis are documented as user-facing features rather than a separate subsystem.

## Communication and Bridging

Communication features support delayed delivery, channel linking, and other cross-user or cross-channel flows.

Tell commands, channel bridging, and related workflows rely on the routing and provenance mechanisms described in [[architecture#Messaging Architecture#Advanced Routing]].

Comment composition uses plain-text markdown entry, and UI surfaces present that input in monospace so authored markup stays visually scannable while typing.

## Games and Feeds

The bot includes interactive game features and inbound content feed integrations to keep channels active.

Documented examples include 21 matches, hangman, Markov impersonation, RSS feeds, GitHub repository watching, and article idea capture.

## Administration

Administrative operations expose configuration and moderation controls directly through the bot-style command surface.

These commands cover user management, protocol linking, service activation, operation toggles, channel configuration, and site-content management for system pages and sidebar links.
