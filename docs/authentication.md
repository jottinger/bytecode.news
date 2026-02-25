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
The server generates a 6-digit numeric code (via `SecureRandom`), stores it in the `one_time_codes` table with a 5-minute expiry, and sends it via email.

The response is always `200 OK` regardless of whether the email matches an existing account.
This prevents account enumeration.

Rate limiting: a maximum of 3 active (unused, unexpired) codes per email address.
Requests beyond this limit are silently accepted (returning 200) but no code is generated or sent.

### Verifying a code

The client sends `{email, code}` to `POST /auth/otp/verify`.
If the code is valid and not expired, it is marked as used and the identity convergence flow runs.
The response includes a JWT and the user's principal.

If the email does not match any existing account, a new account is created automatically.
OTP verification implies email verification, so no separate email verification step is needed.

### Code lifecycle

- Codes are 6 digits, zero-padded (e.g., `007842`)
- Codes expire after 5 minutes
- Each code can only be used once
- Maximum 3 active codes per email at any time

## OIDC Flow

OIDC uses Spring Security's OAuth2 Client with Google and GitHub as providers.
See [OIDC Setup Guide](oidc-setup-guide.md) for configuration instructions.

### How it works

1. The client redirects the user to `/oauth2/authorization/google` (or `/oauth2/authorization/github`)
2. Spring Security handles the OAuth2 Authorization Code flow
3. On successful authentication, `OidcAuthenticationSuccessHandler` extracts the email and display name from the identity provider
4. The same `UserConvergenceService.converge()` runs as with OTP
5. The handler redirects to `${streampack.baseUrl}/auth/callback#token=<jwt>`
6. The frontend extracts the JWT from the URL fragment

### Provider differences

- **Google**: Returns email via the standard OIDC `email` claim
- **GitHub**: Returns email via the `email` attribute on the OAuth2 user profile

Both are handled transparently by the success handler.

### Conditional activation

OIDC is only enabled when OAuth2 client registrations are configured.
If `GOOGLE_CLIENT_ID` and `GITHUB_CLIENT_ID` are both empty, OIDC endpoints are not registered and the app runs OTP-only.

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

## Account Deletion

`DELETE /auth/account` soft-deletes the authenticated user's account.
Admins can delete other users by providing a username in the request body.
Super admin accounts cannot be deleted by other admins.

## Super Admin Bootstrap

The first super admin is created via the `ADMIN_EMAIL` environment variable.
On startup, if no `SUPER_ADMIN` user exists and `ADMIN_EMAIL` is set, a super admin account is created with that email.
The admin logs in via OTP like everyone else.

If `ADMIN_EMAIL` is not set, the bootstrap is skipped and a warning is logged.
