# Deployment Guide

This guide covers both local development and production deployment.

For command reference, see [User Guide](user-guide.md).
For authentication details, see [Authentication](authentication.md).

---

## Local Development Setup

### Prerequisites

- JDK 25+
- Docker (for PostgreSQL)
- Maven wrapper is included in the repository (`./mvnw`)

### 1. Set Up the Environment File

Create a `.env` file in the project root:

```
DB_URL=jdbc:postgresql://localhost:5432/nevet
DB_USERNAME=nevet
DB_PASSWORD=nevet
CONSOLE_ENABLED=true
```

The `.env` file is in `.gitignore` and will not be committed.
`CONSOLE_ENABLED=true` activates the interactive console adapter for issuing admin commands from stdin.

### 2. Start the Database

```bash
docker compose up -d
```

This starts PostgreSQL 17 with database `nevet`, user `nevet`, password `nevet`, on port 5432.

To stop: `docker compose down`.
To stop and delete all data: `docker compose down -v`.

#### Docker Profiles

The `docker-compose.yml` includes optional profiles:

```bash
docker compose up -d                                              # PostgreSQL only (default)
docker compose --profile mail up -d                               # PostgreSQL + Mailpit
docker compose --profile frontend up -d                           # PostgreSQL + frontend
docker compose --profile backend up -d                            # PostgreSQL + backend
docker compose --profile frontend --profile backend up -d         # All three
```

For frontend in Docker with backend on host, set `API_URL=http://host.docker.internal:8080` in `.env`.

### 3. Build the Application

```bash
./mvnw clean install           # with tests
./mvnw clean install -DskipTests   # without tests
```

### 4. Start the Application

```bash
java -jar app/target/app-1.0.jar
```

On first boot, Flyway creates the schema and `SuperAdminBootstrap` creates a default superadmin:

```
========================================
  Superadmin account created
  Username: admin
  Password: H3SeTdnQ44du2HGQ
  Change this password immediately!
========================================
```

Copy that password - you need it for HTTP login.

### 5. Initial Configuration via Console

With `CONSOLE_ENABLED=true`, type commands directly into the running application's stdin.
See [User Guide](user-guide.md) for the full command reference.

**Create users**:

```
create user alice alice@example.com Alice
create user bob bob@example.com Bob admin
```

**Link protocol identities** (so users are recognized on IRC/Slack):

```
link user admin irc libera ~yournick@your/cloak
```

**Set up RSS feeds**:

```
feed add https://inside.java
feed subscribe https://inside.java to irc://libera/%23java
```

### 6. Email Testing with Mailpit

For testing OTP login locally, start Mailpit to catch outbound emails:

```bash
docker compose --profile mail up -d
```

Add to your `.env`:

```
MAIL_HOST=localhost
MAIL_PORT=3025
```

Mailpit's web UI is at `http://localhost:8025`.
When you request an OTP login, the email with the passcode appears there.

Mailpit does not send mail externally - it only captures and displays.

### HTTP Access

The blog service exposes REST endpoints on port 8080:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "THE_GENERATED_PASSWORD"}'
```

Returns a JWT for use as `Authorization: Bearer <token>`.
See [API Reference](api-reference.md) for full endpoint documentation.

---

## Production Deployment

**Assumptions**: DNS A/AAAA records already point to this server, and a working MTA is available.
All commands assume Ubuntu/Debian.

### 1. Install System Packages

#### Docker and Docker Compose

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) \
  signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker $USER
```

Log out and back in for the group change.
Verify: `docker run --rm hello-world`.

#### JDK 25

```bash
sudo apt install -y wget
wget https://download.java.net/java/GA/jdk25/ea/binaries/openjdk-25_linux-x64_bin.tar.gz
sudo mkdir -p /opt/java
sudo tar -xzf openjdk-25_linux-x64_bin.tar.gz -C /opt/java
```

Add to your shell profile:

```bash
export JAVA_HOME=/opt/java/jdk-25
export PATH=$JAVA_HOME/bin:$PATH
```

If a packaged JDK 25 is available for your distribution, prefer that.

#### Node.js (LTS)

Only needed if running the frontend on the host instead of Docker.

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

#### nginx

```bash
sudo apt install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
```

#### certbot

```bash
sudo apt install -y certbot
```

### 2. Clone and Configure

```bash
git clone <repository-url> nevet
cd nevet
cp .env.default .env
```

Edit `.env` with production values.
These must be changed:

