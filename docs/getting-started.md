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
| `IRC_SIGNAL` | `!` | Global IRC signal character |

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

The syntax is:

```
link user <username> <protocol> <serviceId> <externalIdentifier>
```

For example, to link the superadmin's IRC nick on Libera:

```
link user admin irc libera dreamreal
```

This creates a service binding so that when `dreamreal` speaks on the `libera` IRC network, the bot recognizes them as the `admin` user with `SUPER_ADMIN` privileges.

After this, `dreamreal` can issue admin commands directly from IRC:

```
nevet: irc join libera #kotlin
nevet: create user charlie charlie@example.com Charlie
nevet: link user charlie irc libera charlie_irc
```

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
9. `link user admin irc libera yournick` - link your IRC nick to the superadmin account

From this point, you can admin the bot over IRC by addressing it with the signal character or its nick.

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
