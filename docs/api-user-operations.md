# User Operations API

All user operations flow through the event system as typed request/response messages.
Protocol adapters translate HTTP (or other protocol) requests into these operation messages and translate responses back to the caller.

Every request message carries a `Provenance` header identifying the protocol, service, and (optionally) the authenticated user.

## Authentication Model

Authentication is passwordless.
Users sign in via email one-time passcode (OTP) or OIDC (Google/GitHub).
Both paths produce a JWT that is included in subsequent requests.

Operations that require authentication check `provenance.user` (a `UserPrincipal`).
The HTTP adapter is responsible for extracting the JWT from the `Authorization` header and populating the principal on the provenance before dispatching.

Privilege levels follow the `Role` hierarchy: `GUEST < USER < ADMIN < SUPER_ADMIN`.

## Operations

### OTP Request

Generates a one-time sign-in code and sends it via email.
Always returns success to prevent account enumeration.

**Request:** `OtpRequest`

| Field | Type | Required |
|-------|------|----------|
| `email` | String | yes |

**Response:** `"Code sent"` (string)

**Auth:** None (public).

**Constraints:**
- Maximum 3 active (unused, unexpired) codes per email address
- Codes expire after 5 minutes

**HTTP mapping:**
```
POST /auth/otp/request
Content-Type: application/json

{"email": "alice@example.com"}
```

---

### OTP Verify

Validates a one-time code and returns a JWT.
If no account exists for the email, one is created automatically.
OTP verification implies email verification.

**Request:** `OtpVerifyRequest`

| Field | Type | Required |
|-------|------|----------|
| `email` | String | yes |
| `code` | String | yes |

**Response:** `LoginResponse`

| Field | Type | Description |
|-------|------|-------------|
| `token` | String | JWT bearer token |
| `principal` | UserPrincipal | Authenticated user identity |

**Auth:** None (public).

**Errors:**
- `"Invalid or expired code"` - wrong code, expired code, or no active code for that email

**HTTP mapping:**
```
POST /auth/otp/verify
Content-Type: application/json

{"email": "alice@example.com", "code": "123456"}
```

---

### Token Refresh

Issues a fresh JWT from a valid existing token.
The token in the request body is the authentication - no provenance user required.

**Request:** `TokenRefreshRequest`

| Field | Type | Required |
|-------|------|----------|
| `token` | String | yes |

**Response:** `LoginResponse`

| Field | Type | Description |
|-------|------|-------------|
| `token` | String | New JWT with fresh expiration |
| `principal` | UserPrincipal | User identity from the original token |

**Auth:** None on provenance. The request token is the credential.

**Errors:**
- `"Invalid or expired token"` - token is malformed, expired, tampered, or the user has been deleted

**HTTP mapping:**
```
POST /auth/refresh
Content-Type: application/json

{"token": "<existing-jwt>"}
```

---

### Delete Account

Soft-deletes a user account (sets `deleted = true`).
Supports self-deletion and admin-initiated deletion.

**Request:** `DeleteAccountRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | String? | no | Target username. Null means self-deletion. |

**Response:** `"Account deleted"` (string)

**Auth:** Required.
Self-deletion: any authenticated user (username omitted or matches own).
Deleting others: `ADMIN` or `SUPER_ADMIN` only.

**Constraints:**
- Non-admin users can only delete themselves
- Super admin accounts cannot be deleted by other users (even admins)
- Super admins can self-delete (use with caution)

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - non-admin attempting to delete another user
- `"User not found"` - target username does not exist
- `"Cannot delete a super admin"` - admin attempting to delete a super admin

**HTTP mapping:**
```
DELETE /auth/account
Authorization: Bearer <token>
Content-Type: application/json

{"username": "targetuser"}
```

Self-deletion:
```
DELETE /auth/account
Authorization: Bearer <token>
```

---

### Change Role

Changes a user's role.
Restricted to `SUPER_ADMIN` only.

**Request:** `ChangeRoleRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | String | yes | Target user |
| `newRole` | Role | yes | `GUEST`, `USER`, `ADMIN`, or `SUPER_ADMIN` |

**Response:** `UserPrincipal` (the updated principal reflecting the new role)

**Auth:** Required. `SUPER_ADMIN` only.

**Constraints:**
- Only `SUPER_ADMIN` can change roles (not `ADMIN`)
- Cannot change own role (prevents accidental lockout)

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - caller is not `SUPER_ADMIN`
- `"Cannot change own role"` - super admin targeting themselves
- `"User not found"` - target username does not exist

**HTTP mapping:**
```
PUT /admin/users/{username}/role
Authorization: Bearer <token>
Content-Type: application/json

{"username": "alice", "newRole": "ADMIN"}
```

## Common Types

### UserPrincipal

Lightweight identity carried in message headers and returned from several operations.

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | UUIDv7 user identifier |
| `username` | String | Unique username |
| `displayName` | String | Display name |
| `role` | Role | `GUEST`, `USER`, `ADMIN`, or `SUPER_ADMIN` |

### Role Hierarchy

| Role | Can do |
|------|--------|
| `GUEST` | Read-only, no posting or commenting |
| `USER` | Submit posts (as drafts), comment, edit own profile |
| `ADMIN` | All USER + approve/reject posts, delete users/content |
| `SUPER_ADMIN` | All ADMIN + change user roles, system configuration |

### Error Response Format

All operation errors return `OperationResult.Error(message)`.
The HTTP adapter should translate these to appropriate HTTP status codes:

| Error message pattern | HTTP status |
|----------------------|-------------|
| `"Not authenticated"` | 401 Unauthorized |
| `"Invalid or expired code"` | 401 Unauthorized |
| `"Invalid or expired token"` | 401 Unauthorized |
| `"Insufficient privileges"` | 403 Forbidden |
| `"Cannot delete a super admin"` | 403 Forbidden |
| `"Cannot change own role"` | 400 Bad Request |
| `"User not found"` | 404 Not Found |
| `"No service context"` | 500 Internal Server Error |
