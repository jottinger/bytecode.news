# User Operations API

All user operations flow through the event system as typed request/response messages.
Protocol adapters translate HTTP (or other protocol) requests into these operation messages and translate responses back to the caller.

Every request message carries a `Provenance` header identifying the protocol, service, and (optionally) the authenticated user.

## Authentication Model

Operations that require authentication check `provenance.user` (a `UserPrincipal`).
The HTTP adapter is responsible for extracting the JWT from the `Authorization` header and populating the principal on the provenance before dispatching.

Privilege levels follow the `Role` hierarchy: `GUEST < USER < ADMIN < SUPER_ADMIN`.

## Operations

### Login

Authenticates a user by username and password, returning a JWT.

**Request:** `LoginRequest`

| Field | Type | Required |
|-------|------|----------|
| `username` | String | yes |
| `password` | String | yes |

**Response:** `LoginResponse`

| Field | Type | Description |
|-------|------|-------------|
| `token` | String | JWT bearer token |
| `principal` | UserPrincipal | Authenticated user identity |

**Auth:** None (public).

**Errors:**
- `"Invalid credentials"` - wrong username, wrong password, or deleted user
- `"No service context"` - provenance missing serviceId

**HTTP mapping:**
```
POST /auth/login
Content-Type: application/json

{"username": "alice", "password": "secret"}
```

---

### Registration

Creates a new user account with password.
Returns the created principal.
The frontend should call login separately to obtain a JWT.

**Request:** `RegistrationRequest`

| Field | Type | Required |
|-------|------|----------|
| `username` | String | yes |
| `email` | String | yes |
| `displayName` | String | yes |
| `password` | String | yes |

**Response:** `UserPrincipal`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | User's unique identifier |
| `username` | String | Chosen username |
| `displayName` | String | Display name |
| `role` | Role | Always `USER` for new registrations |

**Auth:** None (public).

**Errors:**
- `"Username already taken"` - duplicate username
- `"No service context"` - provenance missing serviceId

**HTTP mapping:**
```
POST /auth/register
Content-Type: application/json

{"username": "alice", "email": "alice@example.com", "displayName": "Alice", "password": "secret"}
```

---

### Change Password

Changes the authenticated user's password.
Requires knowledge of the current password.

**Request:** `ChangePasswordRequest`

| Field | Type | Required |
|-------|------|----------|
| `oldPassword` | String | yes |
| `newPassword` | String | yes |

**Response:** `"Password changed successfully"` (string)

**Auth:** Required. Any authenticated user.

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"No service context"` - provenance missing serviceId
- `"Binding not found"` - no service binding for this user/protocol
- `"No password set"` - binding has no passwordHash in metadata
- `"Invalid current password"` - oldPassword does not match

**HTTP mapping:**
```
PUT /auth/change-password
Authorization: Bearer <token>
Content-Type: application/json

{"oldPassword": "oldsecret", "newPassword": "newsecret"}
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

### Password Reset (Admin)

Admin-initiated password reset.
Generates a temporary random password and returns it to the admin.
The admin is responsible for communicating the temporary password to the user.

**Request:** `PasswordResetRequest`

| Field | Type | Required |
|-------|------|----------|
| `username` | String | yes |

**Response:** `PasswordResetResponse`

| Field | Type | Description |
|-------|------|-------------|
| `username` | String | The user whose password was reset |
| `temporaryPassword` | String | The generated temporary password |

**Auth:** Required. `ADMIN` or `SUPER_ADMIN` only.

**Errors:**
- `"Not authenticated"` - no user on provenance
- `"Insufficient privileges"` - non-admin user
- `"No service context"` - provenance missing serviceId
- `"Binding not found"` - no service binding for the target user/protocol

**HTTP mapping:**
```
POST /admin/reset-password
Authorization: Bearer <token>
Content-Type: application/json

{"username": "targetuser"}
```

**Future:** This will eventually be supplemented by a self-service flow where the user requests a reset, the system emails a time-limited token, and the user redeems it.
This requires the MAILTO protocol adapter (not yet implemented).

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
| `ADMIN` | All USER + approve/reject posts, reset passwords, delete users/content |
| `SUPER_ADMIN` | All ADMIN + change user roles, system configuration |

### Error Response Format

All operation errors return `OperationResult.Error(message)`.
The HTTP adapter should translate these to appropriate HTTP status codes:

| Error message pattern | HTTP status |
|----------------------|-------------|
| `"Not authenticated"` | 401 Unauthorized |
| `"Invalid credentials"` | 401 Unauthorized |
| `"Invalid or expired token"` | 401 Unauthorized |
| `"Insufficient privileges"` | 403 Forbidden |
| `"Cannot delete a super admin"` | 403 Forbidden |
| `"Cannot change own role"` | 400 Bad Request |
| `"User not found"` | 404 Not Found |
| `"Binding not found"` | 404 Not Found |
| `"Username already taken"` | 409 Conflict |
| `"No service context"` | 500 Internal Server Error |
