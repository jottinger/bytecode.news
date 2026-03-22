import { beforeEach, describe, expect, it, vi } from "vitest";

import { GET } from "@/app/feed.xml/route";

describe("feed.xml route", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
    process.env.BACKEND_SCHEME = "https";
    process.env.BACKEND_HOST = "api.bytecode.news";
    delete process.env.API_URL;
  });

  it("returns backend feed body when available", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("<rss></rss>", {
        status: 200,
        headers: { "content-type": "application/xml" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const response = await GET(new Request("https://bytecode.news/feed.xml"));
    const body = await response.text();

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toContain("application/xml");
    expect(body).toBe("<rss></rss>");
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/feed.xml");

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("Host")).toBe("bytecode.news");
    expect(headers.get("X-Forwarded-Host")).toBe("bytecode.news");
    expect(headers.get("X-Forwarded-Proto")).toBe("https");
    expect(headers.get("X-Forwarded-Port")).toBe("443");
  });

  it("returns 503 fallback when backend feed fails", async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error("down"));
    vi.stubGlobal("fetch", fetchMock);

    const response = await GET(new Request("https://bytecode.news/feed.xml"));
    const body = await response.text();

    expect(response.status).toBe(503);
    expect(body).toContain("Feed unavailable");
  });
});
