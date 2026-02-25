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
- Codes expire after 10 minutes (configurable via `streampack.otp.expiration-minutes`)

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

### Erase Account

Permanently erases a user's identity.
Creates an anonymous sentinel user, reassigns all posts and comments to the sentinel, deletes OTP codes, and hard-deletes the original user record.
Service bindings and verification tokens are removed via FK cascade.

The sentinel preserves content grouping: an admin can still identify "these 47 comments came from the same erased account" and bulk-delete them via the purge operation.

**Request:** `DeleteAccountRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | String? | no | Target username. Null means self-erasure. |

**Response:** `"Account deleted"` (string)

**Auth:** Required.
Self-erasure: any authenticated user (username omitted or matches own).
Erasing others: `ADMIN` or `SUPER_ADMIN` only.

**Constraints:**
- Non-admin users can only erase themselves
- Super admin accounts cannot be erased by other admins
- Super admins can self-erase (use with caution)
- Users with ERASED status cannot be erased again

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - non-admin attempting to erase another user
- `"User not found"` - target username does not exist
- `"User is already erased"` - target has already been erased
- `"Cannot delete a super admin"` - admin attempting to erase a super admin

**HTTP mapping:**
```
DELETE /auth/account
Authorization: Bearer <token>
Content-Type: application/json

{}
```

Admin-initiated erasure:
```
DELETE /admin/users/{username}
Authorization: Bearer <token>
```

---

### Export User Data

Exports a user's data as JSON for GDPR data portability.
Authenticated users export their own data.
Admins can export any user's data (for review before erasure).

**Request:** `ExportUserDataRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | String? | no | Target username. Null means export own data. |

**Response:** `UserDataExport`

| Field | Type | Description |
|-------|------|-------------|
| `profile` | ProfileExport | Username, email, display name, role, creation date |
| `posts` | List<PostExport> | Title, markdown source, status, creation and publication dates |
| `comments` | List<CommentExport> | Target post title, markdown source, creation date |

**Auth:** Required.
Self-export: any authenticated user.
Exporting others: `ADMIN` or `SUPER_ADMIN` only.

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - non-admin attempting to export another user's data
- `"User not found"` - target username does not exist

**HTTP mapping:**
```
GET /auth/export
Authorization: Bearer <token>
```

Admin export:
```
GET /admin/users/{username}/export
Authorization: Bearer <token>
```

---

### Suspend Account

Freezes a user account so they cannot log in.
Content remains attributed and navigable for admin review.
Reversible via unsuspend.

**Request:** `SuspendAccountRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | String | yes | Target username |

**Response:** `"Account suspended"` (string)

**Auth:** Required. `ADMIN` or `SUPER_ADMIN` only.

**Constraints:**
- Target must be in ACTIVE status
- Super admin accounts cannot be suspended

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - caller lacks admin role
- `"User not found"` - target username does not exist
- `"User is not active"` - target is not in ACTIVE status
- `"Cannot suspend a super admin"` - target is a super admin

**HTTP mapping:**
```
PUT /admin/users/{username}/suspend
Authorization: Bearer <token>
```

---

### Unsuspend Account

Restores a suspended user account to active status.

**Request:** `UnsuspendAccountRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | String | yes | Target username |

**Response:** `"Account unsuspended"` (string)

**Auth:** Required. `ADMIN` or `SUPER_ADMIN` only.

**Constraints:**
- Target must be in SUSPENDED status

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - caller lacks admin role
- `"User not found"` - target username does not exist
- `"User is not suspended"` - target is not in SUSPENDED status

**HTTP mapping:**
```
PUT /admin/users/{username}/unsuspend
Authorization: Bearer <token>
```

---

### Purge Erased Content

Hard-deletes all posts and comments belonging to an erased user sentinel, then removes the sentinel itself.
Only works on users with ERASED status.

**Request:** `PurgeErasedContentRequest`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sentinelUserId` | UUID | yes | ID of the erased sentinel user |

**Response:** `"Content purged"` (string)

**Auth:** Required. `ADMIN` or `SUPER_ADMIN` only.

**Constraints:**
- Target must have ERASED status (sentinel users only)

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - caller lacks admin role
- `"User not found"` - sentinel user does not exist
- `"Target user is not an erased sentinel"` - target does not have ERASED status

**HTTP mapping:**
```
DELETE /admin/users/{sentinel-username}/purge
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
| `"Cannot suspend a super admin"` | 400 Bad Request |
| `"Cannot change own role"` | 400 Bad Request |
| `"User not found"` | 400 Bad Request |
| `"User is already erased"` | 400 Bad Request |
| `"User is not active"` | 400 Bad Request |
| `"User is not suspended"` | 400 Bad Request |
| `"Target user is not an erased sentinel"` | 400 Bad Request |
| `"No service context"` | 500 Internal Server Error |
