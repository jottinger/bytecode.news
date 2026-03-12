import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

const notFoundSentinel = new Error("NOT_FOUND");

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw notFoundSentinel;
  },
}));

vi.mock("@/lib/api", () => {
  class ApiError extends Error {
    readonly status: number;
    readonly path: string;

    constructor(status: number, path: string) {
      super(`API ${status} on ${path}`);
      this.status = status;
      this.path = path;
    }
  }

  return {
    ApiError,
    getFactoid: vi.fn(),
  };
});

import FactoidDetailPage from "@/app/factoids/[selector]/page";
import { ApiError, getFactoid } from "@/lib/api";

const getFactoidMock = vi.mocked(getFactoid);

describe("factoid detail page", () => {
  it("throws notFound for missing selector", async () => {
    getFactoidMock.mockRejectedValueOnce(new ApiError(404, "/factoids/missing"));

    await expect(
      FactoidDetailPage({ params: Promise.resolve({ selector: "missing" }) }),
    ).rejects.toBe(notFoundSentinel);
  });

  it("renders detail and strips tags/see also prefixes", async () => {
    getFactoidMock.mockResolvedValueOnce({
      selector: "mvnd",
      locked: false,
      updatedBy: "dreamreal",
      updatedAt: "2026-03-12T10:00:00Z",
      lastAccessedAt: null,
      accessCount: 4,
      attributes: [
        { type: "tags", value: "java", rendered: "tags: java, build tools" },
        { type: "seealso", value: "maven", rendered: "see also: maven, gradle" },
        { type: "tags", value: "duplicate", rendered: "tags: duplicate" },
      ],
    });

    const element = await FactoidDetailPage({
      params: Promise.resolve({ selector: "mvnd" }),
      searchParams: Promise.resolve({ page: "2", q: "java" }),
    });
    const html = renderToStaticMarkup(element);

    expect(html).toContain("mvnd");
    expect(html).toContain("java, build tools");
    expect(html).toContain("maven, gradle");
    expect(html).not.toContain("tags:");
    expect(html).not.toContain("see also:");
    expect(html).not.toContain("duplicate");
    expect(html).toContain("/factoids?page=2&amp;q=java");
  });
});
