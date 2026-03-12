import { describe, expect, it } from "vitest";
import { formatDate } from "@/lib/format";

describe("formatDate", () => {
  it("returns fallback for undefined", () => {
    expect(formatDate()).toBe("Unknown");
  });

  it("returns input for invalid dates", () => {
    expect(formatDate("not-a-date")).toBe("not-a-date");
  });

  it("formats valid dates", () => {
    const formatted = formatDate("2026-03-10T12:34:56Z");
    expect(formatted).toContain("2026");
  });
});
