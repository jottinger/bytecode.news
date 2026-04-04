import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { MarkdownPreview } from "@/components/markdown-preview";

describe("MarkdownPreview", () => {
  it("renders headings, paragraph, and list", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source={"# Title\n\ntext\n\n- a\n- b"} />);
    expect(html).toContain("<h1>Title</h1>");
    expect(html).toContain("<p>text</p>");
    expect(html).toContain("<ul>");
    expect(html).toContain("<li>a</li>");
  });

  it("does not execute raw html", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source={"<script>alert(1)</script>"} />);
    expect(html).not.toContain("<script>");
  });

  it("applies inline formatting", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source={"**bold** *em* `code`"} />);
    expect(html).toContain("<strong>bold</strong>");
    expect(html).toContain("<em>em</em>");
    expect(html).toContain("<code>code</code>");
  });

  it("renders links", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source="[site](https://example.com)" />);
    expect(html).toContain('href="https://example.com"');
    expect(html).toContain("site");
  });

  it("renders fenced code blocks", () => {
    const html = renderToStaticMarkup(
      <MarkdownPreview source={"```kotlin\nval x = 1 < 2\n```\n"} />
    );
    expect(html).toContain("<pre><code");
    expect(html).toContain("language-kotlin");
    expect(html).toContain("hljs");
    expect(html).toContain("&lt;");
    expect(html).toContain("</code></pre>");
  });

  it("renders factoid wikilink selector syntax", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source={"See [[kotauth]]"} />);
    expect(html).toContain('href="/factoids/kotauth"');
    expect(html).toContain(">kotauth<");
  });

  it("renders factoid wikilink label syntax", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source={"See [[KotAuth|kotauth]]"} />);
    expect(html).toContain('href="/factoids/kotauth"');
    expect(html).toContain(">KotAuth<");
  });

  it("renders factoid wikilink with encoded spaces in href", () => {
    const html = renderToStaticMarkup(<MarkdownPreview source={"See [[comprehension debt]]"} />);
    expect(html).toContain('href="/factoids/comprehension%20debt"');
    expect(html).toContain(">comprehension debt<");
  });

  it("renders flexmark admonition syntax as static admonition markup", () => {
    const html = renderToStaticMarkup(
      <MarkdownPreview
        source={`!!! note "Remember"
    This needs UI styling.

    - and markdown inside`}
      />,
    );
    expect(html).toContain("adm-block adm-note");
    expect(html).toContain("adm-heading");
    expect(html).toContain("adm-body");
    expect(html).toContain(">Remember<");
    expect(html).toContain("This needs UI styling.");
    expect(html).toContain("<li>and markdown inside</li>");
  });

  it("keeps collapsible admonition previews readable without interaction", () => {
    const html = renderToStaticMarkup(
      <MarkdownPreview
        source={`??? warning "Heads up"
    Preview should still show this content.`}
      />,
    );
    expect(html).toContain("adm-block adm-warning adm-collapsed");
    expect(html).toContain("Preview should still show this content.");
  });
});
