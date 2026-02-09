# API Reference

All request and response bodies are JSON (`Content-Type: application/json`).
Field names are camelCase and match the Kotlin property names exactly - no custom serialization annotations.
All entity IDs are UUIDv7 strings (e.g. `"01234567-89ab-7def-8123-456789abcdef"`).

## Authentication

Protected endpoints require a Bearer token in the `Authorization` header:

```
Authorization: Bearer <JWT>
```

Tokens are obtained from `POST /auth/login` or `POST /auth/refresh`.
The JWT encodes a `UserPrincipal` (see Common Types below).
Token lifetime is 24 hours by default.

## Common Types

**UserPrincipal** - represents an authenticated user:

```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "username": "dreamreal",
  "displayName": "Joe Ottinger",
  "role": "USER"
}
```

`role` is one of: `GUEST`, `USER`, `ADMIN`, `SUPER_ADMIN` (hierarchical, lowest to highest).

**ProblemDetail** - all errors use RFC 9457 format:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Human-readable error message",
  "instance": "/auth/register"
}
```

## Implemented Endpoints: Authentication

---

### POST /auth/register

Create a new account.
Sends a verification email with a link to confirm the email address.

**Auth**: None

**Request**:
```json
{
  "username": "dreamreal",
  "email": "joe@example.com",
  "displayName": "Joe Ottinger",
  "password": "s3cureP@ss"
}
```

All fields required.

**Success (200)**:
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "username": "dreamreal",
  "displayName": "Joe Ottinger",
  "role": "USER"
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 409 | Username or email already in use | "Username already taken" / "Email already taken" |
| 400 | Other validation failure | Varies |

**Side effect**: A verification email is sent to the provided address containing a link of the form `/auth/verify?token=<uuid>`.
The user can log in immediately but email is not yet verified.

---

### POST /auth/verify

Verify an email address using the token from the verification email.

**Auth**: None

**Request**:
```json
{
  "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Success (200)**: `"Email verified"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 400 | Token invalid, expired, or already used | "Invalid or expired token" |

---

### POST /auth/login

Authenticate with username and password.

**Auth**: None

**Request**:
```json
{
  "username": "dreamreal",
  "password": "s3cureP@ss"
}
```

**Success (200)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "principal": {
    "id": "01234567-89ab-7def-8123-456789abcdef",
    "username": "dreamreal",
    "displayName": "Joe Ottinger",
    "role": "USER"
  }
}
```

Store `token` for use in `Authorization: Bearer <token>` on protected endpoints.
`principal` provides the current user's identity and role for UI display/routing.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Bad credentials or account not found | "Invalid credentials" |

---

### POST /auth/logout

Client-side logout.
This is a no-op on the server - JWT tokens are stateless.
The client should discard its stored token.

**Auth**: None

**Request**: Empty body (or no body)

**Success**: `204 No Content` (no response body)

---

### POST /auth/forgot-password

Request a password reset email.
Always returns the same success message regardless of whether the email exists - this prevents account enumeration.

**Auth**: None

**Request**:
```json
{
  "email": "joe@example.com"
}
```

**Success (200)**: `"If an account with that email exists, a reset link has been sent"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 400 | Malformed request | Varies |

**Side effect**: If the email matches an account, a password reset email is sent containing a link of the form `/auth/reset-password?token=<uuid>`.
Reset tokens expire after 1 hour.

---

### POST /auth/reset-password

Reset password using a token from the forgot-password email.
This is the user-facing reset (token-based), not the admin reset.

**Auth**: None

**Request**:
```json
{
  "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "newPassword": "n3wS3cure!"
}
```

**Success (200)**: `"Password reset successfully"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 400 | Token invalid, expired, or already used | "Invalid or expired token" |

---

### POST /auth/refresh

Exchange a valid (non-expired) JWT for a fresh token.
Use this to extend sessions without re-authenticating.

**Auth**: None (the token is in the request body, not the header)

**Request**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Success (200)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...(new token)",
  "principal": {
    "id": "01234567-89ab-7def-8123-456789abcdef",
    "username": "dreamreal",
    "displayName": "Joe Ottinger",
    "role": "USER"
  }
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Token invalid or expired | "Invalid or expired token" |

---

### PUT /auth/password

Change the authenticated user's password.

**Auth**: Required (Bearer token)

**Request**:
```json
{
  "oldPassword": "currentP@ss",
  "newPassword": "n3wS3cure!"
}
```

**Success (200)**: `"Password changed successfully"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token or wrong old password | "Not authenticated" / "Invalid credentials" |
| 400 | Other validation failure | Varies |

---

### DELETE /auth/account

Delete the authenticated user's account (soft delete).
Admin/super-admin can delete other users by providing a username.

**Auth**: Required (Bearer token)

