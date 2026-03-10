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

# Run frontend tests
npm test

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
  main.js               # Entry point: render immediately, hydrate auth/features in background
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
- `api.js` prepends the API base URL to every path, so `get('/posts')` becomes `fetch('{API_BASE}/posts')`.
  The base URL is set at build time via `VITE_API_BASE` (defaults to `/api` for local dev, set to `https://api.bytecode.news` for production).
- JWT is stored in localStorage and injected automatically by `api.js`.
- 401 responses clear auth state and redirect to `/login`.
- Route guards in `router.js` check `auth: true` or `role: 'ADMIN'` before rendering views.
  All authorization is also enforced server-side - UI guards are convenience only.
- OIDC flow passes an `origin` parameter so the backend knows which frontend to redirect back to.
- Startup is non-blocking: router/nav render first, then feature/auth hydration updates UI state asynchronously.

## Technology Choices

- **Vite** - dev server with proxy, production bundler
- **Vanilla JS** with ES modules - no build step required for understanding the code
- **Pico CSS** - classless semantic CSS, styles `<article>`, `<nav>`, `<form>` directly
- **pushState routing** - real URLs, requires `try_files` for production nginx

## Production Deployment (Docker)

The reference UI ships as a Docker image with nginx serving the SPA and proxying crawler/feed requests to the backend.

### Environment variables

| Variable | Build/Runtime | Default | Purpose |
|----------|---------------|---------|---------|
| `VITE_API_BASE` | Build (`--build-arg`) | `/api` | API base URL baked into the JS bundle. Set to the full backend URL for production (e.g. `https://api.bytecode.news`). |
| `VITE_API_TIMEOUT_MS` | Build (`--build-arg`) | `8000` | Frontend API timeout in milliseconds before surfacing a network timeout error. |
| `VITE_UI_VERSION` | Build (`--build-arg`) | `dev` | Frontend version label shown in the footer status line. |
| `VITE_UI_COMMIT` | Build (`--build-arg`) | `unknown` | Frontend git commit shown in the footer status line. |
| `VITE_UI_BRANCH` | Build (`--build-arg`) | `unknown` | Frontend git branch shown in the footer status line. |
| `VITE_UI_BUILD_TIME` | Build (`--build-arg`) | `unknown` | Frontend build timestamp shown in the footer status line. |
| `BACKEND_SCHEME` | Runtime (`-e`) | `http` | Scheme nginx uses for server-side proxies (`http` or `https`). |
| `BACKEND_HOST` | Runtime (`-e`) | `backend:8080` | Host used by nginx for server-side proxies (sitemap, RSS feed, SSR). Not used by the browser. |

### Build

```bash
docker build --no-cache \
  --build-arg VITE_API_BASE=https://api.bytecode.news \
  --build-arg VITE_UI_VERSION=0.1.0 \
  --build-arg VITE_UI_COMMIT=$(git rev-parse --short HEAD) \
  --build-arg VITE_UI_BRANCH=$(git rev-parse --abbrev-ref HEAD) \
  --build-arg VITE_UI_BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ") \
  -t reference-ui reference-ui/
```

`VITE_API_BASE` is baked in at build time - the browser fetches directly from this URL.
Omit it for local dev (defaults to `/api`, which the Vite proxy or nginx forwards to the backend).

### Run

```bash
docker run -d --name reference-ui \
  -e BACKEND_SCHEME=http \
  -e BACKEND_HOST=host.docker.internal:8080 \
  --add-host host.docker.internal:host-gateway \
  -p 3001:3001 \
  reference-ui
```

`BACKEND_SCHEME` and `BACKEND_HOST` tell nginx where to proxy sitemap, RSS feed, and SSR requests.
Use `host.docker.internal:8080` when the Java backend runs on the host machine.
The `--add-host` flag makes `host.docker.internal` resolve inside the container.
If your backend is only reachable over TLS (for example `api.bytecode.news`), set:

```bash
-e BACKEND_SCHEME=https -e BACKEND_HOST=api.bytecode.news
```

### Verify Bot SSR Routing

After deploying the container, verify crawler user-agents are rewritten to backend SSR:

```bash
# Should return SSR HTML for bots
curl -s -A "Googlebot/2.1" https://bytecode.news/posts/2026/03/example | head -n 20

# Should return SPA shell for regular browsers
curl -s -A "Mozilla/5.0" https://bytecode.news/posts/2026/03/example | head -n 20
```

For the bot request, confirm the response contains server-rendered article content (not only the SPA shell markup).

### External reverse proxy

An external nginx handles SSL and forwards to the container:

```nginx
server {
    listen 443 ssl;
    server_name bytecode.news;

    # SSL certs managed by Certbot
    ssl_certificate /etc/letsencrypt/live/bytecode.news/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/bytecode.news/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3001;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Backend configuration

Set `FRONTEND_URL` on the backend to this frontend's public URL if OIDC is configured.
Add this frontend's origin to `CORS_ORIGINS`.

### What nginx proxies vs. what the browser handles

The browser (SPA) talks directly to `VITE_API_BASE` for all API calls - no proxy involved.
Nginx only proxies requests that browsers don't initiate directly:
- `/sitemap.xml` - search engine crawlers
- `/feed.xml` - RSS readers
- `/ssr/*` - server-side rendered pages for crawler user-agents (internal rewrites only)

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

Not covered: spam prevention, real-time updates, markdown preview.
