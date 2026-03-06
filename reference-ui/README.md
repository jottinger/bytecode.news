# Nevet Reference UI

A minimal reference frontend for the Nevet API.
Built with vanilla JS, Vite, and Pico CSS.
No framework - every `fetch()` call is visible, making this readable documentation for anyone building their own frontend.

## Prerequisites

- **Node.js** (LTS, 22.x or later)
- **Nevet backend** running on `localhost:8080` - see [docs/deployment.md](../docs/deployment.md) for backend setup
- **Docker** (optional, for Mailpit email testing)

## Quick Start

```bash
# Install dependencies
npm install

# Start dev server (port 3003, proxies /api to localhost:8080)
npm run dev

# Production build
npm run build
```

The backend must be running on `localhost:8080`.
The Vite dev server proxies `/api/*` requests to the backend, stripping the `/api` prefix.

## Development Workflow

### How the Vite proxy works

In development, the frontend runs on `localhost:3003`.
All requests to `/api/*` are proxied to `localhost:8080` with the `/api` prefix stripped.
So `fetch('/api/posts')` in the browser becomes `GET http://localhost:8080/posts` on the backend.
This is configured in `vite.config.js` and mirrors what nginx does in production.

### What you see at each step

1. **Home page** (`/`) - lists published posts with pagination. Empty until posts are approved.
2. **Login** (`/login`) - email-based OTP form, plus OIDC buttons if Google/GitHub are configured.
3. **Submit** (`/submit`) - visible after login. Creates a draft post in markdown.
4. **Admin queue** (`/admin/posts`) - visible to admins. Approve or delete submitted drafts.
5. **Post detail** (`/posts/:slug`) - full post with nested comment thread. Comments require login.
6. **Edit** (`/posts/:slug/edit`) - authors can edit their own drafts, admins can edit anything.
7. **Profile** (`/profile`) - display name editing, data export, account erasure.
8. **Search** (`/search?q=term`) - full-text search across published posts.

### Email testing with Mailpit

OTP login sends a one-time passcode via email.
In local development, use Mailpit to catch these emails without a real mail server.

```bash
# Start Mailpit alongside PostgreSQL
docker compose --profile mail up -d
```

Configure the backend to use Mailpit's SMTP port by adding to your `.env`:

```
MAIL_HOST=localhost
MAIL_PORT=3025
```

Open `http://localhost:8025` to see the Mailpit web UI.
When you request an OTP login, the email with the passcode appears there.

### OIDC on localhost

Google and GitHub OAuth can work on localhost for development.

