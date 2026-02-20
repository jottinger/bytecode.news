# Mail Egress Service

The mail service delivers egress messages with `MAILTO` provenance as email via SMTP.
It is egress-only - there is no ingress mail handling.

## Service enablement

Set `MAIL_ENABLED=true` and configure SMTP via environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `MAIL_ENABLED` | `false` | Activates the mail egress subscriber |
| `MAIL_HOST` | `localhost` | SMTP server hostname |
| `MAIL_PORT` | `25` | SMTP server port |

The from address is controlled by `StreampackProperties.mail.from` (defaults to `noreply@jvm.news`).

## Linking a user's email (ServiceBinding)

A SUPER_ADMIN creates the binding via LinkProtocol:

```
link user <username> mailto "" <email@example.com>
```

Example: `link user jottinger mailto "" dreamreal@gmail.com`

This creates a `ServiceBinding` row associating the email address with the user account.
Operations that want to notify a user can look up their mailto binding and construct a mailto provenance for delivery.

## How operations send memos

An operation that wants to email a user:

1. Looks up the user's mailto `ServiceBinding`
2. Constructs a `Provenance(protocol = Protocol.MAILTO, replyTo = "user@example.com")`
3. Sends the message through `EventGateway`
4. `MailEgressSubscriber` picks it up and delivers via SMTP

All mail is sent as Nevet (from the system-configured address).
Nevet never impersonates users - it sends on its own behalf.

## Relationship to EmailService

`EmailService` in lib-core handles transactional emails (verification, password reset).
The mail egress service handles notification delivery for operations.
They share the same SMTP configuration but serve different purposes.
