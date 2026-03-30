# Authentication

Streampack uses passwordless authentication.
Two methods are supported: email one-time passcode (OTP) and OpenID Connect (OIDC) via Google or GitHub.
Both paths converge on the same identity resolution: verified email -> find-or-create User -> HTTP ServiceBinding -> JWT.

## Identity Model

Email is the identity anchor.
Every authenticated user has an HTTP `ServiceBinding` where `externalIdentifier` is their email address.
The same user can also have bindings for IRC, Discord, Slack, etc., each with their own external identifiers.

When a user authenticates via OTP or OIDC, the system:

1. Normalizes the email to lowercase
2. Finds an existing User by email, or creates one
3. Derives a username from the email prefix (e.g., `joe` from `joe@example.com`), handling collisions with numeric suffixes
4. Ensures an HTTP ServiceBinding exists for that email
5. Sets `emailVerified = true` and stamps `lastLoginAt`
6. Issues a JWT

This convergence logic lives in `UserConvergenceService` and is shared by both auth paths.

## OTP Flow

OTP requires no external dependencies - just an SMTP server for sending emails.

### Requesting a code

The client sends the user's email to `POST /auth/otp/request`.
The server generates a 6-digit numeric code (via `SecureRandom`), stores it in the `one_time_codes` table with a configurable expiry (default 10 minutes, via `streampack.otp.expiration-minutes`), and sends it via email.

The response is always `202 Accepted` regardless of whether the email matches an existing account.
This prevents account enumeration.

Rate limiting: a maximum of 3 active (unused, unexpired) codes per email address.
Requests beyond this limit are silently accepted (returning 202) but no code is generated or sent.

### Verifying a code

The client sends `{email, code}` to `POST /auth/otp/verify`.
If the code is valid and not expired, it is marked as used and the identity convergence flow runs.
The response includes a JWT and the user's principal.

If the email does not match any existing account, a new account is created automatically.
OTP verification implies email verification, so no separate email verification step is needed.

### Code lifecycle

- Codes are 6 digits, zero-padded (e.g., `007842`)
- Codes expire after 10 minutes by default (configurable via `streampack.otp.expiration-minutes`)
- Each code can only be used once
- Maximum 3 active codes per email at any time

## OIDC Flow

OIDC uses Spring Security's OAuth2 Client with Google and GitHub as providers.
OIDC is activated by enabling the `oidc` Spring profile.
Without the profile, the app runs OTP-only and no OAuth2 endpoints are registered.
See [Deployment Guide](deployment.md#oidc-setup) for activation instructions and provider setup.

### How it works

1. The client redirects the user to `/oauth2/authorization/google` (or `/oauth2/authorization/github`)
2. Spring Security handles the OAuth2 Authorization Code flow
3. On successful authentication, `OidcAuthenticationSuccessHandler` extracts the email and display name from the identity provider
4. The same `UserConvergenceService.converge()` runs as with OTP
5. The handler redirects to `{origin}/auth/callback#token=<jwt>` when the initiating frontend origin is present and allowlisted; otherwise it falls back to `${streampack.frontendUrl}/auth/callback#token=<jwt>` and then `baseUrl`
6. The frontend extracts the JWT from the URL fragment

### Provider differences

- **Google**: Returns email via the standard OIDC `email` claim
- **GitHub**: Returns email via the `email` attribute on the OAuth2 user profile

Both are handled transparently by the success handler.

## JWT Tokens

JWTs are issued on successful authentication (OTP verify, OIDC callback, or token refresh).
They encode a `UserPrincipal` containing the user's ID, username, display name, and role.

- Algorithm: HMAC-SHA256
- Default lifetime: 24 hours
- Stateless: no server-side session storage
- Refresh: `POST /auth/refresh` with an existing valid token returns a new token

## Logout

Logout is a client-side operation.
`POST /auth/logout` returns `204 No Content` - the server does nothing.
The client should discard its stored JWT.

## Account Lifecycle

User accounts have three statuses: `ACTIVE`, `SUSPENDED`, and `ERASED`.

### Suspension (Tier 2)

Admin action: freezes a user's ability to log in while preserving the full content graph.
The admin can review all of the user's posts and comments before deciding next steps.
Suspension is reversible.

- `PUT /admin/users/{username}/suspend` - freeze the account
- `PUT /admin/users/{username}/unsuspend` - restore the account to active

Super admin accounts cannot be suspended.

### Account Erasure (Tier 3)

Terminal state: the user's identity is permanently removed.
Both self-service (GDPR "right to be forgotten") and admin-initiated erasure use this path.

What happens during erasure:

1. A unique anonymous sentinel User is created (`erased-<short-uuid>`, no email, display name "[deleted]", role GUEST, status ERASED)
2. All posts and comments are reassigned from the original user to the sentinel
3. One-time codes for the user's email are deleted
4. The original User record is hard-deleted (service bindings and verification tokens cascade via FK)

The sentinel preserves content grouping: an admin can still identify "these 47 comments came from the same erased account" and bulk-delete them via `DELETE /admin/users/{sentinel-username}/purge`.

- `DELETE /auth/account` - self-erasure (authenticated user erases their own account)
- `DELETE /admin/users/{username}` - admin-initiated erasure

Admins can erase active or suspended users.
Super admin accounts cannot be erased by other admins.

### Content Purge

After erasure, an admin may decide the sentinel's content should also be removed.

`DELETE /admin/users/{sentinel-username}/purge` hard-deletes all posts and comments belonging to the sentinel, then removes the sentinel itself.
Only works on users with ERASED status.

### Listing Erased Users

`GET /admin/users?status=ERASED` returns all erased sentinel users so admins can find them for review or purge.

## Data Export

`GET /auth/export` returns a JSON export of the authenticated user's data (profile, posts, comments).
Admins can export any user's data via `GET /admin/users/{username}/export` for review before erasure.

The export includes:
- Profile: username, email, display name, role, creation date
- Posts: title, markdown source, status, creation and publication dates
- Comments: target post title, markdown source, creation date

## Super Admin Bootstrap

The first super admin is created via the `ADMIN_EMAIL` environment variable.
On startup, if no `SUPER_ADMIN` user exists and `ADMIN_EMAIL` is set, a super admin account is created with that email.
The admin logs in via OTP like everyone else.

If `ADMIN_EMAIL` is not set, the bootstrap is skipped and a warning is logged.
