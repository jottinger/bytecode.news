import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const originalEnv = { ...process.env };

describe("post submit api routes", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
    process.env = { ...originalEnv };
    process.env.BACKEND_SCHEME = "https";
    process.env.BACKEND_HOST = "api.bytecode.news";
    delete process.env.API_URL;
  });

  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it("proxies post create to backend with auth passthrough", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"id":"1","title":"T","status":"DRAFT"}', {
        status: 201,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/posts/route");

    const request = new Request("http://localhost:3000/api/posts", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer abc",
      },
      body: JSON.stringify({ title: "T", markdownSource: "M", formLoadedAt: Date.now() }),
    });

    const response = await POST(request);
    expect(response.status).toBe(201);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/posts");
  });

  it("proxies comment create to backend with path segments", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"id":"c1"}', {
        status: 201,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/posts/[year]/[month]/[slug]/comments/route");

    const request = new Request("http://localhost:3000/api/posts/2026/03/test/comments", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer abc",
      },
      body: JSON.stringify({ markdownSource: "Nice post" }),
    });

    const response = await POST(request, {
      params: Promise.resolve({ year: "2026", month: "03", slug: "test" }),
    });

    expect(response.status).toBe(201);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "https://api.bytecode.news/posts/2026/03/test/comments",
    );
  });
});
