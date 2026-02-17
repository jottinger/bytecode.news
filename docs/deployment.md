# Deployment Guide

Ground-up deployment of Nevet on a fresh server.

**Assumptions**: DNS A/AAAA records already point to this server (including any frontend subdomains), and a working MTA is available (e.g., Postfix).
Everything else is installed and configured by this guide.

All commands assume Ubuntu/Debian.
Adapt package manager commands for other distributions.

## 1. Install System Packages

### Docker and Docker Compose

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

Log out and back in for the group change to take effect.
Verify: `docker run --rm hello-world`.

### JDK 25

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

Verify: `java -version`.

If a packaged JDK 25 is available for your distribution, prefer that over the manual install.

### Node.js (LTS)

Only needed if running the frontend on the host instead of Docker.

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
```

Verify: `node --version && npm --version`.

### nginx

```bash
sudo apt install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
```

Verify: `curl http://localhost` returns the nginx welcome page.

### certbot

```bash
sudo apt install -y certbot python3-certbot-nginx
```

Verify: `certbot --version`.

## 2. Clone and Configure

```bash
git clone <repository-url> nevet
cd nevet
cp .env.default .env
```

Edit `.env` with production values.
These must be changed from their defaults:

| Variable | What to set |
|----------|-------------|
| `JWT_SECRET` | Strong random value: `openssl rand -base64 32` |
| `BASE_URL` | Public URL, e.g., `https://bytecode.news` |
| `DB_PASSWORD` | A real password, not the development default |
| `CORS_ORIGINS` | All frontend origins, e.g., `https://bytecode.news,https://nextjs.bytecode.news` |

