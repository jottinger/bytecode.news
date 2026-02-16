# Deployment Guide

This guide covers deploying Nevet across several scenarios, from development hybrid to fully containerized.
It assumes familiarity with the [Getting Started](getting-started.md) guide for local development.

## Environment Variables

All backend configuration flows through environment variables with sensible defaults in `application.yml`.
Copy `.env.default` to `.env` and fill in production values.
The `.env` file is in `.gitignore` and will not be committed.

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/nevet` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `nevet` | Database user |
| `DB_PASSWORD` | `nevet` | Database password |
| `JWT_SECRET` | `change-me-in-production` | HMAC signing key for JWTs |
| `BASE_URL` | `http://localhost:8080` | Public base URL for links in emails, etc. |
| `MAIL_HOST` | `localhost` | SMTP server host |
| `MAIL_PORT` | `25` | SMTP server port |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:3003` | Comma-separated allowed origins |
| `CONSOLE_ENABLED` | `false` | Enable stdin console adapter |
| `IRC_ENABLED` | `false` | Enable IRC connections |
| `DISCORD_ENABLED` | `false` | Enable Discord bot |
| `DISCORD_APPLICATION_ID` | | Discord application ID |
| `DISCORD_PUBLIC_KEY` | | Discord public key |
| `DISCORD_BOT_TOKEN` | | Discord bot token |
| `SLACK_ENABLED` | `false` | Enable Slack bot |
| `SLACK_SIGNAL` | `!` | Slack signal character |
| `OPENWEATHERMAP_API_KEY` | | OpenWeatherMap API key |
| `KARMA_IMMUNE_SUBJECTS` | `nevet` | Comma-separated karma-immune names |
| `API_URL` | `http://localhost:8080` | Backend URL for Next.js server-side rewrites (frontend only) |

**Production essentials** - these must be changed from their defaults:

- `JWT_SECRET` - set to a strong random value (e.g., `openssl rand -base64 32`)
- `BASE_URL` - set to the public URL (e.g., `https://bytecode.news`)
- `DB_PASSWORD` - use a real password, not the development default
- `CORS_ORIGINS` - include all frontend domains (e.g., `https://bytecode.news,https://nextjs.bytecode.news`)

## TLS Certificate Management

The registrar is Namecheap.
Two approaches for Let's Encrypt certificates via certbot.

### Option 1: HTTP-01 Challenges (simpler)

Per-subdomain certificates using certbot's nginx plugin.
No DNS provider integration required.

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx \
  -d bytecode.news \
  -d www.bytecode.news \
  -d nextjs.bytecode.news \
  -d primate.bytecode.news
```

Certbot auto-renews via systemd timer.
Verify renewal works: `sudo certbot renew --dry-run`.
Downside: must re-run certbot to add each new subdomain.

### Option 2: DNS-01 with Cloudflare (wildcard)

Move nameservers from Namecheap to Cloudflare (free tier).
Namecheap remains the registrar.

```bash
sudo apt install python3-certbot-dns-cloudflare
sudo certbot certonly --dns-cloudflare \
  --dns-cloudflare-credentials /etc/letsencrypt/cloudflare.ini \
  -d bytecode.news \
  -d '*.bytecode.news'
```

One wildcard cert covers all current and future subdomains.
Also provides CDN and DDoS protection.
Downside: DNS management moves away from Namecheap.

## Scenario 0: Development Hybrid

PostgreSQL and frontends in Docker, backend and infrastructure on the host.
This is the current development and staging setup.

**Components**:
- PostgreSQL: Docker container (`docker-compose.yml`)
- Spring Boot backend: `java -jar app/target/app-1.0.jar` on host
- Next.js frontend: Docker container or `npm start` on host
- nginx: host system
- MTA: host system (e.g., Postfix)
- certbot: host system

### Prerequisites

- JDK 25+
- Node.js (LTS) and npm
- Docker and Docker Compose
- nginx installed and running
- certbot installed
- Local MTA running (Postfix, etc.)
- Domain DNS pointing to the host (including frontend subdomains)

### Database

```bash
docker compose up -d
```

Verify: `psql -h localhost -U nevet -d nevet` connects.

### Backend

```bash
cp .env.default .env
# Edit .env with production values
./mvnw clean install -DskipTests
java -jar app/target/app-1.0.jar
```

Verify: `curl http://localhost:8080/v3/api-docs` returns JSON.

### Frontend (Docker)

Set `API_URL=http://host.docker.internal:8080` in `.env` so the frontend container can reach the host-running backend.

```bash
docker compose --profile frontend up -d
```

Verify: `curl http://localhost:3000` returns HTML.

### Frontend (host)

```bash
cd frontend && npm install && npm run build && npm start
```

Or for development: `npm run dev`.

### nginx

Copy `deploy/nginx/bytecode.news.conf` to `/etc/nginx/sites-available/` and symlink to `sites-enabled/`.
Edit domain names and ports as needed.

