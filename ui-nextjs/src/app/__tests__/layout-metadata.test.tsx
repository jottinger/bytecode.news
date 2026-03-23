import { describe, expect, it, vi } from "vitest";

vi.mock("next/font/google", () => ({
  Instrument_Serif: () => ({ variable: "" }),
  Source_Serif_4: () => ({ variable: "" }),
  IBM_Plex_Mono: () => ({ variable: "" }),
}));

import { metadata } from "@/app/layout";

describe("layout metadata", () => {
  it("exposes RSS alternate link metadata for feed discovery", () => {
    expect(metadata.alternates?.types?.["application/rss+xml"]).toBe("/feed.xml");
  });
});
