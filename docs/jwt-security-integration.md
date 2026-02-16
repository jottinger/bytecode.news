# JWT / Spring Security Integration

This document describes an incomplete migration: wiring JWT authentication into the Spring Security filter chain so that endpoint protection is enforced by the framework rather than manually in each controller.

## Current State (the problem)

Spring Security is present but does nothing.
`WebSecurityConfiguration` sets `.anyRequest().permitAll()` - every request passes through regardless of credentials.

Each controller manually duplicates a `resolveUser(HttpServletRequest)` method that extracts the `Authorization: Bearer` header and calls `JwtService.validateToken()`.
Four controllers each carry their own copy: `AuthController`, `CommentController`, `AdminCommentController`, `AdminUserController`.
Each also carries its own `unauthorized()` helper.

This means:
- Spring Security provides zero enforcement at the HTTP layer.
- Admin endpoints (`/admin/**`) are only protected because the *operation layer* rejects non-admin callers.
- There is no single place that defines which endpoints require authentication.
- Error messages for unauthenticated requests are inconsistent ("Not authenticated" in some controllers, "Authentication required" in others).

The operation-layer auth checks are correct and should remain as defense-in-depth, but the HTTP layer should be the first line of enforcement.

## Target State

A `OncePerRequestFilter` in `lib-core` reads the Bearer token, validates it via `JwtService`, and populates the Spring `SecurityContext` with a custom `Authentication` object.
The `SecurityFilterChain` in `service-blog` defines endpoint rules (which paths are public, which require authentication, which require admin).
Controllers stop doing manual token resolution and instead read the principal from `SecurityContextHolder`.

### Design decisions

- **Filter and token class live in `lib-core`**, alongside `JwtService` and `UserPrincipal`.
  Any future HTTP-serving module (service-factoid, service-irc-admin) can reuse them.
- **`SecurityFilterChain` stays in `service-blog`** because endpoint rules are application-specific policy.
- **The filter always attempts to populate SecurityContext**, even on `permitAll` endpoints.
  Example: `GET /posts/{year}/{month}/{slug}/comments` is public, but the response includes `editable` flags based on whether the caller is the comment's author.
  Best-effort auth means the filter runs, and if a valid token is present, the user is available.
- **The filter is NOT a `@Component`**.
  It is instantiated explicitly in the `SecurityFilterChain` bean to avoid Spring Boot's automatic servlet filter registration (which would cause it to run twice).
- **Operation-layer auth checks remain untouched** as defense-in-depth.
- **Custom `AuthenticationEntryPoint` and `AccessDeniedHandler`** return `ProblemDetail` JSON matching the existing `{"detail": "..."}` contract.

## Files to Create

### 1. `lib-core/.../core/security/JwtAuthenticationToken.kt`

A Spring Security `Authentication` implementation wrapping `UserPrincipal`.

Responsibilities:
- Extend `AbstractAuthenticationToken`.
- Grant `SimpleGrantedAuthority("ROLE_${principal.role.name}")` - this maps to Spring Security's `hasRole()` convention (Spring adds the `ROLE_` prefix internally, so `hasRole("ADMIN")` matches `ROLE_ADMIN`).
- `getPrincipal()` returns the `UserPrincipal`.
- `getCredentials()` returns null (the token has already been validated; no need to carry the raw JWT).
- Constructed as already-authenticated (`isAuthenticated = true`).

### 2. `lib-core/.../core/security/JwtAuthenticationFilter.kt`

A `OncePerRequestFilter` that bridges the raw HTTP `Authorization` header to Spring Security's `SecurityContext`.

Behavior:
- Extract the `Authorization` header.
  If absent or not prefixed with `Bearer `, continue the filter chain without touching the `SecurityContext` (the request proceeds as anonymous).
- Call `JwtService.validateToken(token)`.
  If it returns null (invalid, expired, malformed), continue the chain as anonymous.
- If valid, wrap the `UserPrincipal` in a `JwtAuthenticationToken` and set it on `SecurityContextHolder.getContext().authentication`.
- Always call `filterChain.doFilter()` - this filter never rejects requests.
  Rejection is Spring Security's authorization layer's job.

Not annotated `@Component`.
Instantiated in the `SecurityFilterChain` configuration.

### 3. `lib-core/.../core/security/SecurityContextExtensions.kt`

