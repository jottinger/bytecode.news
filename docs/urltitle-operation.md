# URL Title Operation

When a user posts a message containing URLs, this operation fetches the page title and reports it to the channel.
Titles that are too similar to the URL itself (determined by Jaccard similarity) are suppressed to avoid noise.

## Title Display

This operation is **unaddressed** - it fires on ambient conversation without requiring a signal character or bot mention.

**Priority:** 91 (runs late, after most other operations).

**Behavior:**

- Extracts all `http://` and `https://` URLs from the message.
- Fetches each URL's HTML and extracts the title.
- Title extraction priority: `og:title` meta tag, then `twitter:title` meta tag, then the `<title>` element.
  The `og:title` tag is preferred because many sites (YouTube, for example) serve useless `<title>` content without JavaScript rendering, but include meaningful OpenGraph metadata for link sharing.
- If a title's Jaccard similarity to the URL exceeds the configured threshold (default 0.3), it is suppressed.
  This avoids reporting titles that just repeat the URL path.
- URLs whose hosts are on the ignored list are skipped entirely.

**Response example:**
```
joe mentioned a url: https://example.com/article ("The Actual Article Title")
joe mentioned 2 urls: https://example.com/one ("First") and https://example.com/two ("Second")
```

## Manage Ignored Hosts

Admin commands for managing which hosts are skipped during title fetching.
This operation is **addressed**.

**Syntax:**
```
url ignore list                 show a sample of ignored hosts
url ignore add <hostname>       add a host to the ignore list
url ignore delete <hostname>    remove a host from the ignore list
```

**Priority:** 50 (default).

**Default ignored hosts:**

- `bpa.st`, `dpaste.com`, `pastebin.com`, `pastebin.org` - paste sites whose titles are not meaningful.
- `twitter.com`, `x.com` - see "Twitter/X.com Limitations" below.

## Twitter/X.com Limitations

Twitter/X.com URLs are ignored by default because there is no free method to extract tweet content programmatically.

**Approaches investigated (February 2026):**

| Approach | Result |
|----------|--------|
| Scraping x.com directly | Returns a JavaScript shell with no content; tweets render client-side only |
| nitter.net proxy | Returns HTTP 200 with 0 bytes body (anti-bot measures) |
| fxtwitter.com proxy | Redirects to x.com |
| vxtwitter.com proxy | Redirects to x.com |
| xcancel.com proxy | JavaScript bot challenge (BotD fingerprinting, service worker validation) |
| Twitter API free tier | No free read access; the API moved to pay-per-use ($0.005/tweet) in January 2026 |

If a free proxy or API tier becomes available in the future, support could be re-added.

### Implementation Plan for Twitter API Support

The `TitleFetcher` interface (`lib-core`) is a single method: `fetchTitle(url: String): String?`.
Currently, `HtmlTitleFetcher` is the only implementation, injected into `UrlTitleService`.
Adding Twitter support requires three pieces:

**1. `TwitterApiTitleFetcher`** - a new `TitleFetcher` that handles twitter/x.com URLs via the Twitter v2 API.

- Parse the tweet ID from the URL path (the numeric segment after `/status/`).
- Call `GET https://api.twitter.com/2/tweets/{id}?expansions=author_id&user.fields=username,name&tweet.fields=text` with a Bearer token.
- Compose the title as `@username: tweet text`.
- The Bearer token comes from app-only OAuth 2.0 authentication (no user context needed for public tweets).

**2. `CompositeTitleFetcher`** - a delegating `TitleFetcher` that routes by URL host.

- If the host is `twitter.com`, `x.com`, or `www.` variants, delegate to `TwitterApiTitleFetcher`.
- Otherwise, delegate to `HtmlTitleFetcher`.
- This replaces `HtmlTitleFetcher` as the bean injected into `UrlTitleService`.
- Marked `@Primary` so Spring picks it over `HtmlTitleFetcher` when both are present.

**3. Conditional activation** - the composite fetcher and Twitter fetcher only activate when a bearer token is configured.

```kotlin
@Configuration
@ConditionalOnProperty(prefix = "streampack.twitter", name = ["bearer-token"])
class TwitterTitleConfiguration {
    // creates TwitterApiTitleFetcher and CompositeTitleFetcher beans
}
```

When the bearer token is absent, `HtmlTitleFetcher` remains the sole `TitleFetcher` and twitter/x.com stays on the ignored hosts list.
When present, the composite fetcher takes over and twitter/x.com should be removed from the ignored hosts list (either manually via `url ignore delete` or by omitting them from `defaultIgnoredHosts` in the deployment's config).

**Configuration properties:**

| Property | Description |
|----------|-------------|
| `streampack.twitter.bearer-token` | Twitter API v2 Bearer token (enables Twitter title fetching when present) |

**Cost estimate (pay-per-use, January 2026 pricing):**
$0.005 per tweet read, with 24-hour deduplication.
A small community channel seeing 20 twitter links per day would cost roughly $3/month.

**No SDK dependency needed.**
All maintained Java/Kotlin Twitter SDKs are either abandoned or unstable.
A single `HttpClient` GET with a Bearer token header is simpler and more reliable than any SDK.
The existing `HttpPageFetcher` pattern (Java `HttpClient`, gzip support, timeout handling) provides a template.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `streampack.urltitle.protocols` | IRC, DISCORD, SLACK, CONSOLE, MATTERMOST | Protocols on which title fetching is active |
| `streampack.urltitle.defaultIgnoredHosts` | bpa.st, dpaste.com, pastebin.com, pastebin.org, twitter.com, x.com | Hosts seeded into the ignore list on startup |
| `streampack.urltitle.similarityThreshold` | 0.3 | Jaccard similarity threshold; titles more similar than this to the URL are suppressed |
| `streampack.urltitle.connectTimeoutSeconds` | 5 | HTTP connection timeout |
| `streampack.urltitle.readTimeoutSeconds` | 10 | HTTP read timeout |
