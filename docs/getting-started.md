# Getting Started

This guide walks through the complete onboarding flow from a fresh checkout to a working Nevet instance with an IRC-connected superadmin.

## Prerequisites

- JDK 25+
- Docker (for PostgreSQL)
- Maven wrapper is included in the repository (`./mvnw`)

## 1. Set Up the Environment File

Create a `.env` file in the project root.
The application reads this automatically on startup via Spring's config import.

```
DB_URL=jdbc:postgresql://localhost:5432/nevet
DB_USERNAME=nevet
DB_PASSWORD=nevet
CONSOLE_ENABLED=true
IRC_ENABLED=true
```

The `.env` file is in `.gitignore` and will not be committed.

`CONSOLE_ENABLED=true` activates the interactive console adapter, which lets you issue admin commands from stdin.
`IRC_ENABLED=true` activates the IRC connection manager so the bot will actually connect to IRC networks.

Additional environment variables you may want to set:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/nevet` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `nevet` | Database username |
| `DB_PASSWORD` | `nevet` | Database password |
| `CONSOLE_ENABLED` | `false` | Enable stdin console adapter |
| `IRC_ENABLED` | `false` | Enable IRC connections |
| `JWT_SECRET` | `change-me-in-production` | HMAC signing key for JWTs |
| `BASE_URL` | `http://localhost:8080` | Public base URL for the application |
| `MAIL_HOST` | `localhost` | SMTP server host |
| `MAIL_PORT` | `25` | SMTP server port |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:3003,https://bytecode.news` | Comma-separated CORS allowed origins |
| `API_URL` | `http://localhost:8080` | Backend API URL for Next.js server-side rewrites (frontend only) |
| `IRC_SIGNAL` | `!` | Global IRC signal character |
| `AI_ENABLED` | `false` | Enable AI services (poetry operations, etc.) |
| `ANTHROPIC_API_KEY` | (empty) | Anthropic API key; required when `AI_ENABLED=true` |

## 2. Start the Database

```bash
docker compose up -d
```

This starts a PostgreSQL 17 instance with the database `nevet`, user `nevet`, password `nevet`, on port 5432.
Data is persisted in a Docker volume (`pgdata`), so it survives restarts.

To stop the database:

```bash
docker compose down
```

To stop the database and delete all data:

```bash
docker compose down -v
```

### Docker Profiles

The `docker-compose.yml` includes optional profiles for running the frontend and backend in Docker.
By default, `docker compose up` starts only PostgreSQL - the same behavior as before.

Profile commands:

```bash
# PostgreSQL only (default, unchanged)
docker compose up -d

# PostgreSQL + frontend
docker compose --profile frontend up -d

# PostgreSQL + backend
docker compose --profile backend up -d

# All three services
docker compose --profile frontend --profile backend up -d
```

**Scenario: Frontend in Docker, backend on host** (live deployment pattern).
Set `API_URL=http://host.docker.internal:8080` in your `.env` file, then:

```bash
docker compose --profile frontend up -d
```

The frontend container reaches the host-running backend via `host.docker.internal`.

**Scenario: Backend in Docker, frontend running locally** (UI development).
Build the application locally first (the backend Docker image packages a pre-built JAR, not a source build):

```bash
./mvnw clean install -DskipTests
docker compose --profile backend up -d
cd frontend && npm run dev
```

The local dev server proxies API requests to `http://localhost:8080`, which Docker maps to the backend container.

To build (or rebuild) the Docker images:

```bash
docker compose --profile frontend --profile backend build
```

The backend image must be rebuilt after any code change (`./mvnw clean install -DskipTests` first, then `docker compose --profile backend build`).

## 3. Build the Application

```bash
./mvnw clean install
```

Or without tests if you want to get running quickly:

```bash
./mvnw clean install -DskipTests
```

## 4. Start the Application

```bash
java -jar app/target/app-1.0.jar
```

On first boot with an empty database, Flyway runs all migrations to create the schema.
The `SuperAdminBootstrap` then creates a default superadmin account and logs the generated password:

```
========================================
  Superadmin account created
  Username: admin
  Password: H3SeTdnQ44du2HGQ
  Change this password immediately!
========================================
```

Copy that password - you will need it for HTTP login.
This only happens once; on subsequent boots, the existing superadmin is detected and the bootstrap is skipped.

On later boots, if you ever need to reset the superadmin, delete the `admin` user from the database and restart the app.

## 5. Set Up IRC (Console Commands)

With `CONSOLE_ENABLED=true`, you can type commands directly into the running application's stdin.
The console adapter treats all input as addressed to the bot.

### Connect to an IRC network

```
irc connect libera irc.libera.chat nevet
```

This registers a network named "libera" with the nick "nevet" and connects immediately (since `IRC_ENABLED=true`).

If the network requires SASL authentication:

```
irc connect libera irc.libera.chat nevet myaccount mypassword
```

### Join a channel

```
irc join libera #java
```

### Set autoconnect and autojoin