A single top-level utility function that replaces the four duplicated `resolveUser()` methods:

```kotlin
fun currentUserPrincipal(): UserPrincipal? =
    (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)
        ?.principal as? UserPrincipal
```

Controllers call `currentUserPrincipal()` instead of manually parsing the request.

### 4. `service-blog/.../controller/JwtAuthenticationEntryPoint.kt`

An `AuthenticationEntryPoint` that fires when an unauthenticated request hits a protected endpoint.

Returns HTTP 401 with body:
```json
{"type": "about:blank", "title": "Unauthorized", "status": 401, "detail": "Authentication required"}
```

This standardizes the inconsistent messages across controllers.

### 5. `service-blog/.../controller/JwtAccessDeniedHandler.kt`

An `AccessDeniedHandler` that fires when an authenticated user lacks the required role (e.g., a `USER` hitting `/admin/**`).

Returns HTTP 403 with body:
```json
{"type": "about:blank", "title": "Forbidden", "status": 403, "detail": "Admin access required"}
```

## Files to Modify

### 6. `service-blog/.../controller/WebSecurityConfiguration.kt`

Currently:
```kotlin
http
    .csrf { it.disable() }
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    .authorizeHttpRequests { it.anyRequest().permitAll() }
    .build()
```

Becomes (pseudocode):
```kotlin
http
    .csrf { it.disable() }
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    .exceptionHandling {
        it.authenticationEntryPoint(JwtAuthenticationEntryPoint())
        it.accessDeniedHandler(JwtAccessDeniedHandler())
    }
    .addFilterBefore(JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter::class.java)
    .authorizeHttpRequests { auth ->
        // endpoint rules - see table below
    }
    .build()
```

Constructor-inject `JwtService` so it can instantiate the filter.

### 7-10. Controller refactoring

All four controllers get the same transformation.

**Remove from each controller:**
- `jwtService` constructor parameter (no longer needed for auth resolution).
- `resolveUser(HttpServletRequest)` method.
- `unauthorized()` helper method.
- `HttpServletRequest` parameters from endpoint methods that only used it for `resolveUser()`.

**Replace in authenticated endpoints:**
```kotlin
// Before
val user = resolveUser(httpRequest) ?: return unauthorized("Not authenticated")

// After
val user = currentUserPrincipal()!!
```

On authenticated endpoints, Spring Security guarantees a principal exists (it rejects the request before it reaches the controller).
Using `!!` is acceptable here per the project's convention ("No `!!` except in tests" - but here the filter chain provides the guarantee, and the plan noted using `?: error("Security context missing principal")` as an alternative).

**Replace in optional-auth endpoints** (like `getComments`):
```kotlin
// Before
val user = resolveUser(httpRequest)

// After
val user = currentUserPrincipal()
```

Nullable - the endpoint is `permitAll`, so the principal may or may not be present.

**Files:**
- `AuthController.kt` - `changePassword()` and `deleteAccount()` methods
- `CommentController.kt` - `getComments()` (optional), `createComment()`, `editComment()`
- `AdminCommentController.kt` - `deleteComment()`
- `AdminUserController.kt` - `changeRole()`, `resetPassword()`

## Endpoint Security Rules

