import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const globalsCss = readFileSync(resolve(process.cwd(), "src/app/globals.css"), "utf8");

describe("admonition styles", () => {
  it("ships static flexmark admonition styles in post bodies", () => {
    expect(globalsCss).toContain(".post-body .adm-block");
    expect(globalsCss).toContain(".post-body .adm-heading");
    expect(globalsCss).toContain(".post-body .adm-body");
    expect(globalsCss).toContain(".post-body .adm-icon");
  });
});
