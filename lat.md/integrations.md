# Integrations

Integrations document the specialized external interfaces that are not just ordinary user commands or blog pages.

## MCP Access

The MCP interface exposes a public, read-only JSON-RPC surface for search and retrieval by agents and tooling.

Clients connect to `POST /mcp`, complete an MCP initialize handshake, discover tools with `tools/list`, and invoke them with `tools/call`. The exposed tools cover post search, post retrieval, factoid listing, factoid retrieval, factoid search, taxonomy listing, and factoid write guidance.

This surface is intentionally non-admin and non-mutating, which keeps it safe for broad agent access.

## Parser Library

The parser library is a shared utility for command-shaped input that needs structured matching and useful validation failures.

It centralizes tokenization, optional trigger handling, quoted argument support, typed positional arguments, and match outcomes such as missing arguments or invalid arguments. Operations can still use raw parsing when flexibility matters more than strict structure.

This library supports the operation patterns described in [[architecture#Messaging Architecture#Operations#Base Classes]].

## External Protocol Adapters

Protocol adapters extend the same business behavior into IRC, Slack, Discord, console, and HTTP contexts.

Each adapter is responsible for translation and delivery, not for domain rules. That keeps integrations additive: a new transport can join the system without reimplementing core features.