| Method | Path | Rule | Rationale |
|--------|------|------|-----------|
| POST | `/auth/login`, `/auth/register`, `/auth/verify`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/refresh` | `permitAll` | Auth endpoints are inherently public |
| GET | `/posts/**` | `permitAll` | Blog reads are public (filter still populates principal if token present) |
| GET | `/factoids/**` | `permitAll` | Factoid reads are public |
| GET | `/swagger-ui/**`, `/v3/api-docs/**` | `permitAll` | API docs |
| PUT | `/auth/password` | `authenticated` | Change own password |
| DELETE | `/auth/account` | `authenticated` | Delete own account |
| POST | `/posts/**/comments` | `authenticated` | Create comment (operation also checks email-verified) |
| PUT | `/comments/**` | `authenticated` | Edit comment (operation also checks author + edit window) |
| * | `/admin/**` | `hasAnyRole("ADMIN", "SUPER_ADMIN")` | Admin operations |
| * | everything else | `authenticated` | Secure by default |

Rule ordering matters.
Spring Security evaluates `requestMatchers` top-to-bottom, first match wins.
Place `permitAll` rules before the catch-all `authenticated` rule.

## Test Impact

### 5 assertions that must change

These tests currently assert error messages produced by the controllers' `resolveUser()` / `unauthorized()` helpers.
After the change, Spring Security's entry point and access denied handler produce the responses instead, with standardized messages.

| File | Line | Test method | Old `$.detail` value | New `$.detail` value | Why |
|------|------|-------------|---------------------|---------------------|-----|
| `AuthControllerTests.kt` | 317 | `change password without auth returns 401` | `"Not authenticated"` | `"Authentication required"` | Entry point responds before controller |
| `AuthControllerTests.kt` | 360 | `delete account without auth returns 401` | `"Not authenticated"` | `"Authentication required"` | Entry point responds before controller |
| `AdminUserControllerTests.kt` | 109 | `unauthenticated role change returns 401` | `"Not authenticated"` | `"Authentication required"` | Entry point responds before controller |
| `AdminUserControllerTests.kt` | 152 | `regular user gets 403 on password reset` | `"Insufficient privileges"` | `"Admin access required"` | USER on `/admin/**` is now rejected by Spring Security's access denied handler, not the operation |
| `AdminUserControllerTests.kt` | 164 | `unauthenticated password reset returns 401` | `"Not authenticated"` | `"Authentication required"` | Entry point responds before controller |

### Tests that pass without changes

- `CommentControllerTests` 401s already assert `"Authentication required"`.
- `AdminCommentControllerTests` 401 already asserts `"Authentication required"`.
- `AdminCommentControllerTests` 403 already asserts `"Admin access required"`.
- `AdminUserControllerTests` `non-super-admin gets 403 on role change` uses an ADMIN token - Spring Security lets ADMIN through to `/admin/**`, and the operation rejects with `"Insufficient privileges"` (correct, SUPER_ADMIN required for role changes) - passes as-is.

### Operation tests: zero changes

Operation tests dispatch through `EventGateway` directly, not through HTTP/Spring Security.
Their auth assertions test the operation layer and are completely unaffected.

### New tests to write

**`JwtAuthenticationFilterTests`** in `lib-core/src/test/kotlin/.../core/security/`:
- Valid token populates SecurityContext with correct principal.
- Missing `Authorization` header leaves SecurityContext empty.
- Invalid/expired token leaves SecurityContext empty.
- Non-Bearer authorization scheme (e.g., `Basic`) leaves SecurityContext empty.
- Filter always calls `filterChain.doFilter()` (never short-circuits).

**`JwtAuthenticationTokenTests`** in `lib-core/src/test/kotlin/.../core/security/`:
- Authorities contain `ROLE_USER` for a USER principal, `ROLE_ADMIN` for ADMIN, etc.
- `getPrincipal()` returns the `UserPrincipal`.
- `getCredentials()` returns null.
- `getName()` returns the username.
- `isAuthenticated` is true.

## Implementation Order

1. Create `JwtAuthenticationToken` (no dependencies beyond Spring Security + UserPrincipal).
2. Create `JwtAuthenticationFilter` (depends on JwtService + JwtAuthenticationToken).
3. Create `SecurityContextExtensions` (depends on JwtAuthenticationToken).
4. Write and run lib-core security tests.
5. Create `JwtAuthenticationEntryPoint` (standalone).
6. Create `JwtAccessDeniedHandler` (standalone).
7. Update `WebSecurityConfiguration` (depends on filter + handlers).
8. Refactor all four controllers (depends on SecurityContextExtensions).
9. Update 5 test assertions.
10. Run `./mvnw clean test` - full suite should be green.

Steps 1-3 can be done together.
Steps 5-6 can be done together.
Steps 7-9 should be sequential (configuration before controllers, controllers before test assertions).

## Out of Scope

- **Anonymous content submission**: CLAUDE.md mentions "Anyone (including anonymous) submits", but `CreateContentOperation` currently requires auth and there is no content submission controller. That is a separate feature.
- **CORS configuration**: Configured in `WebSecurityConfiguration` via the `CORS_ORIGINS` env var (comma-separated allowed origins, defaults to `localhost:3000`, `localhost:3003`, `bytecode.news`).
- **OAuth**: Listed in the roadmap for Phase 2.
- **Operation-layer auth refactoring**: The operation layer's own auth checks stay exactly as they are. This change only affects the HTTP entry point.