See the full [environment variables reference](#environment-variables-reference) at the end of this guide.

## 3. Start PostgreSQL

```bash
docker compose up -d
```

Verify: `psql -h localhost -U nevet -d nevet` connects (password is whatever you set in `.env`).

Data is persisted in a Docker volume (`pgdata`), so it survives container restarts.

## 4. Build and Start the Backend

```bash
./mvnw clean install -DskipTests
java -jar app/target/app-1.0.jar
```

On first boot, Flyway runs all migrations and `SuperAdminBootstrap` creates a default superadmin account.
Copy the generated password from the log output - you will need it for HTTP login.

Verify: `curl http://localhost:8080/v3/api-docs` returns JSON.

For production, consider running the backend as a systemd service (see [Process Management](#process-management) below).

## 5. Start the Frontend

### Option A: Docker (recommended for production)

Set `API_URL` in `.env` to tell the frontend how to reach the backend.
If the backend runs on the host, use `host.docker.internal`:

```bash
# In .env:
API_URL=http://host.docker.internal:8080
```

Build and start:

```bash
docker compose --profile frontend build
docker compose --profile frontend up -d
```

Verify: `curl http://localhost:3000` returns HTML.

### Option B: Host

```bash
cd frontend
npm install
npm run build
npm start
```

Verify: `curl http://localhost:3000` returns HTML.

## 6. Configure nginx

Remove the default site and deploy the Nevet config:

```bash
sudo rm /etc/nginx/sites-enabled/default
sudo cp deploy/nginx/bytecode.news.conf /etc/nginx/sites-available/bytecode.news
sudo ln -s /etc/nginx/sites-available/bytecode.news /etc/nginx/sites-enabled/
```

Edit the config if your domains or ports differ from the defaults.
The config file has comments explaining each server block.

Test and reload:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Verify: `curl -H "Host: bytecode.news" http://localhost` returns the frontend (proxied through nginx).

At this point, the site is accessible over HTTP.
The next step adds TLS.

## 7. TLS Certificates

### Option 1: HTTP-01 Challenges (simpler)

Per-subdomain certificates.
certbot's nginx plugin reads the server blocks, issues certificates, and modifies the config to add TLS directives and an HTTP -> HTTPS redirect automatically.

The nginx config includes `location /.well-known/acme-challenge/` blocks that serve challenge files from a local directory instead of proxying them to the frontend.
Create this directory before running certbot:

```bash
sudo mkdir -p /var/www/certbot
sudo certbot --nginx \
  -d bytecode.news \
  -d www.bytecode.news \
  -d nextjs.bytecode.news
```

Follow the prompts.
certbot adds `listen 443 ssl`, `ssl_certificate` directives, and a redirect block to the nginx config.

Verify auto-renewal:

```bash
sudo certbot renew --dry-run
```

To add a new subdomain later, re-run certbot with the additional `-d` flag.

### Option 2: DNS-01 with Cloudflare (wildcard)

One wildcard cert covers all current and future subdomains.
Requires moving nameservers from Namecheap to Cloudflare (free tier).
Namecheap remains the registrar.

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
  -d bytecode.news \
  -d '*.bytecode.news'
```

With this approach, certbot does not modify the nginx config automatically.
You must manually add TLS directives to each server block:

```nginx
listen 443 ssl;
ssl_certificate /etc/letsencrypt/live/bytecode.news/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/bytecode.news/privkey.pem;
```

And add an HTTP -> HTTPS redirect block:

```nginx
server {
    listen 80;
    server_name bytecode.news www.bytecode.news nextjs.bytecode.news;
    return 301 https://$host$request_uri;
}
```

## 8. Verify the Full Stack

```bash
curl -s -o /dev/null -w "%{http_code}" https://bytecode.news
# expect 200

curl -s -o /dev/null -w "%{http_code}" https://bytecode.news/api/v3/api-docs
# expect 200

curl -s -o /dev/null -w "%{http_code}" https://nextjs.bytecode.news
# expect 200
```

Test login from a browser to confirm CORS, JWT, and the full request chain work end-to-end.

## Deployment Scenarios

The steps above describe **Scenario 0** (development hybrid) - the current real deployment.
PostgreSQL and the frontend run in Docker; the backend, nginx, MTA, and certbot run on the host.

### Scenario A: Docker for App Services

Backend and all frontends in Docker.
nginx, MTA, and certbot remain on the host.

Differences from the steps above:

- Build the backend image: `./mvnw clean install -DskipTests && docker compose --profile backend build`
- Start everything: `docker compose --profile frontend --profile backend up -d`
- The backend connects to PostgreSQL via Docker network (`jdbc:postgresql://db:5432/nevet` - already configured in `docker-compose.yml`)
- After code changes: rebuild (`./mvnw clean install -DskipTests && docker compose --profile backend build`) and restart

### Scenario B: Everything in Docker

Fully containerized including nginx and certbot.
Not yet implemented - requires an nginx container, certbot sidecar, and MTA container.

### Scenario C: Bare Metal

No Docker at all.
Install PostgreSQL 17 locally, create the database manually (`createdb -U postgres nevet`), and run the backend and frontend directly.
Everything else (nginx, certbot, `.env`) is the same.

## Process Management

For production, the backend and frontend should run as systemd services so they start on boot and restart on failure.

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

The `.env` file must be in the `WorkingDirectory` for Spring to pick it up.

### Frontend (host) systemd unit

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

If running the frontend in Docker, systemd management is not needed - Docker's `restart: unless-stopped` handles it.

## Adding a New Frontend

1. Build the frontend and create a Dockerfile (if using Docker)
2. Add a `docker-compose.yml` service with a unique port and profile (if using Docker)
3. Add a server block to the nginx config (copy an existing one, change `server_name` and `proxy_pass` port)
4. `sudo nginx -t && sudo systemctl reload nginx`
5. `sudo certbot --nginx -d <subdomain>.bytecode.news`
6. Add the new origin to `CORS_ORIGINS` in `.env` and restart the backend
7. Verify: `curl https://<subdomain>.bytecode.news` returns HTML
8. Verify: browser login works, no CORS errors in console

See `deploy/nginx/bytecode.news.conf` for the template with detailed comments.

## Email Deliverability

The backend sends email for account verification and password resets via `MAIL_HOST` and `MAIL_PORT`.
The default (`localhost:25`) assumes a local MTA is running.

Verify: `echo "test" | mail -s "Nevet test" you@example.com`

Without proper DNS records, sent mail will land in spam folders.

**SPF** - add a TXT record to the domain:

```
v=spf1 a mx -all
```

**DKIM** - configure your MTA to sign outbound mail, then publish the public key as a DNS TXT record.
The exact steps depend on the MTA (Postfix: use opendkim).

**DMARC** - add a TXT record at `_dmarc.bytecode.news`:

```
v=DMARC1; p=quarantine; rua=mailto:dmarc@bytecode.news
```

## PostgreSQL Backup

The database is the only stateful component.
The Docker volume `pgdata` persists data across restarts but is not a backup.

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

Rotate old backups: `find /backups -name "nevet-*.dump" -mtime +30 -delete`.

## Environment Variables Reference

All backend configuration flows through environment variables with defaults in `application.yml`.

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/nevet` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `nevet` | Database user |
| `DB_PASSWORD` | `nevet` | Database password |
| `JWT_SECRET` | `change-me-in-production` | HMAC signing key for JWTs |
| `BASE_URL` | `http://localhost:8080` | Public base URL for links in emails |
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
| `API_URL` | `http://localhost:8080` | Backend URL for Next.js rewrites (frontend only) |
