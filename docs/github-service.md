# GitHub Service Configuration Guide

The GitHub service lets Nevet watch GitHub repositories and route notifications for new issues, pull requests, and releases to any protocol destination (IRC channels, console, Discord, Slack).

Repository registration and subscription are separate operations, following the same pattern as the RSS service.
You add a repository to tell Nevet about it; you subscribe a channel to tell Nevet where to send notifications when new activity appears.

## Activation

The GitHub service is always active when `service-github` is on the classpath.
There is no on/off switch - if the module is included in the app POM, it runs.

Polling interval is configurable:

```yaml
streampack:
  github:
    poll-interval: PT60M          # ISO-8601 duration, default 60 minutes
    connect-timeout-seconds: 5    # HTTP connect timeout for API calls
    read-timeout-seconds: 10      # HTTP read timeout for API calls
```

The poll interval can also be set via environment variable:

```
GITHUB_POLL_INTERVAL=PT60M
```

The default of 60 minutes is conservative.
The GitHub API allows 5000 requests per hour with authentication (60 without).
Each poll costs one API request per watched repository, so even with dozens of repos, hourly polling stays well within limits.

## How It Works

### Repository lifecycle

1. **Add** a repository with `github add <owner/repo>` - Nevet validates the repository exists via the GitHub API, stores it, and seeds all current issues, PRs, and releases as a baseline.
2. **Subscribe** a channel with `github subscribe <owner/repo>` - links the repo to the channel where the command was issued.
3. **Poll** - a scheduled service checks all active repositories at the configured interval, detects new items by number/tag comparison, and stores them.
4. **Notify** - for each new item on a subscribed repo, a notification is sent to the egress channel with the subscriber's provenance.

### Private repositories

Public repositories need no authentication.
Private repositories require a GitHub personal access token (fine-grained PAT recommended) with read access to the repository.

Provide the token when adding the repo:

```
github add owner/private-repo ghp_xxxxxxxxxxxxxxxxxxxx
```

The token is stored with the repository record and used for all API calls to that repo.
Tokens are redacted from the message log - they appear as `[REDACTED]` in stored messages.

If no token is provided, Nevet uses unauthenticated API access.
This works for public repos but has a lower rate limit (60 requests/hour shared across all unauthenticated calls).

### What gets tracked

Each poll checks for:

- **Issues** - new issues opened since the last poll
- **Pull requests** - new PRs opened since the last poll
- **Releases** - new releases published since the last poll

Nevet tracks the highest seen issue/PR number and the set of known release tags.
Anything with a higher number or an unknown tag is new.

Comments, status checks, merges, and other activity are not tracked.
This keeps the notification volume manageable and the polling cost low.

### Notification format

```
[owner/repo] New issue #42: Fix login timeout - https://github.com/owner/repo/issues/42
[owner/repo] New PR #43: Add retry logic - https://github.com/owner/repo/pull/43
[owner/repo] New release v1.2.3 - https://github.com/owner/repo/releases/tag/v1.2.3
```

Notifications are published directly to the egress channel, not through ingress/operations.
They are system-generated output, not inbound commands.

## Commands

All commands require addressing (signal character or bot nick prefix).
Registration and removal require ADMIN role.

### Repository registration

| Command | Effect |
|---------|--------|
| `github add <owner/repo> [token]` | Register a repository to watch. Validates it exists via the API. Seeds current issues/PRs/releases as baseline. Token is optional (needed for private repos). |
| `github remove <owner/repo>` | Deactivate a repo and all its subscriptions. Stops polling. |
| `github list` | Show all watched repositories |

### Subscription management

| Command | Effect |
|---------|--------|
| `github subscribe <owner/repo>` | Subscribe the current channel to a repo's notifications |
| `github subscribe <owner/repo> to <provenance-uri>` | Subscribe an explicit channel to a repo |
| `github unsubscribe <owner/repo>` | Unsubscribe the current channel from a repo |
| `github unsubscribe <owner/repo> to <provenance-uri>` | Unsubscribe an explicit channel from a repo |
| `github subscriptions` | Show what the current channel is subscribed to |
| `github subscriptions for <provenance-uri>` | Show subscriptions for an explicit channel |

Without an explicit target, the destination is the channel where the command was issued.
With `to <provenance-uri>` or `for <provenance-uri>`, you can manage subscriptions from anywhere - including the console.

## Typical Setup Workflow

### From an IRC channel

```
> github add anthropics/claude-code
Watching anthropics/claude-code (142 issues, 38 PRs, 12 releases)

> github subscribe anthropics/claude-code
Subscribed to anthropics/claude-code

> github subscriptions
anthropics/claude-code
```

### From the console (private repo with token)

```
> github add myorg/internal-tool ghp_xxxxxxxxxxxxxxxxxxxx
Watching myorg/internal-tool (7 issues, 3 PRs, 2 releases)

> github subscribe myorg/internal-tool to irc://libera/%23myorg
Subscribed to myorg/internal-tool

> github subscriptions for irc://libera/%23myorg
myorg/internal-tool
```

### When new activity appears

```
[anthropics/claude-code] New issue #143: Support for custom prompts - https://github.com/anthropics/claude-code/issues/143
[myorg/internal-tool] New PR #11: Add retry logic - https://github.com/myorg/internal-tool/pull/11
```

## Token Management

### Creating a token

GitHub offers two token types.
Fine-grained personal access tokens are recommended because they can be scoped to specific repositories with minimal permissions.

To create a fine-grained PAT:

1. Go to **Settings > Developer settings > Personal access tokens > Fine-grained tokens** (or visit `https://github.com/settings/personal-access-tokens/new` directly).
2. Give the token a descriptive name (e.g., "nevet-myorg-internal-tool").
3. Set an expiration - GitHub requires one, but you can choose up to one year.
4. Under **Repository access**, select **Only select repositories** and pick the repo you want Nevet to watch.
5. Under **Permissions > Repository permissions**, grant:
   - **Issues**: Read-only
   - **Pull requests**: Read-only
   - **Contents**: Read-only (needed to verify the repo exists)
6. Click **Generate token** and copy it immediately - GitHub will not show it again.

For classic PATs (Settings > Developer settings > Personal access tokens > Tokens (classic)):
- Private repos need the `repo` scope.
- Public repos need no scopes at all.

Fine-grained tokens are preferred because a classic `repo` token grants write access to every repo on your account.
A fine-grained token scoped to one repo with read-only permissions is the least-privilege option.

### Updating a token

To change the token for a repo, remove it and re-add with the new token:

```
github remove myorg/internal-tool
github add myorg/internal-tool ghp_new_token_here
```

Re-adding seeds the current baseline again, so you will not get duplicate notifications.

### Token expiration

GitHub requires an expiration on fine-grained PATs.
When a token expires, API calls for that repo will start failing silently - Nevet will log the error but will not notify channels about the failure.
To renew, generate a new token in GitHub and re-add the repo:

```
github remove myorg/internal-tool
github add myorg/internal-tool ghp_new_token_here
```

There is no command to update a token in place.
The remove-and-add cycle re-seeds the baseline, so no duplicate notifications are generated.