```bash
sudo cp deploy/nginx/bytecode.news.conf /etc/nginx/sites-available/bytecode.news
sudo ln -s /etc/nginx/sites-available/bytecode.news /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### TLS

Run certbot for all domains (see TLS section above).

### Checklist

- [ ] `.env` created with production values (`JWT_SECRET`, `BASE_URL`, `DB_PASSWORD`, `CORS_ORIGINS`)
- [ ] PostgreSQL running (`docker compose up -d`)
- [ ] Backend built and running
- [ ] Frontend running (Docker or host)
- [ ] nginx configured and reloaded
- [ ] TLS certificates issued
- [ ] Auto-renewal verified (`sudo certbot renew --dry-run`)
- [ ] MTA verified (see Email section below)

## Scenario A: Docker for App Services

Backend and all frontends run in Docker.
nginx, MTA, and certbot remain on the host.

### Build

The backend Docker image packages a pre-built JAR - build locally first:

```bash
./mvnw clean install -DskipTests
docker compose --profile frontend --profile backend build
```

### Run

```bash
docker compose --profile frontend --profile backend up -d
```

The backend connects to PostgreSQL via Docker's internal network (`jdbc:postgresql://db:5432/nevet`).
The frontend connects to the backend via Docker's internal network when `API_URL` defaults to `http://backend:8080`.

### Rebuild after code changes

```bash
./mvnw clean install -DskipTests
docker compose --profile backend build
docker compose --profile frontend --profile backend up -d
```

nginx, certbot, and MTA: same as Scenario 0.

### Checklist

All items from Scenario 0, plus:

- [ ] `./mvnw clean install -DskipTests` completed
- [ ] `docker compose --profile frontend --profile backend build` succeeded
- [ ] Backend responds: `curl http://localhost:8080/v3/api-docs`
- [ ] Frontend responds: `curl http://localhost:3000`

## Scenario B: Everything in Docker

Fully self-contained.
No host dependencies beyond Docker.

This scenario requires additional work not yet implemented:

- nginx container with multi-frontend config and TLS volume mounts
- certbot sidecar or cert volume mount
- MTA container (lightweight relay, e.g., namshi/smtp or Postfix container)
- `docker-compose.prod.yml` for the full stack

## Scenario C: Bare Metal

Everything runs directly on the host, no Docker.

### Differences from Scenario 0

- Install PostgreSQL 17 locally instead of using Docker
- Create the database: `createdb -U postgres nevet && createuser -U postgres nevet`
- Consider systemd units for the backend and frontend processes

Everything else (nginx, certbot, MTA, `.env` configuration) is the same as Scenario 0.

## Adding a New Frontend

Full checklist for deploying a new frontend implementation alongside the existing ones.

### Code and build

- [ ] Frontend has a working build that produces a servable application
- [ ] Frontend is configured to call the backend API (via proxy rewrite or direct URL)

### Docker (if applicable)

- [ ] Create a Dockerfile for the frontend
- [ ] Add a service entry to `docker-compose.yml` with a unique host port and a profile
- [ ] Test: `docker compose --profile <name> build` succeeds
- [ ] Test: `docker compose --profile <name> up` starts and responds on its port

### DNS

- [ ] Create DNS A/AAAA record for the new subdomain pointing to the server
- [ ] Wait for propagation: `dig <subdomain>.bytecode.news`

### TLS

- [ ] If using HTTP-01 certs: re-run certbot to add the new subdomain
- [ ] If using wildcard cert: no action needed

### nginx

- [ ] Copy an existing frontend server block in `bytecode.news.conf`
- [ ] Change `server_name` to the new subdomain
- [ ] Change `proxy_pass` port in the `/` location to the new frontend's port
- [ ] Add the new subdomain to the HTTP -> HTTPS redirect block
- [ ] Test and reload: `sudo nginx -t && sudo systemctl reload nginx`

See `deploy/nginx/bytecode.news.conf` for the template and detailed instructions.

### CORS

- [ ] Add the new frontend's origin (e.g., `https://primate.bytecode.news`) to `CORS_ORIGINS` in `.env`
- [ ] Restart the backend for the change to take effect

### Verification

- [ ] `curl https://<subdomain>.bytecode.news` returns HTML from the new frontend
- [ ] `curl https://<subdomain>.bytecode.news/api/posts` returns JSON from the backend
- [ ] Browser: navigate to the new subdomain, verify login/auth works
- [ ] Browser: verify no CORS errors in the console

## Email and MTA Setup

The backend sends email for account verification and password resets via `MAIL_HOST` and `MAIL_PORT`.
The default (`localhost:25`) assumes a local MTA is running.

### Verify MTA

```bash
echo "test" | mail -s "Nevet test" you@example.com
```

### DNS records for deliverability

Without these, sent mail will land in spam folders.

**SPF** - add a TXT record to the domain:

```
v=spf1 a mx -all
```

**DKIM** - configure your MTA to sign outbound mail, then add the public key as a TXT record.
The exact steps depend on the MTA (Postfix: use opendkim).

**DMARC** - add a TXT record at `_dmarc.bytecode.news`:

```
v=DMARC1; p=quarantine; rua=mailto:dmarc@bytecode.news
```

## PostgreSQL Backup

The database is the only stateful component.
The Docker volume `pgdata` persists data across restarts but is not a backup.

### pg_dump

```bash
pg_dump -h localhost -U nevet -d nevet -F custom -f nevet-$(date +%Y%m%d).dump
```

### Restore

```bash
pg_restore -h localhost -U nevet -d nevet --clean nevet-20260216.dump
```

### Automated backup

A daily cron job is the simplest approach:

```bash
0 3 * * * pg_dump -h localhost -U nevet -d nevet -F custom -f /backups/nevet-$(date +\%Y\%m\%d).dump
```

Rotate old backups with `find /backups -name "nevet-*.dump" -mtime +30 -delete`.
