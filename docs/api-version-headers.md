# API Version Headers (Advisory Mode)

ByteCode.News supports header-based API version signaling in advisory mode.

## Request Header

- `Accept-Version` (optional): client-requested API contract version.

## Response Headers

- `Content-Version`: server's current API contract version.
- `Accept-Version`: resolved request version used for this response.

## Current Behavior

- If `Accept-Version` is absent, the server resolves to the current version.
- If `Accept-Version` is recognized, it is echoed back.
- If `Accept-Version` is unrecognized, behavior falls back to current version and a warning is logged.

No endpoint behavior changes are currently version-gated. This is scaffolding for future, explicit version routing.

