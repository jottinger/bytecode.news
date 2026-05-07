import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const articleCss = readFileSync(resolve(process.cwd(), "src/styles/article.css"), "utf8");

describe("admonition styles", () => {
  it("ships static flexmark admonition styles in post bodies", () => {
    expect(articleCss).toContain(".post-body .adm-block");
    expect(articleCss).toContain(".post-body .adm-heading");
    expect(articleCss).toContain(".post-body .adm-body");
    expect(articleCss).toContain(".post-body .adm-icon");
  });
});