So the bot reconnects automatically on restart:

```
irc autoconnect libera true
irc autojoin libera #java true
```

See [irc-service.md](irc-service.md) for the full set of IRC admin commands.

## 6. Create Users (Console Commands)

The superadmin can create new user accounts from the console.
The syntax is:

```
create user <username> <email> <displayName> [role]
```

For example, to create a regular user:

```
create user alice alice@example.com Alice
```

To create another admin:

```
create user bob bob@example.com Bob admin
```

Valid roles are `user` (default), `admin`, and `super-admin`.

## 7. Link a Protocol Identity (Console Commands)

User accounts start with no protocol bindings.
To allow a user to be recognized over IRC (or any other protocol), you need to link their protocol identity to their account.

If you're not sure what values to use, `link help` shows what each protocol expects:

```
link help
```
```
Available protocols for identity binding:
  IRC: link user <username> irc <network> <hostmask>
    Networks: libera
```

You can also filter to a specific protocol with `link help <protocol>`.

Here is a concrete example.
You're on the console, the bot is connected to Libera, and you want the IRC user with hostmask `~random_person@cloak/random_person` to be recognized as the `admin` account.
First, check what IRC needs:

```
link help irc
```
```
IRC: link user <username> irc <network> <hostmask>
  Networks: libera
```

The output tells you the serviceId is a network name (and `libera` is available), and the externalIdentifier is a hostmask in `ident@host` format.
The hostmask is the ident and cloaked host assigned by NickServ after authentication - you can see it in `/whois` output.
Using the hostmask instead of the nick prevents identity spoofing, since the cloaked host requires NickServ authentication.

Now link them:

```
link user admin irc libera ~random_person@cloak/random_person
```

This creates a service binding so that when a user with hostmask `~random_person@cloak/random_person` speaks on the `libera` IRC network, the bot recognizes them as the `admin` user with `SUPER_ADMIN` privileges.

After this, `random_person` can issue admin commands directly from IRC:

```
nevet: irc join libera #kotlin
nevet: create user charlie charlie@example.com Charlie
nevet: link user charlie irc libera ~charlie@cloak/charlie
```

To remove a protocol binding, use `unlink user`:

```
unlink user charlie irc libera ~charlie@cloak/charlie
```

This is useful when a user's hostmask changes or when a binding was created with the wrong identifier.

## Complete Onboarding Sequence

Here is the full sequence from zero to a working IRC-connected superadmin:

1. Create `.env` with database credentials and `CONSOLE_ENABLED=true`, `IRC_ENABLED=true`
2. `docker compose up -d` - start PostgreSQL
3. `./mvnw clean install -DskipTests` - build the application
4. `java -jar app/target/app-1.0.jar` - start the app, note the superadmin password from the log
5. `irc connect libera irc.libera.chat nevet` - register and connect to IRC
6. `irc join libera #yourchannel` - join a channel
7. `irc autoconnect libera true` - persist the connection across restarts
8. `irc autojoin libera #yourchannel true` - persist the channel join across restarts
9. `link help irc` - confirm the network name and field names for IRC
10. `link user admin irc libera ~yournick@your/cloak` - link your IRC hostmask to the superadmin account

From this point, you can admin the bot over IRC by addressing it with the signal character or its nick.

11. `feed add https://inside.java` - register an RSS feed
12. `feed subscribe https://inside.java to irc://libera/%23yourchannel` - subscribe the IRC channel to the feed (from the console, using explicit target)

## 10. Set Up RSS Feeds (Console or IRC Commands)

Once the bot is running and connected to IRC, you can register RSS feeds and subscribe channels to them.

### Add a feed

```
feed add https://inside.java
```

This discovers the RSS feed at the URL (via direct parsing or HTML autodiscovery), stores it, and seeds all current entries as a baseline so you only get notified about new posts.

### Subscribe a channel

From the channel you want to receive notifications in:

```
nevet: feed subscribe https://inside.java
```

Or from the console, targeting a specific channel:

```
feed subscribe https://inside.java to irc://libera/%23java
```

The feed must already exist (use `feed add` first).
Without an explicit target, the subscription goes to the channel where the command is issued.

### Verify

From the channel:

```
nevet: feed subscriptions
```

Or from the console, for a specific channel:

```
feed subscriptions for irc://libera/%23java
```

Shows what the channel is subscribed to.

New entries are polled automatically (default every 5 minutes) and delivered as:

```
[Inside Java] New JEP: Pattern Matching for switch - https://inside.java/2026/02/10/jep-xxx/
```

See [rss-service.md](rss-service.md) for the full set of RSS commands and configuration options.

## HTTP Access

The blog service exposes REST endpoints on port 8080.
To authenticate over HTTP, use the superadmin credentials from step 4:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "THE_GENERATED_PASSWORD"}'
```

This returns a JWT that you include in subsequent requests as `Authorization: Bearer <token>`.

See [api-reference.md](api-reference.md) and [api-user-operations.md](api-user-operations.md) for the full API surface.
