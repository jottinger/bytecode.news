import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const originalEnv = { ...process.env };

function mockJsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("api client", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
    process.env = { ...originalEnv };
    delete process.env.API_URL;
    delete process.env.BACKEND_SCHEME;
    delete process.env.BACKEND_HOST;
    delete process.env.ACCEPT_VERSION;
  });

  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it("uses localhost fallback backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, {
        adapters: [],
        ai: false,
        anonymousSubmission: false,
        authentication: { otp: true, oidc: { google: false, github: false } },
        operationGroups: [],
        siteName: "bytecode.news",
        version: { name: "nevet" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getFeatures } = await import("@/lib/api");
    await getFeatures();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://localhost:8080/features");
  });

  it("uses BACKEND_SCHEME and BACKEND_HOST when provided", async () => {
    process.env.BACKEND_SCHEME = "https";
    process.env.BACKEND_HOST = "api.bytecode.news";

    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, {
        adapters: [],
        ai: false,
        anonymousSubmission: false,
        authentication: { otp: true, oidc: { google: false, github: false } },
        operationGroups: [],
        siteName: "bytecode.news",
        version: { name: "nevet" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getFeatures } = await import("@/lib/api");
    await getFeatures();

    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/features");
  });

  it("prefers API_URL over scheme/host", async () => {
    process.env.BACKEND_SCHEME = "https";
    process.env.BACKEND_HOST = "ignored.example";
    process.env.API_URL = "http://backend:8080";

    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, {
        adapters: [],
        ai: false,
        anonymousSubmission: false,
        authentication: { otp: true, oidc: { google: false, github: false } },
        operationGroups: [],
        siteName: "bytecode.news",
        version: { name: "nevet" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getFeatures } = await import("@/lib/api");
    await getFeatures();

    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://backend:8080/features");
  });

  it("adds Accept-Version header when configured", async () => {
    process.env.ACCEPT_VERSION = "0.1";
    const fetchMock = vi.fn().mockResolvedValue(mockJsonResponse(200, {
      adapters: [],
      ai: false,
      anonymousSubmission: false,
      authentication: { otp: true, oidc: { google: false, github: false } },
      operationGroups: [],
      siteName: "bytecode.news",
      version: { name: "nevet" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    const { getFeatures } = await import("@/lib/api");
    await getFeatures();

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("Accept-Version")).toBe("0.1");
  });

  it("throws ApiError for non-2xx responses", async () => {
    const fetchMock = vi.fn().mockResolvedValue(mockJsonResponse(503, { title: "Unavailable" }));
    vi.stubGlobal("fetch", fetchMock);

    const { ApiError, listPosts } = await import("@/lib/api");

    await expect(listPosts(0)).rejects.toBeInstanceOf(ApiError);
  });

  it("fetches post list without cache so edits appear immediately", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, { posts: [], page: 0, totalPages: 0, totalCount: 0 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { listPosts } = await import("@/lib/api");
    await listPosts(0);

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
  });

  it("fetches post detail without cache so slug view updates immediately", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, {
        id: "1",
        title: "T",
        slug: "2026/03/t",
        renderedHtml: "<p>x</p>",
        authorDisplayName: "a",
        createdAt: "2026-03-12T00:00:00Z",
        updatedAt: "2026-03-12T00:00:00Z",
        commentCount: 0,
        categories: [],
        tags: [],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getPostBySlug } = await import("@/lib/api");
    await getPostBySlug("2026/03/t");

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
  });

  it("fetches comments without cache so refresh shows new replies immediately", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, { postId: "p1", comments: [], totalActiveCount: 0 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getCommentsBySlug } = await import("@/lib/api");
    await getCommentsBySlug("2026/03/test-post");

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
  });

  it("fetches factoid list with query and no-store cache", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, { factoids: [], page: 0, totalPages: 0, totalCount: 0 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { listFactoids } = await import("@/lib/api");
    await listFactoids(1, 20, "java");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://localhost:8080/factoids?page=1&size=20&q=java");
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
  });

  it("fetches factoid detail by encoded selector", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, {
        selector: "foo bar",
        locked: false,
        updatedAt: "2026-03-12T00:00:00Z",
        accessCount: 0,
        attributes: [],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getFactoid } = await import("@/lib/api");
    await getFactoid("foo bar");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://localhost:8080/factoids/foo%20bar");
  });

  it("fetches karma leaderboard with bounded limit path", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, { top: [], bottom: [], limit: 10 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getKarmaLeaderboard } = await import("@/lib/api");
    await getKarmaLeaderboard(25);

    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://localhost:8080/karma/leaderboard?limit=25");
  });

  it("fetches log provenances without cache", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, { provenances: [] }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getLogProvenances } = await import("@/lib/api");
    await getLogProvenances();

    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://localhost:8080/logs/provenances");
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
  });

  it("fetches logs day with encoded provenance and day", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockJsonResponse(200, { provenanceUri: "x", day: "2026-03-12", entries: [] }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { getLogsDay } = await import("@/lib/api");
    await getLogsDay("irc://libera/%23nevet", "2026-03-12");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/logs?provenance=irc%3A%2F%2Flibera%2F%2523nevet&day=2026-03-12",
    );
  });
});
