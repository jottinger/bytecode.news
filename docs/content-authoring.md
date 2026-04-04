# Content Authoring

This guide covers markdown features that authors can rely on when writing posts and comments for the blog.

## Admonitions

Admonitions create visibly distinct callout blocks such as notes, warnings, tips, and examples.

Use the Flexmark admonition syntax:

```md
!!! note "Remember"
    This is a note for the reader.
```

The first word after `!!!` is the admonition kind. Common kinds include:

- `note`
- `info`
- `tip`
- `success`
- `warning`
- `danger`
- `example`
- `quote`

The optional quoted string becomes the admonition title. If you omit it, the kind name is used as the title.

Example:

```md
!!! warning "Breaking Change"
    This API changed in version 2.

    Review the migration notes before deploying.
```

This renders as a warning callout with a title and normal markdown content inside the body.

## Collapsible Admonitions

Flexmark also supports collapsible markers using `???` and `???+`.

```md
??? note "More detail"
    Hidden-by-default in systems that support interactive admonitions.

???+ tip "Expanded detail"
    Open-by-default in systems that support interactive admonitions.
```

In the current web UIs, these blocks are rendered as normal readable admonitions without interactive collapse behavior. The content is still shown; only the toggle behavior is omitted.

## Formatting Inside Admonitions

Admonition bodies support normal markdown such as lists, emphasis, links, and code fences.

```md
!!! tip "Checklist"
    - Verify the endpoint
    - Review the logs
    - Rebuild the frontend
```

Indent the body content under the admonition header so it stays inside the callout.
