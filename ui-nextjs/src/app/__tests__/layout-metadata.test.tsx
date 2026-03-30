import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/font/google", () => ({
  Instrument_Serif: () => ({ variable: "" }),
  Source_Serif_4: () => ({ variable: "" }),
  IBM_Plex_Mono: () => ({ variable: "" }),
}));

describe("layout metadata", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("exposes RSS alternate link metadata for feed discovery", async () => {
    const { metadata } = await import("@/app/layout");
    expect(metadata.alternates?.types?.["application/rss+xml"]).toBe("/feed.xml");
  });

  it("uses BLOG_BASE_URL for metadataBase when present", async () => {
    vi.stubEnv("BLOG_BASE_URL", "https://reference.bytecode.news");
    vi.stubEnv("SITE_URL", "");
    vi.resetModules();

    const { metadata } = await import("@/app/layout");

    expect(metadata.metadataBase?.toString()).toBe("https://reference.bytecode.news/");
  });
});
