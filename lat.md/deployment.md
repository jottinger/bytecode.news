# Deployment and Operations

Deployment combines a Java backend, PostgreSQL, optional frontend processes, and adapter-specific configuration for external services.

## Local Development

Local development is designed around Docker-managed PostgreSQL, Maven builds, and optional frontend or console surfaces.

Developers set environment variables in a local file, start PostgreSQL with Docker Compose, build the Maven modules, and launch the bootable application jar. Console mode can then be used for initial administrative setup.

This workflow supports backend development, adapter testing, and frontend integration against a local stack.

## Core Configuration

Runtime behavior is controlled through environment variables that cover database access, JWT signing, base URLs, mail, CORS, and feature-specific secrets.

The most sensitive production values are the JWT secret, webhook encryption secret, database credentials, public base URL, and allowed frontend origins. Misconfiguring these breaks security or cross-surface integration quickly, so they form the minimum production checklist.

## Production Topology

Production deployment assumes a Linux host with DNS, TLS termination, PostgreSQL, and one or more application processes.

The documented setups support host-based and Docker-based combinations for the backend and frontend, with nginx acting as the public reverse proxy. The system is intended to serve both the public site and its supporting APIs from the same platform footprint.

## OIDC Setup

OIDC activation is opt-in and requires both Spring profile configuration and provider-specific credentials.

Operators enable the `oidc` profile, configure callback URLs, and provide Google or GitHub client credentials. Split-domain setups are supported so the API host can authenticate users for a separate frontend origin.
Reverse-proxy deployments should also enable Spring forwarded-header handling so provider callback URLs are generated from the public HTTPS host instead of the internal app address.

The authentication behavior behind this setup is described in [[authentication#Authentication and Identity#OIDC Authentication]].

## Adapter Setup

Protocol adapters are deployed as features of the main application and require service-specific credentials and activation.

### IRC Adapter

The IRC adapter needs explicit activation, secret externalization, and channel-level workflow setup.

This adapter is intended for long-lived bot operation and therefore treats credential handling and runtime configuration as operator concerns rather than end-user features.

### Slack Adapter

The Slack adapter depends on a configured Slack app and its associated tokens before it can join the shared operation runtime.

Like the IRC adapter, Slack activation extends the common behavior model rather than introducing separate business logic.

## Mail and AI

Mail and AI integrations are optional subsystems with operational impact beyond the core blog runtime.

Mail configuration supports OTP and transactional delivery, while AI configuration enables language-model-backed features. Both require explicit deployment-time configuration because they depend on external providers.

## Process Management and Backup

Production guidance includes process supervision, frontend service startup, and PostgreSQL backup procedures.

Documented systemd units show how to keep backend and frontend services running, while manual and automated backup flows cover database durability.
