# ui-nextjs

Next.js SSR blog UI for bytecode.news using the public API contract in `docs/openapi.json`.

## Environment

- `BACKEND_SCHEME` (default: `http`)
- `BACKEND_HOST` (default: `localhost:8080`)
- `API_URL` (optional override, takes precedence)
- `PORT` (default: `3000`)

## Run

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
npm run start
```

## Markdown Rendering Notes

`ui-nextjs` consumes backend-rendered post/comment HTML and also renders a local markdown preview for authoring flows.

### Admonitions

The backend emits Flexmark admonition markup using `.adm-*` classes such as:

- `.adm-block`
- `.adm-heading`
- `.adm-icon`
- `.adm-body`
- `.adm-note`, `.adm-warning`, `.adm-tip`, and related type classes

The global stylesheet in [`src/app/globals.css`](src/app/globals.css) provides static styling for that emitted structure. Interactive collapse behavior is intentionally not implemented, so `.adm-collapsed` and `.adm-open` still render visible content.

The local author preview in [`src/components/markdown-preview.tsx`](src/components/markdown-preview.tsx) also recognizes Flexmark-style admonition syntax:

```md
!!! note "Remember"
    Preview this before publishing.
```

This preview support is local to `ui-nextjs` and exists so draft/edit screens stay close to backend rendering without requiring a preview API round-trip.