| Variable | What to set |
|----------|-------------|
| `JWT_SECRET` | Strong random value: `openssl rand -base64 32` |
| `BASE_URL` | Public URL, e.g., `https://bytecode.news` |
| `DB_PASSWORD` | A real password |
| `CORS_ORIGINS` | All frontend origins, e.g., `https://bytecode.news,https://nextjs.bytecode.news` |

### 3. Start PostgreSQL

```bash
docker compose up -d
```

Verify: `psql -h localhost -U nevet -d nevet` connects.

### 4. Build and Start the Backend

```bash
./mvnw clean install -DskipTests
java -jar app/target/app-1.0.jar
```

On first boot, Flyway runs migrations and `SuperAdminBootstrap` creates the superadmin.
Copy the generated password from the log output.

Verify: `curl http://localhost:8080/v3/api-docs` returns JSON.

For production, run as a systemd service (see [Process Management](#process-management)).

### 5. Start a Frontend

#### Reference UI (vanilla JS, recommended for testing)

```bash
cd reference-ui && npm install && npm run dev
```

Opens on `http://localhost:3003`, proxies API requests to `localhost:8080`.

For production, build static files and serve with nginx:

```bash
cd reference-ui && npm run build
```

The `dist/` directory contains the built files.
See the nginx config at `deploy/nginx/bytecode.news.conf` for the `reference.bytecode.news` server block.

#### Next.js Frontend

**Docker (recommended)**:

```bash
docker compose --profile frontend build
docker compose --profile frontend up -d
```

Set `API_URL=http://host.docker.internal:8080` in `.env` if the backend runs on the host.

**Host**:

```bash
cd frontend && npm install && npm run build && npm start
```

Verify: `curl http://localhost:3000` returns HTML.

### Reference UI Production Deployment

The reference UI is a static site - no server process, no systemd unit, no restart needed for updates.

**Build**:

```bash
cd reference-ui && npm install && npm run build
```

This creates `reference-ui/dist/` with the bundled static files.

**Deploy**:

Copy the built files to the expected location (or build in place if the repo is on the server):

```bash
sudo mkdir -p /opt/nevet/reference-ui
sudo cp -r reference-ui/dist /opt/nevet/reference-ui/
```

The nginx config in `deploy/nginx/bytecode.news.conf` already has a `reference.bytecode.news` server block that serves from `/opt/nevet/reference-ui/dist` with `try_files` for SPA routing.
No proxy is needed for the UI itself - only the `/api/` location proxies to the backend.

**Backend configuration**:

Add the reference UI's origin to `CORS_ORIGINS` in `.env`:

```
CORS_ORIGINS=https://bytecode.news,https://reference.bytecode.news
```

If this is the primary frontend for OIDC login, set `FRONTEND_URL`:

```
FRONTEND_URL=https://reference.bytecode.news
```

Restart the backend after changing environment variables.

**TLS**:

`reference.bytecode.news` is already included in the certbot domain list (see [TLS Certificates](#7-tls-certificates)).
No additional certificate step is needed if you followed the standard deployment.

**Updating**:

```bash
cd /opt/nevet && git pull && cd reference-ui && npm run build
```

Since the files are static, nginx serves the new build immediately - no process restart required.

### 6. Configure nginx

```bash
sudo rm /etc/nginx/sites-enabled/default
sudo cp deploy/nginx/bytecode.news.conf /etc/nginx/sites-available/bytecode.news
sudo ln -s /etc/nginx/sites-available/bytecode.news /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### 7. TLS Certificates

#### Option 1: HTTP-01 Challenges (simpler)

```bash
sudo mkdir -p /var/www/certbot
sudo certbot certonly --webroot -w /var/www/certbot \
  -d bytecode.news -d www.bytecode.news \
  -d nextjs.bytecode.news -d primate.bytecode.news \
  -d reference.bytecode.news
```

After certificates are issued:

1. Uncomment the HTTPS server blocks in the nginx config
2. In each HTTP block, replace proxy locations with `return 301 https://$host$request_uri;`
3. `sudo nginx -t && sudo systemctl reload nginx`

Verify auto-renewal: `sudo certbot renew --dry-run`.

#### Option 2: DNS-01 with Cloudflare (wildcard)

```bash
sudo apt install -y python3-certbot-dns-cloudflare
```

Create `/etc/letsencrypt/cloudflare.ini`:

```ini
dns_cloudflare_api_token = YOUR_CLOUDFLARE_API_TOKEN
```

```bash
sudo chmod 600 /etc/letsencrypt/cloudflare.ini
sudo certbot certonly --dns-cloudflare \
  --dns-cloudflare-credentials /etc/letsencrypt/cloudflare.ini \
  -d bytecode.news -d '*.bytecode.news'
```

Same nginx activation steps as Option 1.

### 8. Verify the Full Stack

```bash
curl -s -o /dev/null -w "%{http_code}" https://bytecode.news           # 200
curl -s -o /dev/null -w "%{http_code}" https://bytecode.news/api/v3/api-docs   # 200
```

---

## OIDC Setup

Nevet supports "Sign in with Google" and "Sign in with GitHub" via OIDC / OAuth2.
Both are optional and independent - configure one, both, or neither.
When OIDC is not enabled, the app works with OTP-only authentication.

OIDC is gated behind the `oidc` Spring profile.
Without the profile active, the OAuth2 client auto-configuration is not loaded and no OIDC endpoints are registered.
The app starts and runs normally with OTP as the sole authentication method.

### Activating the OIDC Profile

There are several ways to activate the profile, depending on how you run the application.

**From the command line** (local development or manual launch):

```bash
java -jar app/target/app-1.0.jar --spring.profiles.active=oidc
```

Multiple profiles can be comma-separated:

```bash
java -jar app/target/app-1.0.jar --spring.profiles.active=oidc,someotherprofile
```

**Via environment variable** (Docker, systemd, CI):

```bash
export SPRING_PROFILES_ACTIVE=oidc
java -jar app/target/app-1.0.jar
```

**In the `.env` file**:

```
SPRING_PROFILES_ACTIVE=oidc
```

**In a systemd unit** (production):

```ini
[Service]
Environment=SPRING_PROFILES_ACTIVE=oidc
ExecStart=/opt/java/jdk-25/bin/java -jar app/target/app-1.0.jar
```

### Provider Setup

When the `oidc` profile is active, the following environment variables are required.
You can configure one provider or both.

#### Google

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project (no billing required)
2. Navigate to **APIs & Services > OAuth consent screen**, choose **External**, add scopes: `openid`, `email`, `profile`
3. Navigate to **APIs & Services > Credentials**, click **Create Credentials > OAuth client ID**
4. Application type: **Web application**
5. Authorized redirect URI: `https://rest.your-domain.com/login/oauth2/code/google`
   - For local dev: `http://localhost:8080/login/oauth2/code/google`
6. Copy the **Client ID** and **Client Secret**

Add to your `.env`:

```
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

#### GitHub

1. Go to [GitHub Developer Settings](https://github.com/settings/developers), click **OAuth Apps > New OAuth App**
2. Homepage URL: `https://your-domain.com`
3. Authorization callback URL: `https://rest.your-domain.com/login/oauth2/code/github`
   - For local dev: `http://localhost:8080/login/oauth2/code/github`
4. Copy the **Client ID** and generate a **Client Secret**

Add to your `.env`:

```
GITHUB_CLIENT_ID=your-client-id
GITHUB_CLIENT_SECRET=your-client-secret
```

### Split-Domain Deployment

When the API and frontend live on different domains:

- `streampack.baseUrl` - the API's public URL (what Google/GitHub call back to)
- `streampack.frontendUrl` - the frontend's public URL (where the user is redirected after auth)

```yaml
streampack:
  base-url: https://rest.bytecode.news
  frontend-url: https://bytecode.news
```

If `frontendUrl` is not set, it defaults to `baseUrl`.

### How OIDC Works

1. Frontend redirects to `/oauth2/authorization/google` (or `github`)
2. Spring Security redirects to the provider's login page
3. Provider redirects back to `/login/oauth2/code/{provider}`
4. The app exchanges the code for user info, resolves identity by email, issues JWT
5. Browser is redirected to `{frontendUrl}/auth/callback#token=<jwt>`

Email is the identity anchor: signing in with Google and later GitHub using the same email gives the same account.

---

## IRC Adapter Setup

The IRC service connects Nevet to IRC networks as a channel bot.

### Activation

```yaml
streampack:
  irc:
    enabled: ${IRC_ENABLED:false}
    signal-character: ${IRC_SIGNAL:!}
```

Set `IRC_ENABLED=true` in `.env` to activate.

When `enabled` is `false`, admin commands still persist configuration to the database but no connections are made.
When `enabled` is `true`, the connection manager connects to all networks with `autoconnect = true` and joins channels with `autojoin = true`.

### Setup Workflow

From the console:

```
irc connect libera irc.libera.chat nevet
irc join libera #java
irc autoconnect libera true
irc autojoin libera #java true
```

With SASL authentication:

```
irc connect libera irc.libera.chat nevet myaccount mypassword
```

Port defaults to 6697, TLS defaults to true.

### Configuration

All network and channel configuration lives in the database (`irc_networks`, `irc_channels` tables).
The only application properties are the on/off switch and the default signal character.

See [User Guide](user-guide.md#irc-super-admin) for the full set of admin commands.

### Auto-Deop

By default, the bot automatically removes channel operator status (`+o`) from itself when opped.
A bot should not hold ops - it is unnecessary privilege and a potential abuse vector.

The behavior is per-channel and controlled via `irc allow-ops`:

```
irc allow-ops libera #java true     allow the bot to stay opped
irc allow-ops libera #java false    restore default auto-deop behavior
```

The default is `false` (auto-deop enabled).
The setting is stored via `ProvenanceStateService` and persists across restarts.

---

## Slack Adapter Setup

The Slack service connects Nevet to Slack workspaces via Socket Mode (no public HTTP endpoint required).

### Creating a Slack App

1. Go to [api.slack.com/apps](https://api.slack.com/apps), click **Create New App > From scratch**
2. **Socket Mode**: Enable it, create an App-Level Token with `connections:write` scope. Copy the `xapp-` token.
3. **Event Subscriptions**: Enable, subscribe to bot events: `message.channels`, `message.groups`, `message.im`
4. **OAuth & Permissions**: Add bot token scopes: `chat:write`, `channels:history`, `groups:history`, `im:history`, `channels:read`, `channels:join`, `im:read`, `users:read`
5. **Install to Workspace**: Copy the `xoxb-` Bot User OAuth Token

| Token | Prefix | Purpose |
|-------|--------|---------|
| Bot Token | `xoxb-` | API calls (send messages, read info) |
| App Token | `xapp-` | Socket Mode connection (receive events) |

### Activation

```yaml
streampack:
  slack:
    enabled: ${SLACK_ENABLED:false}
    signal-character: ${SLACK_SIGNAL:!}
```

Set `SLACK_ENABLED=true` in `.env` to activate.

### Setup Workflow

```
slack connect jvm-news xoxb-1234567890-abcdef xapp-1-ABCDEF123456
slack join jvm-news #general
slack autoconnect jvm-news true
slack autojoin jvm-news #general true
```

Invite the bot to channels: `/invite @Nevet` in Slack, or use `slack join`.

See [User Guide](user-guide.md#slack-super-admin) for the full set of admin commands.

---

## Mail Configuration

### Transactional Email (OTP, password reset)

Configure SMTP for account verification and OTP delivery:

| Variable | Default | Purpose |
|----------|---------|---------|
| `MAIL_HOST` | `localhost` | SMTP server host |
| `MAIL_PORT` | `25` | SMTP server port |

The default (`localhost:25`) assumes a local MTA.

### Mail Egress Service

The mail egress subscriber delivers operation output to users via email.

| Variable | Default | Purpose |
|----------|---------|---------|
| `MAIL_ENABLED` | `false` | Activates the mail egress subscriber |

Link a user's email for mail delivery:

```
link user testuser mailto "" testuser@example.com
```

### Email Deliverability

Without proper DNS records, sent mail will land in spam folders.

**SPF** - add a TXT record: `v=spf1 a mx -all`

**DKIM** - configure your MTA to sign outbound mail, publish the public key as a DNS TXT record.

**DMARC** - add a TXT record at `_dmarc.yourdomain.com`: `v=DMARC1; p=quarantine; rua=mailto:dmarc@yourdomain.com`

---

## AI Configuration

AI-powered features (ask, poems, sentiment analysis) require an API key.

| Variable | Default | Purpose |
|----------|---------|---------|
| `AI_ENABLED` | `false` | Master switch for AI services |
| `ANTHROPIC_API_KEY` | (empty) | Anthropic API key |

Both must be set for AI operations to work.
Get a key at [console.anthropic.com](https://console.anthropic.com/).

To swap providers (OpenAI, Ollama, etc.):
1. Replace the `spring-ai-anthropic` dependency in `app/pom.xml`
2. Replace `AnthropicConfiguration` with the new provider's configuration
3. Update `.env` with the new provider's credentials

---

## Deployment Scenarios

### Scenario 0: Development Hybrid (current)

PostgreSQL and frontend in Docker; backend, nginx, MTA, certbot on the host.

### Scenario A: Docker for App Services

Backend and frontends in Docker; nginx, MTA, certbot on the host.

```bash
./mvnw clean install -DskipTests
docker compose --profile frontend --profile backend build
docker compose --profile frontend --profile backend up -d
```

### Scenario B: Everything in Docker

Not yet implemented - requires nginx container, certbot sidecar, MTA container.

### Scenario C: Bare Metal

No Docker.
Install PostgreSQL 17 locally, create the database manually, run backend and frontend directly.

---

## Process Management

### Backend systemd unit

Create `/etc/systemd/system/nevet-backend.service`:

```ini
[Unit]
Description=Nevet Backend
After=network.target postgresql.service

[Service]
Type=simple
User=nevet
WorkingDirectory=/opt/nevet
# Add SPRING_PROFILES_ACTIVE=oidc to enable OIDC authentication (see OIDC Setup)
ExecStart=/opt/java/jdk-25/bin/java -jar app/target/app-1.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable nevet-backend
sudo systemctl start nevet-backend
```

### Frontend systemd unit

Create `/etc/systemd/system/nevet-frontend.service`:

```ini
[Unit]
Description=Nevet Frontend
After=network.target

[Service]
Type=simple
User=nevet
WorkingDirectory=/opt/nevet/frontend
ExecStart=/usr/bin/node .next/standalone/server.js
Environment=HOSTNAME=0.0.0.0
Environment=NODE_ENV=production
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## Adding a New Frontend

1. Build the frontend, create a Dockerfile if using Docker
2. Add a `docker-compose.yml` service with a unique port and profile
3. Add an nginx server block (copy an existing one, change `server_name` and port)
4. `sudo nginx -t && sudo systemctl reload nginx`
5. Issue a TLS certificate for the new subdomain
6. Add the new origin to `CORS_ORIGINS` in `.env` and restart the backend

---

## PostgreSQL Backup

### Manual backup

```bash
pg_dump -h localhost -U nevet -d nevet -F custom -f nevet-$(date +%Y%m%d).dump
```

### Restore

```bash
pg_restore -h localhost -U nevet -d nevet --clean nevet-20260216.dump
```

### Automated daily backup

```bash
0 3 * * * pg_dump -h localhost -U nevet -d nevet -F custom -f /backups/nevet-$(date +\%Y\%m\%d).dump
```

Rotate: `find /backups -name "nevet-*.dump" -mtime +30 -delete`.

---

## Environment Variables Reference

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/nevet` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `nevet` | Database user |
| `DB_PASSWORD` | `nevet` | Database password |
| `JWT_SECRET` | `change-me-in-production` | HMAC signing key for JWTs |
| `BASE_URL` | `http://localhost:8080` | Public base URL for links in emails |
| `FRONTEND_URL` | (same as BASE_URL) | Frontend URL for OIDC redirect |
| `MAIL_HOST` | `localhost` | SMTP server host |
| `MAIL_PORT` | `25` | SMTP server port |
| `MAIL_ENABLED` | `false` | Enable mail egress subscriber |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:3003` | Comma-separated allowed origins |
| `CONSOLE_ENABLED` | `false` | Enable stdin console adapter |
| `IRC_ENABLED` | `false` | Enable IRC connections |
| `IRC_SIGNAL` | `!` | Global IRC signal character |
| `SLACK_ENABLED` | `false` | Enable Slack connections |
| `SLACK_SIGNAL` | `!` | Global Slack signal character |
| `DISCORD_ENABLED` | `false` | Enable Discord bot |
| `DISCORD_APPLICATION_ID` | | Discord application ID |
| `DISCORD_PUBLIC_KEY` | | Discord public key |
| `DISCORD_BOT_TOKEN` | | Discord bot token |
| `AI_ENABLED` | `false` | Enable AI services |
| `ANTHROPIC_API_KEY` | (empty) | Anthropic API key |
| `OPENWEATHERMAP_API_KEY` | | OpenWeatherMap API key |
| `KARMA_IMMUNE_SUBJECTS` | `nevet` | Comma-separated karma-immune names |
| `GOOGLE_CLIENT_ID` | | Google OAuth2 client ID (requires `oidc` profile) |
| `GOOGLE_CLIENT_SECRET` | | Google OAuth2 client secret (requires `oidc` profile) |
| `GITHUB_CLIENT_ID` | | GitHub OAuth2 client ID (requires `oidc` profile) |
| `GITHUB_CLIENT_SECRET` | | GitHub OAuth2 client secret (requires `oidc` profile) |
| `RSS_POLL_INTERVAL` | `PT60M` | RSS polling interval (ISO-8601) |
| `GITHUB_POLL_INTERVAL` | `PT60M` | GitHub polling interval (ISO-8601) |
| `API_URL` | `http://localhost:8080` | Backend URL for frontend rewrites |