**Request**:
```json
{
  "username": null
}
```

`username` is optional.
Omit or set to `null` for self-deletion.
Provide a username string to delete another user (requires admin privileges).

**Success (200)**: `"Account deleted"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Deleting another user without admin role | "Insufficient privileges" |
| 400 | User not found or other error | Varies |

---

## Implemented Endpoints: Admin User Management

All admin endpoints require authentication.
The authenticated user's role determines access.

---

### PUT /admin/users/{username}/role

Change a user's role.

**Auth**: Required (Super-admin only)

**Path parameters**: `username` - the target user's username

**Request**:
```json
{
  "newRole": "ADMIN"
}
```

`newRole` is one of: `GUEST`, `USER`, `ADMIN`, `SUPER_ADMIN`.

**Success (200)**:
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "username": "dreamreal",
  "displayName": "Joe Ottinger",
  "role": "ADMIN"
}
```

Returns the updated UserPrincipal reflecting the new role.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller is not super-admin | "Insufficient privileges" |
| 400 | Target user not found or other error | Varies |

---

### POST /admin/users/{username}/reset-password

Admin-initiated password reset.
Generates a temporary password for the user (no email sent).

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `username` - the target user's username

**Request**: Empty body (username comes from the path)

**Success (200)**:
```json
{
  "username": "dreamreal",
  "temporaryPassword": "xK7mNpQ2rT9vW3"
}
```

The admin should communicate the temporary password to the user through a secure channel.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin privileges | "Insufficient privileges" |
| 400 | Target user not found or other error | Varies |

---

## Implemented Endpoints: Comments

---

### GET /posts/{year}/{month}/{slug}/comments

Get the threaded comment tree for a post.
The slug path matches the post's URL structure (e.g. `2026/01/my-post-title`).

**Auth**: None (anonymous users see all non-deleted comments; authenticated users see editability flags)

**Path parameters**: `year`, `month`, `slug` - the post's slug path segments

**Success (200)**:
```json
{
  "postId": "01234567-89ab-7def-8123-456789abcdef",
  "comments": [
    {
      "id": "11111111-1111-7111-8111-111111111111",
      "authorId": "22222222-2222-7222-8222-222222222222",
      "authorDisplayName": "Joe Ottinger",
      "renderedHtml": "<p>Great article!</p>",
      "createdAt": "2026-01-15T10:30:00Z",
      "updatedAt": "2026-01-15T10:30:00Z",
      "deleted": false,
      "editable": true,
      "children": []
    }
  ],
  "totalActiveCount": 1
}
```

Deleted comments appear as `{"authorId": null, "authorDisplayName": "Anonymous", "renderedHtml": "[deleted]", "deleted": true}` to preserve thread structure.
The `editable` flag reflects whether the current user can edit the comment (author within 5 minutes, or admin).

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 404 | Slug does not resolve to a post | "Post not found" |

---

### POST /posts/{year}/{month}/{slug}/comments

Add a comment to a post.
Requires email verification.

**Auth**: Required (Bearer token, email verified)

**Path parameters**: `year`, `month`, `slug` - the post's slug path segments

**Request**:
```json
{
  "parentCommentId": null,
  "markdownSource": "This is my comment."
}
```

`parentCommentId` is optional.
Omit or set to `null` for a top-level comment.
Provide a comment UUID to create a nested reply.

