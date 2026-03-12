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
});
