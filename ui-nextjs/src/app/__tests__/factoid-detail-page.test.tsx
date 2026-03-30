import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

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
  it("exports social metadata for factoid detail pages", async () => {
    getFactoidMock.mockResolvedValueOnce({
      selector: "mvnd",
      locked: false,
      updatedBy: "dreamreal",
      updatedAt: "2026-03-12T10:00:00Z",
      lastAccessedAt: null,
      accessCount: 4,
      attributes: [],
    });

    const module = await import("@/app/factoids/[selector]/page");
    const metadata = await module.generateMetadata({
      params: Promise.resolve({ selector: "mvnd" }),
    });

    expect(metadata.title).toBe("mvnd");
    expect(metadata.openGraph?.title).toBe("mvnd");
    expect(metadata.twitter?.title).toBe("mvnd");
    expect(metadata.alternates?.canonical).toBe("https://bytecode.news/factoids/mvnd");
    expect(metadata.openGraph?.url).toBe("https://bytecode.news/factoids/mvnd");
  });

  it("renders missing factoid guidance for 404 selector", async () => {
    getFactoidMock.mockRejectedValueOnce(new ApiError(404, "/factoids/missing"));

    const element = await FactoidDetailPage({ params: Promise.resolve({ selector: "missing" }) });
    const html = renderToStaticMarkup(element);
    expect(html).toContain("Factoid not found");
    expect(html).toContain("missing");
    expect(html).toContain("!missing is");
  });

  it("renders detail with structured values for tags, see also, and urls", async () => {
    getFactoidMock.mockResolvedValueOnce({
      selector: "mvnd",
      locked: false,
      updatedBy: "dreamreal",
      updatedAt: "2026-03-12T10:00:00Z",
      lastAccessedAt: null,
      accessCount: 4,
      attributes: [
        { type: "tags", value: "java, build tools", rendered: "tags: java, build tools" },
        { type: "seealso", value: "maven, gradle", rendered: "see also: maven, gradle" },
        { type: "urls", value: "https://mvnd.apache.org", rendered: "URLs: https://mvnd.apache.org" },
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
    expect(html).toContain(">maven<");
    expect(html).toContain(">gradle<");
    expect(html).toContain('href="/factoids/maven"');
    expect(html).toContain('href="/factoids/gradle"');
    expect(html).toContain('href="https://mvnd.apache.org/"');
    expect(html).not.toContain("duplicate");
    expect(html).toContain("/factoids?page=2&amp;q=java");
  });

  it("applies <reply> semantics for text attributes", async () => {
    getFactoidMock.mockResolvedValueOnce({
      selector: "foo",
      locked: false,
      updatedBy: "dreamreal",
      updatedAt: "2026-03-24T10:00:00Z",
      lastAccessedAt: null,
      accessCount: 2,
      attributes: [
        { type: "text", value: "<reply>a bar", rendered: "foo is <reply>a bar" },
      ],
    });

    const element = await FactoidDetailPage({ params: Promise.resolve({ selector: "foo" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain(">a bar<");
    expect(html).not.toContain("&lt;reply&gt;");
    expect(html).not.toContain("foo is");
  });

  it("prefixes selector for normal text attributes", async () => {
    getFactoidMock.mockResolvedValueOnce({
      selector: "foo",
      locked: false,
      updatedBy: "dreamreal",
      updatedAt: "2026-03-24T10:00:00Z",
      lastAccessedAt: null,
      accessCount: 2,
      attributes: [
        { type: "text", value: "a bar", rendered: "foo is a bar" },
      ],
    });

    const element = await FactoidDetailPage({ params: Promise.resolve({ selector: "foo" }) });
    const html = renderToStaticMarkup(element);

    expect(html).toContain(">foo is a bar<");
  });
});