**Google**:
1. In the [Google Cloud Console](https://console.cloud.google.com/), create OAuth credentials (see [docs/deployment.md](../docs/deployment.md#google) for full steps)
2. Add `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI
3. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`

**GitHub**:
1. In [GitHub Developer Settings](https://github.com/settings/developers), create an OAuth app
2. Set the callback URL to `http://localhost:8080/login/oauth2/code/github`
3. Set `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` in `.env`

For OIDC to redirect back to the reference UI after authentication, set in `.env`:

```
FRONTEND_URL=http://localhost:3003
```

Without this, the backend defaults to redirecting to `BASE_URL` (which is `http://localhost:8080`).

The reference UI passes an `origin` parameter when initiating OIDC so the backend knows which frontend started the flow.
After authentication, the backend redirects to `{origin}/auth/callback#token=<jwt>`, and `auth-callback.js` extracts the token.

## Project Structure

```
src/
  main.js               # Entry point: init auth, render nav, start router
  router.js             # pushState routing with path params and route guards
  api.js                # fetch() wrapper with JWT injection and ProblemDetail errors
  auth.js               # Token storage, session refresh, auth change listeners
  roles.js              # Role hierarchy (GUEST < USER < ADMIN < SUPER_ADMIN)
  views/
    home.js             # Published post listing with pagination
    post-detail.js      # Full post with nested comment thread
    search.js           # Full-text search across posts
    login.js            # OTP two-step form + OIDC provider buttons
    auth-callback.js    # Extracts JWT from OIDC redirect URL fragment
    submit-post.js      # Draft post creation form
    edit-post.js        # Edit post (loads raw markdown from backend)
    profile.js          # Profile editing, data export, account erasure
    terms.js            # Terms of service page
    privacy.js          # Privacy policy page
    cookies.js          # Cookie policy page
    admin/
      pending-posts.js  # Review queue: approve or delete drafts
      manage-users.js   # Filter by status, suspend/unsuspend/erase/purge/role
      manage-categories.js  # Create and delete categories
  components/
    nav.js              # Auth-aware navigation (role-based link visibility)
    post-card.js        # Post summary card
    comment-thread.js   # Recursive nested comment rendering
    comment-form.js     # New comment and reply forms
    pagination.js       # Page navigation
    error-display.js    # RFC 9457 ProblemDetail renderer
```

## How to Read the Code

Each file is self-contained and short.
Start with `api.js` to see how every backend call works, then `auth.js` for token management.
Views are in `src/views/` - each exports a single `render(container, params, search)` function.

Key patterns:
- `api.js` prepends `/api/` to every path, so `get('/posts')` becomes `fetch('/api/posts')`.
  The Vite proxy (dev) or nginx (production) strips `/api` before forwarding to the backend.
- JWT is stored in localStorage and injected automatically by `api.js`.
- 401 responses clear auth state and redirect to `/login`.
- Route guards in `router.js` check `auth: true` or `role: 'ADMIN'` before rendering views.
  All authorization is also enforced server-side - UI guards are convenience only.
- OIDC flow passes an `origin` parameter so the backend knows which frontend to redirect back to.

## Technology Choices

- **Vite** - dev server with proxy, production bundler
- **Vanilla JS** with ES modules - no build step required for understanding the code
- **Pico CSS** - classless semantic CSS, styles `<article>`, `<nav>`, `<form>` directly
- **pushState routing** - real URLs, requires `try_files` for production nginx

## Production Deployment

Build the static files:

```bash
npm run build
```

This produces a `dist/` directory.
Serve it with nginx using `try_files` for SPA routing:

```nginx
location / {
    root /opt/nevet/reference-ui/dist;
    try_files $uri $uri/ /index.html;
}
```

See the main project's `deploy/nginx/bytecode.news.conf` for the full server block, and [docs/deployment.md](../docs/deployment.md#reference-ui-production-deployment) for the complete production deployment guide.

Set `FRONTEND_URL` on the backend to this frontend's public URL if OIDC is configured.
Add this frontend's origin to `CORS_ORIGINS`.

## Troubleshooting

**Backend not running / connection refused**:
The Vite dev server proxies to `localhost:8080`.
If the backend is not running, every API call fails with a network error.
Start the backend first: `java -jar app/target/app-1.0.jar`.

**CORS errors in browser console**:
The backend's `CORS_ORIGINS` must include `http://localhost:3003`.
The default `.env.default` already includes this, but check your `.env` if you have overridden it.

**401 redirect loop after OIDC login**:
If `FRONTEND_URL` is not set or points to the wrong origin, the OIDC redirect lands on the wrong frontend (or the backend itself), and the token never reaches the reference UI.
Set `FRONTEND_URL=http://localhost:3003` in `.env`.

**JSON parse error on API response**:
Some backend error responses return plain text instead of JSON.
The `api.js` wrapper handles this by checking the `Content-Type` header before parsing.
If you see this, it usually means the request hit nginx or the Vite proxy's error page instead of the backend.

**OTP email not arriving**:
Make sure Mailpit is running (`docker compose --profile mail up -d`) and the backend is configured with `MAIL_HOST=localhost` and `MAIL_PORT=3025`.
Check `http://localhost:8025` for the email.

## Scope

This covers: blog content workflow, auth (OTP + OIDC), comments, admin post/user/category management, user profile editing with GDPR export and erasure, and legal pages (terms, privacy, cookies).

Not covered: factoids, karma, spam prevention, real-time updates, markdown preview.