**Success (201)**:
```json
{
  "id": "11111111-1111-7111-8111-111111111111",
  "postId": "01234567-89ab-7def-8123-456789abcdef",
  "authorDisplayName": "Joe Ottinger",
  "renderedHtml": "<p>This is my comment.</p>",
  "createdAt": "2026-01-15T10:30:00Z"
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 400 | Email not verified | "Email verification required" |
| 400 | Blank comment content | "Comment content is required" |
| 404 | Slug does not resolve to a post | "Post not found" |
| 404 | Parent comment UUID not found | "Parent comment not found" |

---

### PUT /comments/{id}

Edit a comment's markdown content.
Authors can edit within 5 minutes of creation.
Admins can edit anytime.

**Auth**: Required (Bearer token)

**Path parameters**: `id` - the comment's UUID

**Request**:
```json
{
  "markdownSource": "Updated comment text."
}
```

**Success (200)**:
```json
{
  "id": "11111111-1111-7111-8111-111111111111",
  "postId": "01234567-89ab-7def-8123-456789abcdef",
  "authorDisplayName": "Joe Ottinger",
  "renderedHtml": "<p>Updated comment text.</p>",
  "createdAt": "2026-01-15T10:30:00Z"
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Not the comment author | "Not authorized to edit this comment" |
| 403 | More than 5 minutes since creation | "Edit window has expired" |
| 404 | Comment not found or deleted | "Comment not found" |
| 400 | Blank comment content | "Comment content is required" |

---

## Implemented Endpoints: Admin Comment Management

All admin comment endpoints require authentication with Admin or Super-admin role.

---

### DELETE /admin/comments/{id}

Delete a comment.
Soft-deletes by default (comment shows as "[deleted]" in thread).
Add `?hard=true` for permanent removal.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `id` - the comment's UUID

**Query parameters**: `hard` (optional, default `false`) - set to `true` for permanent deletion

**Request**: No body required.

**Success (200)** (soft delete):
```json
{
  "id": "11111111-1111-7111-8111-111111111111",
  "message": "Comment deleted"
}
```

**Success (200)** (hard delete):
```json
{
  "id": "11111111-1111-7111-8111-111111111111",
  "message": "Comment permanently removed"
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Caller lacks admin role | "Admin access required" |
| 404 | Comment not found | "Comment not found" |
| 400 | Comment already soft-deleted (soft delete only) | "Comment is already deleted" |

---

## User Flows

**Registration and email verification**:
1. `POST /auth/register` - creates account, returns UserPrincipal, sends verification email
2. User clicks link in email (contains token)
3. `POST /auth/verify` with the token - marks email as verified
4. User can log in immediately after step 1, but email-gated features (commenting) require step 3

**Login and session management**:
1. `POST /auth/login` - returns JWT token + UserPrincipal
2. Store token client-side (localStorage, cookie, etc.)
3. Include `Authorization: Bearer <token>` on protected requests
4. `POST /auth/refresh` before token expires to get a fresh token
5. `POST /auth/logout` - discard the stored token client-side

**Forgot password**:
1. `POST /auth/forgot-password` with email - always returns success (prevents enumeration)
2. If email exists, user receives reset email with token link
3. `POST /auth/reset-password` with token + new password
4. User logs in with new password

**Change password** (while logged in):
1. `PUT /auth/password` with old and new password (requires Bearer token)

**Admin password reset**:
1. `POST /admin/users/{username}/reset-password` - returns temporary password
2. Admin communicates temporary password to user out-of-band

**Commenting on a post**:
1. User must be logged in with a verified email
2. `GET /posts/{year}/{month}/{slug}/comments` - view existing comments
3. `POST /posts/{year}/{month}/{slug}/comments` - add a top-level comment or reply (include `parentCommentId` for replies)
4. `PUT /comments/{id}` - edit own comment within 5 minutes of creation

**Admin comment moderation**:
1. `DELETE /admin/comments/{id}` - soft-delete a comment (shows as "[deleted]" in thread)
2. `DELETE /admin/comments/{id}?hard=true` - permanently remove a comment and its children

## Endpoint Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | No | Create account |
| POST | `/auth/verify` | No | Verify email token |
| POST | `/auth/login` | No | Authenticate |
| POST | `/auth/logout` | No | Client-side logout (204) |
| POST | `/auth/forgot-password` | No | Request reset email |
| POST | `/auth/reset-password` | No | Reset password with token |
| POST | `/auth/refresh` | No | Refresh JWT |
| PUT | `/auth/password` | Yes | Change own password |
| DELETE | `/auth/account` | Yes | Delete account |
| PUT | `/admin/users/{username}/role` | Super-admin | Change user role |
| POST | `/admin/users/{username}/reset-password` | Admin+ | Reset user password |
| GET | `/posts/{year}/{month}/{slug}/comments` | No | Get comment thread |
| POST | `/posts/{year}/{month}/{slug}/comments` | Yes (verified) | Add comment |
| PUT | `/comments/{id}` | Yes | Edit comment (5 min window) |
| DELETE | `/admin/comments/{id}` | Admin+ | Soft/hard delete comment |

## Planned Endpoints (Not Yet Implemented)

These are part of the design but have no HTTP adapter yet.

**Blog**:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/posts` | List posts (paginated) |
| GET | `/posts/{slug}` | Get single post |
| GET | `/posts/search?q=` | Search posts |
| POST | `/posts` | Submit new post (draft) |
| PUT | `/posts/{slug}` | Edit own draft |

**Admin Blog**:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/admin/posts/pending` | List drafts awaiting approval |
| PUT | `/admin/posts/{id}/approve` | Approve and set publishedAt |
| PUT | `/admin/posts/{id}/reject` | Reject post |
| PUT | `/admin/posts/{id}` | Edit any post |
| DELETE | `/admin/posts/{id}` | Soft delete |
| DELETE | `/admin/posts/{id}?hard=true` | Hard delete (permanent) |
| POST | `/admin/categories` | Create category |
| POST | `/admin/tags` | Create tag |

**RSS**:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/feed` | Full RSS feed |
| GET | `/feed/category/{slug}` | Filtered by category |
| GET | `/feed/tag/{slug}` | Filtered by tag |
