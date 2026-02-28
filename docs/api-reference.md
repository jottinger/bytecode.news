# API Reference

All request and response bodies are JSON (`Content-Type: application/json`).
Field names are camelCase and match the Kotlin property names exactly - no custom serialization annotations.
All entity IDs are UUIDv7 strings (e.g. `"01234567-89ab-7def-8123-456789abcdef"`).

For authentication flows, see [Authentication](authentication.md).
For deployment and configuration, see [Deployment Guide](deployment.md).

## Authentication

Protected endpoints require a Bearer token in the `Authorization` header:

```
Authorization: Bearer <JWT>
```

Tokens are obtained from `POST /auth/otp/verify`, OIDC callback, or `POST /auth/refresh`.
The JWT encodes a `UserPrincipal` (see Common Types below).
Token lifetime is 24 hours by default.
See [Authentication](authentication.md) for details on the OTP and OIDC flows.

## Common Types

**UserPrincipal** - represents an authenticated user:

```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "username": "testuser",
  "displayName": "Test User",
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

### POST /auth/otp/request

Request a one-time sign-in code.
A 6-digit code is emailed to the provided address.
Always returns 202 regardless of whether the email matches an account (prevents account enumeration).

**Auth**: None

**Request**:
```json
{
  "email": "joe@example.com"
}
```

**Success (202)**: `"If that email is registered or valid, a code has been sent"` (plain JSON string)

**Side effect**: If the email is valid, a 6-digit code is generated with a 10-minute expiry (configurable via `streampack.otp.expiration-minutes`) and sent via email.
Maximum 3 active codes per email address.

---

### POST /auth/otp/verify

Verify a one-time sign-in code and receive a JWT.
If no account exists for the email, one is created automatically.
OTP verification implies email verification.

**Auth**: None

**Request**:
```json
{
  "email": "joe@example.com",
  "code": "123456"
}
```

**Success (200)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "principal": {
    "id": "01234567-89ab-7def-8123-456789abcdef",
    "username": "joe",
    "displayName": "joe",
    "role": "USER"
  }
}
```

Store `token` for use in `Authorization: Bearer <token>` on protected endpoints.
`principal` provides the current user's identity and role for UI display/routing.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Wrong code, expired code, or no active code | "Invalid or expired code" |

---

### POST /auth/logout

Client-side logout.
This is a no-op on the server - JWT tokens are stateless.
The client should discard its stored token.

**Auth**: None

**Request**: Empty body (or no body)

**Success**: `204 No Content` (no response body)

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
    "username": "testuser",
    "displayName": "Test User",
    "role": "USER"
  }
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Token invalid or expired | "Invalid or expired token" |

---

### DELETE /auth/account

Erase the authenticated user's account permanently.
Creates an anonymous sentinel user, reassigns all posts and comments to the sentinel, deletes OTP codes, and hard-deletes the original user record.
Service bindings and verification tokens are removed via FK cascade.

**Auth**: Required (Bearer token)

**Request**:
```json
{
  "username": null
}
```

`username` is optional.
Omit or set to `null` for self-erasure.
Provide a username string to erase another user (requires admin privileges).

