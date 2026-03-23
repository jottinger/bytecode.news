# MCP Integration Guide

This project exposes a read-only MCP-compatible JSON-RPC endpoint for LLM/tool clients.

- Endpoint: `POST /mcp`
- Content type: `application/json`
- Transport: JSON-RPC 2.0 over HTTP
- Scope: read-only, public data (approved posts, factoids, taxonomy)

## What It Is For

Use MCP tools to let an agent:

1. Search and fetch public blog posts.
2. Search and fetch factoids and their structured metadata.
3. Read aggregate taxonomy (tags/categories).
4. Get factoid write syntax guidance (reference only, no mutation).

This is intentionally non-admin and non-mutating in v1.

## Connection

Point your MCP/JSON-RPC-capable client at:

- Production: `https://api.bytecode.news/mcp`
- Local dev: `http://localhost:8080/mcp`

If your client cannot directly use HTTP JSON-RPC MCP endpoints, use a small bridge/proxy that forwards JSON-RPC calls to this URL.

## Handshake

Request:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {}
}
```

Response (shape):

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "serverInfo": {
      "name": "bytecode.news-mcp",
      "version": "1.0"
    },
    "capabilities": {
      "tools": {
        "listChanged": false
      }
    }
  }
}
```

## Tool Discovery

Call `tools/list`:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

Exposed tools:

1. `search_posts(query, page, size)`
2. `get_post(postRef)`
3. `list_factoids(page, size)`
4. `get_factoid(selector)`
5. `search_factoids(query, page, size)`
6. `list_taxonomy()`
7. `factoid_write_reference()`

## Tool Calls

`tools/call` request envelope:

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "search_posts",
    "arguments": {
      "query": "spring ai",
      "page": 0,
      "size": 20
    }
  }
}
```

Success result shape:

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "isError": false,
    "structuredContent": {},
    "content": [
      {
        "type": "text",
        "text": "{...json...}"
      }
    ]
  }
}
```

Error result shape:

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "isError": true,
    "content": [
      {
        "type": "text",
        "text": "error message"
      }
    ]
  }
}
```

## Notes

- `get_post.postRef` accepts:
  - UUID
  - slug path (`YYYY/MM/slug`)
  - full post URL (`https://bytecode.news/posts/YYYY/MM/slug`)
- Only published content is returned for posts.
- No auth/session/admin surfaces are exposed through MCP v1.
