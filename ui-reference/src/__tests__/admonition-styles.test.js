import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const indexHtml = readFileSync(resolve(process.cwd(), "index.html"), "utf8");

describe("admonition styles", () => {
  it("ships static flexmark admonition styles in the shell stylesheet", () => {
    expect(indexHtml).toContain(".adm-block");
    expect(indexHtml).toContain(".adm-heading");
    expect(indexHtml).toContain(".adm-body");
    expect(indexHtml).toContain(".adm-icon");
  });
});