**Success (200)**: `"Account deleted"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Erasing another user without admin role | "Insufficient privileges" |
| 400 | User not found | "User not found" |
| 400 | User already erased | "User is already erased" |
| 400 | Attempting to erase a super admin | "Cannot delete a super admin" |

---

### GET /auth/export

Export the authenticated user's data as JSON (GDPR data portability).

**Auth**: Required (Bearer token)

**Success (200)**:
```json
{
  "profile": {
    "username": "testuser",
    "email": "joe@example.com",
    "displayName": "Test User",
    "role": "USER",
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "posts": [
    {
      "title": "My Post",
      "markdownSource": "...",
      "status": "APPROVED",
      "createdAt": "2026-01-15T09:00:00Z",
      "publishedAt": "2026-01-15T12:00:00Z"
    }
  ],
  "comments": [
    {
      "postTitle": "Target Post",
      "markdownSource": "...",
      "createdAt": "2026-01-16T10:00:00Z"
    }
  ]
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |

---

## Implemented Endpoints: Admin User Management

All admin endpoints require authentication.
The authenticated user's role determines access.

---

### PUT /admin/users/{username}/suspend

Suspend a user account.
The user cannot log in while suspended, but their content remains attributed and navigable for admin review.
Reversible via unsuspend.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `username` - the target user's username

**Request**: No body required.

**Success (200)**: `"Account suspended"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin role | "Insufficient privileges" |
| 400 | User not found | "User not found" |
| 400 | User is not active | "User is not active" |
| 400 | Target is a super admin | "Cannot suspend a super admin" |

---

### PUT /admin/users/{username}/unsuspend

Restore a suspended user account to active status.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `username` - the target user's username

**Request**: No body required.

**Success (200)**: `"Account unsuspended"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin role | "Insufficient privileges" |
| 400 | User not found | "User not found" |
| 400 | User is not suspended | "User is not suspended" |

---

### DELETE /admin/users/{username}

Erase a user account (admin-initiated).
Same erasure semantics as `DELETE /auth/account` - creates sentinel, reassigns content, hard-deletes user.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `username` - the target user's username

**Request**: No body required.

**Success (200)**: `"Account deleted"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin role | "Insufficient privileges" |
| 400 | User not found | "User not found" |
| 400 | User already erased | "User is already erased" |
| 400 | Target is a super admin | "Cannot delete a super admin" |

---

### DELETE /admin/users/{username}/purge

Hard-delete all content belonging to an erased user sentinel, then remove the sentinel itself.
Only works on users with ERASED status.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `username` - the erased sentinel's username (e.g. `erased-a7f3b2c1`)

**Request**: No body required.

**Success (200)**: `"Content purged"` (plain JSON string)

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin role | "Insufficient privileges" |
| 404 | User not found | "User not found" |
| 400 | Target is not an erased sentinel | "Target user is not an erased sentinel" |

---

### GET /admin/users?status={status}

List users filtered by account status.
Primarily used to find erased sentinels for review or purge.

**Auth**: Required (Admin or Super-admin)

**Query parameters**: `status` - one of `ACTIVE`, `SUSPENDED`, `ERASED`

**Success (200)**:
```json
[
  {
    "id": "01234567-89ab-7def-8123-456789abcdef",
    "username": "erased-a7f3b2c1",
    "displayName": "[deleted]",
    "role": "GUEST"
  }
]
```

Returns a list of UserPrincipal objects matching the requested status.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin role | "Insufficient privileges" |

---

### GET /admin/users/{username}/export

Export a user's data for admin review (e.g. before erasure).
Same response shape as `GET /auth/export`.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `username` - the target user's username

**Success (200)**: Same JSON shape as `GET /auth/export`.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Not authenticated" |
| 403 | Caller lacks admin role | "Insufficient privileges" |
| 400 | User not found | "User not found" |

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
  "username": "testuser",
  "displayName": "Test User",
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
      "authorDisplayName": "Test User",
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
  "authorDisplayName": "Test User",
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
  "authorDisplayName": "Test User",
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

## Implemented Endpoints: Posts

---

### GET /posts

List published posts, paginated.

**Auth**: None

**Query parameters**:
- `page` (optional, default `0`) - zero-based page index
- `size` (optional, default `20`) - page size

**Success (200)**:
```json
{
  "posts": [
    {
      "id": "01234567-89ab-7def-8123-456789abcdef",
      "title": "Understanding Virtual Threads",
      "slug": "2026/01/understanding-virtual-threads",
      "excerpt": "Virtual threads change the concurrency model...",
      "authorDisplayName": "Test User",
      "publishedAt": "2026-01-15T12:00:00Z",
      "tags": ["java", "concurrency"],
      "categories": ["JVM Internals"]
    }
  ],
  "page": 0,
  "totalPages": 3,
  "totalCount": 42
}
```

Only posts with `status = APPROVED`, `deleted = false`, and `publishedAt <= now` are included.

---

### GET /posts/{year}/{month}/{slug}

Get a single published post by its slug path.

**Auth**: None

**Path parameters**: `year`, `month`, `slug` - the post's slug path segments (e.g. `2026/01/my-post-title`)

**Success (200)**:
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "title": "Understanding Virtual Threads",
  "slug": "2026/01/understanding-virtual-threads",
  "renderedHtml": "<p>Virtual threads change the concurrency model...</p>",
  "excerpt": "Virtual threads change the concurrency model...",
  "authorId": "22222222-2222-7222-8222-222222222222",
  "authorDisplayName": "Test User",
  "status": "APPROVED",
  "publishedAt": "2026-01-15T12:00:00Z",
  "createdAt": "2026-01-14T09:00:00Z",
  "updatedAt": "2026-01-14T11:00:00Z",
  "commentCount": 5,
  "tags": ["java", "concurrency"],
  "categories": ["JVM Internals"]
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 404 | Slug does not resolve to a post | "Post not found" |

---

### GET /posts/search

Full-text search across published posts.
Searches title (weighted higher) and excerpt.

**Auth**: None

**Query parameters**:
- `q` (required) - search query string
- `page` (optional, default `0`) - zero-based page index
- `size` (optional, default `20`) - page size

**Success (200)**: Same shape as `GET /posts` - a `ContentListResponse` with matching posts ranked by relevance.

Returns an empty list for blank queries.
Only searches published, non-deleted posts.

---

### POST /posts

Submit a new blog post as a draft.
When `streampack.blog.anonymous-submission` is `false` (the default), authentication is required.
When `true`, anonymous submissions are accepted.

**Auth**: Required when anonymous submission is disabled; optional when enabled

**Request**:
```json
{
  "title": "Understanding Virtual Threads",
  "markdownSource": "Virtual threads change the concurrency model...",
  "tags": ["java", "concurrency"],
  "categoryIds": ["01234567-89ab-7def-8123-456789abcdef"],
  "website": "",
  "formLoadedAt": 1709740800000
}
```

`tags` and `categoryIds` are optional (default empty).
Tags are created automatically if they don't exist yet.
Category IDs that don't exist or point to deleted categories are silently skipped.

**Spam prevention fields**:

| Field | Type | Description |
|-------|------|-------------|
| `website` | string? | Honeypot field. Frontends must include it as a hidden form field (hidden via CSS, not `type="hidden"`). Leave empty. If a bot auto-fills it, the submission is silently rejected with a fake 201 response. |
| `formLoadedAt` | long | Epoch milliseconds when the form was rendered. Frontends must set this to `Date.now()` on form load. Required. Submissions arriving less than 3 seconds after form load are silently rejected with a fake 201 response. Submissions omitting this field are also silently rejected. |

**Success (201)**:
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "title": "Understanding Virtual Threads",
  "slug": "2026/01/understanding-virtual-threads",
  "excerpt": "Virtual threads change the concurrency model...",
  "status": "DRAFT",
  "authorId": "22222222-2222-7222-8222-222222222222",
  "authorDisplayName": "Test User",
  "createdAt": "2026-01-14T09:00:00Z",
  "tags": ["java", "concurrency"],
  "categories": ["JVM Internals"]
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | No auth token and anonymous submission disabled | "Authentication required" |
| 400 | Email not verified | "Email verification required" |
| 400 | Blank title | "Title is required" |
| 400 | Blank content | "Content is required" |

---

### PUT /posts/{id}

Edit a post.
Authors can edit their own drafts.
Admins can edit any post.

**Auth**: Required (Bearer token)

**Path parameters**: `id` - the post's UUID

**Request**:
```json
{
  "title": "Updated Title",
  "markdownSource": "Updated content...",
  "tags": ["java", "concurrency", "loom"],
  "categoryIds": ["01234567-89ab-7def-8123-456789abcdef"]
}
```

`tags` and `categoryIds` are optional (default empty).
Edit replaces all existing tag and category associations.

**Success (200)**: Returns a `ContentDetail` (same shape as `GET /posts/{year}/{month}/{slug}`).

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Not the author and not admin | "Not authorized to edit this post" |
| 404 | Post not found or deleted | "Post not found" |

---

## Implemented Endpoints: Admin Post Management

All admin post endpoints require authentication with Admin or Super-admin role.

---

### GET /admin/posts/pending

List draft posts awaiting review, paginated.

**Auth**: Required (Admin or Super-admin)

**Query parameters**:
- `page` (optional, default `0`) - zero-based page index
- `size` (optional, default `20`) - page size

**Success (200)**: Same shape as `GET /posts` - a `ContentListResponse` with draft posts.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Caller lacks admin role | "Admin access required" |

---

### PUT /admin/posts/{id}/approve

Approve a draft post and set its publication date.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `id` - the post's UUID

**Request**:
```json
{
  "publishedAt": "2026-01-15T12:00:00Z"
}
```

Set `publishedAt` to a future time for scheduled publishing, or to now/past for immediate publication.

**Success (200)**: Returns a `ContentDetail` with `status: "APPROVED"`.

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Caller lacks admin role | "Admin access required" |
| 404 | Post not found or deleted | "Post not found" |
| 400 | Post already approved | "Post is already approved" |

---

### PUT /admin/posts/{id}

Admin edit of any post (same as `PUT /posts/{id}` but uses admin privilege).

**Auth**: Required (Admin or Super-admin)

**Request and response**: Same as `PUT /posts/{id}`.

---

### DELETE /admin/posts/{id}

Delete a post.
Soft-deletes by default (hidden from public, visible to admins).
Add `?hard=true` for permanent removal.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `id` - the post's UUID

**Query parameters**: `hard` (optional, default `false`) - set to `true` for permanent deletion

**Request**: No body required.

**Success (200)** (soft delete):
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "message": "Post deleted"
}
```

**Success (200)** (hard delete):
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "message": "Post permanently removed"
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Caller lacks admin role | "Admin access required" |
| 404 | Post not found | "Post not found" |
| 400 | Post already soft-deleted (soft delete only) | "Post is already deleted" |

---

## Implemented Endpoints: Categories

---

### GET /categories

List all active (non-deleted) categories.

**Auth**: None

**Success (200)**:
```json
[
  {
    "id": "01234567-89ab-7def-8123-456789abcdef",
    "name": "JVM Internals",
    "slug": "jvm-internals",
    "parentName": null
  },
  {
    "id": "11111111-1111-7111-8111-111111111111",
    "name": "Kotlin Multiplatform",
    "slug": "kotlin-multiplatform",
    "parentName": "Kotlin"
  }
]
```

`parentName` is null for top-level categories.

---

## Implemented Endpoints: Admin Category Management

All admin category endpoints require authentication with Admin or Super-admin role.

---

### POST /admin/categories

Create a new category.

**Auth**: Required (Admin or Super-admin)

**Request**:
```json
{
  "name": "Kotlin",
  "parentId": null
}
```

`parentId` is optional.
Omit or set to `null` for a top-level category.
Provide a category UUID to create a child category.

**Success (201)**:
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "name": "Kotlin",
  "slug": "kotlin",
  "parentName": null
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Caller lacks admin role | "Admin access required" |
| 400 | Blank name | "Category name is required" |
| 400 | Duplicate name | "Category name already exists" |
| 404 | Parent category not found or deleted | "Parent category not found" |

---

### DELETE /admin/categories/{id}

Soft-delete a category.
Hidden from selection, existing post associations preserved.

**Auth**: Required (Admin or Super-admin)

**Path parameters**: `id` - the category's UUID

**Request**: No body required.

**Success (200)**:
```json
{
  "id": "01234567-89ab-7def-8123-456789abcdef",
  "message": "Category deleted"
}
```

**Errors**:

| Status | Condition | Detail |
|--------|-----------|--------|
| 401 | Missing/invalid token | "Authentication required" |
| 403 | Caller lacks admin role | "Admin access required" |
| 404 | Category not found | "Category not found" |
| 400 | Category already deleted | "Category is already deleted" |

---

## User Flows

**OTP sign-in (new or returning user)**:
1. `POST /auth/otp/request` with email - code is sent if the email is valid (always returns 202)
2. User checks email for the 6-digit code
3. `POST /auth/otp/verify` with email and code - returns JWT + UserPrincipal
4. If no account exists for that email, one is created automatically
5. Store token client-side (localStorage, cookie, etc.)
6. Include `Authorization: Bearer <token>` on protected requests

**OIDC sign-in (Google / GitHub)**:
1. Redirect user to `/oauth2/authorization/google` or `/oauth2/authorization/github`
2. User authenticates with the provider
3. Browser is redirected to `/auth/callback#token=<jwt>`
4. Frontend extracts the JWT from the URL fragment

**Session management**:
1. `POST /auth/refresh` before token expires to get a fresh token
2. `POST /auth/logout` - discard the stored token client-side

**Data export (GDPR)**:
1. `GET /auth/export` - download own data as JSON (profile, posts, comments)

**Account erasure (self-service GDPR)**:
1. Optionally export data first: `GET /auth/export`
2. `DELETE /auth/account` with `{}` - creates an anonymous sentinel, reassigns content, hard-deletes identity
3. Client should discard the stored JWT

**Admin: suspend a user**:
1. `PUT /admin/users/{username}/suspend` - freeze the account (login blocked, content preserved)
2. Review the user's content: `GET /admin/users/{username}/export`
3. `PUT /admin/users/{username}/unsuspend` - restore the account if appropriate

**Admin: erase a user**:
1. `GET /admin/users/{username}/export` - review the user's data
2. Optionally hard-delete specific toxic content via `DELETE /admin/posts/{id}?hard=true` / `DELETE /admin/comments/{id}?hard=true`
3. `DELETE /admin/users/{username}` - create sentinel, reassign remaining content, scrub identity
4. Optionally `DELETE /admin/users/{sentinel-username}/purge` - remove all remaining content and the sentinel

**Admin: list erased users**:
1. `GET /admin/users?status=ERASED` - find sentinel users for review or purge

**Post submission and approval**:
1. User creates a draft: `POST /posts` with title, markdown, optional tags and categories
2. Draft is visible to the author and admins only
3. Author can edit freely: `PUT /posts/{id}`
4. Admin reviews pending drafts: `GET /admin/posts/pending`
5. Admin approves with a publication date: `PUT /admin/posts/{id}/approve`
6. Post becomes publicly visible when `publishedAt <= now`

**Searching posts**:
1. `GET /posts/search?q=virtual+threads` - full-text search across title and excerpt
2. Results ranked by relevance, only published posts included

**Commenting on a post**:
1. User must be logged in with a verified email
2. `GET /posts/{year}/{month}/{slug}/comments` - view existing comments
3. `POST /posts/{year}/{month}/{slug}/comments` - add a top-level comment or reply (include `parentCommentId` for replies)
4. `PUT /comments/{id}` - edit own comment within 5 minutes of creation

**Admin comment moderation**:
1. `DELETE /admin/comments/{id}` - soft-delete a comment (shows as "[deleted]" in thread)
2. `DELETE /admin/comments/{id}?hard=true` - permanently remove a comment and its children

**Admin category management**:
1. `POST /admin/categories` - create a new category (optionally nested under a parent)
2. `GET /categories` - list active categories (public, for populating UI selectors)
3. `DELETE /admin/categories/{id}` - soft-delete a category (existing post associations preserved)

## Endpoint Summary

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/otp/request` | No | Request sign-in code (202) |
| POST | `/auth/otp/verify` | No | Verify code, get JWT |
| POST | `/auth/logout` | No | Client-side logout (204) |
| POST | `/auth/refresh` | No | Refresh JWT |
| DELETE | `/auth/account` | Yes | Erase account (creates sentinel) |
| GET | `/auth/export` | Yes | Export own data as JSON |
| PUT | `/admin/users/{username}/role` | Super-admin | Change user role |
| PUT | `/admin/users/{username}/suspend` | Admin+ | Suspend user account |
| PUT | `/admin/users/{username}/unsuspend` | Admin+ | Unsuspend user account |
| DELETE | `/admin/users/{username}` | Admin+ | Erase user account |
| DELETE | `/admin/users/{username}/purge` | Admin+ | Purge erased sentinel content |
| GET | `/admin/users?status=` | Admin+ | List users by status |
| GET | `/admin/users/{username}/export` | Admin+ | Export user data (admin) |
| GET | `/posts` | No | List published posts |
| GET | `/posts/{year}/{month}/{slug}` | No | Get single post |
| GET | `/posts/search?q=` | No | Search published posts |
| POST | `/posts` | Yes (verified) | Submit new post (draft) |
| PUT | `/posts/{id}` | Yes | Edit post |
| GET | `/admin/posts/pending` | Admin+ | List drafts for review |
| PUT | `/admin/posts/{id}/approve` | Admin+ | Approve and schedule post |
| PUT | `/admin/posts/{id}` | Admin+ | Admin edit any post |
| DELETE | `/admin/posts/{id}` | Admin+ | Soft/hard delete post |
| GET | `/categories` | No | List active categories |
| POST | `/admin/categories` | Admin+ | Create category |
| DELETE | `/admin/categories/{id}` | Admin+ | Soft-delete category |
| GET | `/posts/{year}/{month}/{slug}/comments` | No | Get comment thread |
| POST | `/posts/{year}/{month}/{slug}/comments` | Yes (verified) | Add comment |
| PUT | `/comments/{id}` | Yes | Edit comment (5 min window) |
| DELETE | `/admin/comments/{id}` | Admin+ | Soft/hard delete comment |

## Factoid Endpoints

Read-only REST endpoints for factoid browsing and search.
No authentication required.

---

### `GET /factoids`

Paginated listing with optional search.

**Query parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `q` | string | (none) | Search term (filters by selector and text content) |
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Page size (max 100) |

**Response:** Spring Data `Page<FactoidSummaryResponse>`

```json
{
  "content": [
    {
      "selector": "spring",
      "locked": false,
      "updatedBy": "testuser",
      "updatedAt": "2026-02-25T10:30:00Z"
    }
  ],
  "totalElements": 142,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

---

### `GET /factoids/{selector}`

Single factoid with all rendered attributes.

**Response:** `FactoidDetailResponse`

```json
{
  "selector": "spring",
  "locked": false,
  "updatedBy": "testuser",
  "updatedAt": "2026-02-25T10:30:00Z",
  "attributes": [
    {
      "type": "text",
      "value": "A popular Java application framework",
      "rendered": "spring is A popular Java application framework."
    },
    {
      "type": "urls",
      "value": "https://spring.io",
      "rendered": "URL: https://spring.io"
    }
  ]
}
```

**404** when no factoid exists for the selector.

Only attributes with non-empty values that are marked `includeInSummary` are returned.
