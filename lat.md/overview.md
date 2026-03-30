# Nevet

Nevet is a content hub and multi-protocol bot whose behavior stays consistent across chat adapters, HTTP APIs, and console access.

## Product Shape

The system combines a community news site with a reusable operations engine so the same domain capabilities can serve both users and other software.

The main public product is a blog and knowledge hub for bytecode.news. The same backend also exposes commands, automation surfaces, and administrative workflows for moderators and operators.

See [[architecture#Messaging Architecture]] for the execution model and [[http-api#HTTP API]] for the web contract.

## Runtime Surfaces

Nevet exposes a small set of runtime surfaces that share the same domain model and authorization rules.

- HTTP endpoints for site features, authentication, administration, and integrations
- Chat and console adapters for bot-style command execution
- A read-only MCP endpoint for LLM and tool access to public content

These surfaces differ in transport, but they converge on shared operations, services, and persistence.

## Module Layout

The repository is organized as a Maven multi-module system with layered libraries, operations, services, and UI packages.

- Core libraries hold shared domain types, persistence, integration wiring, parsing, and test support
- Operation modules implement protocol-agnostic command handling and routing logic
- Service modules expose adapters such as blog HTTP endpoints, console access, MCP, Slack, IRC, and Discord
- UI modules provide frontend clients for the blog experience

The module taxonomy is described in more detail in [[architecture#Messaging Architecture#Module Taxonomy]].
