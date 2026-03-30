# Authentication and Identity

Nevet uses passwordless authentication and converges every successful login onto the same user, binding, and JWT model.

## Identity Convergence

Verified email is the durable identity anchor even when users arrive through different authentication mechanisms.

Both OTP and OIDC normalize the email, find or create a user, ensure an HTTP service binding exists, mark the email as verified, update login metadata, and issue a JWT. This keeps web identities compatible with the broader multi-protocol user model.

## OTP Authentication

OTP provides the default passwordless sign-in path with only SMTP as an external dependency.

Users request a six-digit code by email, then verify that code to receive a JWT and principal payload. Requests always return success-style responses to avoid account enumeration, and only a limited number of active codes may exist per email at once.

### Code Lifecycle

Codes are short-lived, single-use credentials designed to reduce replay and enumeration risk.

Each code is zero-padded to six digits, expires after a configurable window, and is invalid once consumed. The system silently enforces the active-code cap per email address.

## OIDC Authentication

OIDC is an optional profile-gated authentication path for Google and GitHub identities.

When the `oidc` profile is active, Spring Security handles the provider flow and the success handler extracts identity data before delegating to the same convergence logic used by OTP. The frontend then receives the resulting JWT through a callback redirect.

### Provider Differences

Provider-specific claim handling is isolated so the rest of the authentication model remains uniform.

Google supplies email through standard OIDC claims, while GitHub exposes it through OAuth2 user attributes. Those differences are normalized before convergence.

## JWT Sessions

Successful authentication produces a stateless JWT that carries the current principal and role.

Tokens are signed with HMAC-SHA256, expire after a configurable lifetime, and can be refreshed with a valid existing token. Logout is client-side because the server does not maintain session state for these tokens.

## Account Lifecycle

Accounts move through active, suspended, and erased states with increasingly destructive administrative effects.

### Suspension

Suspension blocks login while preserving the user's identity and content graph for moderation review.

Administrators can suspend and later restore a normal account, but super admins are excluded from this workflow.

### Erasure

Erasure permanently removes the original user identity while preserving authored content under an anonymous sentinel account.

Posts and comments are reassigned to a generated erased user, one-time codes are deleted, and the original user plus cascaded bindings are removed. This supports right-to-be-forgotten behavior without immediately losing the moderation context around prior content.

### Content Purge

Purging lets administrators remove the content owned by an erased sentinel after review.

This is a separate decision from erasure so the system can distinguish identity removal from content removal.

### Data Export

Export endpoints let users or admins review the full account data set before destructive actions.

Exports include profile data, posts, and comments so erasure decisions can be audited or user requests can be fulfilled cleanly.

## Super Admin Bootstrap

The first operator account is created from startup configuration when no super admin already exists.

If `ADMIN_EMAIL` is configured, startup creates a super admin bound to that email so the operator can sign in through the same OTP flow as everyone else.
