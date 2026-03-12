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

  it("proxies category list route to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('[{"id":"1","name":"java"}]', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/categories/route");
    const response = await GET();

    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/categories");
  });

  it("proxies admin category create route to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"id":"1","name":"java"}', {
        status: 201,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/admin/categories/route");
    const request = new Request("http://localhost:3000/api/admin/categories", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer admin",
      },
      body: JSON.stringify({ name: "java", parentId: null }),
    });

    const response = await POST(request);
    expect(response.status).toBe(201);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/admin/categories");
  });

  it("proxies admin category delete route to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"message":"Category deleted"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { DELETE } = await import("@/app/api/admin/categories/[id]/route");
    const request = new Request("http://localhost:3000/api/admin/categories/abc", {
      method: "DELETE",
      headers: {
        Authorization: "Bearer admin",
      },
    });

    const response = await DELETE(request, { params: Promise.resolve({ id: "abc" }) });
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/admin/categories/abc");
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

  it("proxies heuristic tag suggestion to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"tags":["java","spring"]}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/posts/derive-tags/route");

    const request = new Request("http://localhost:3000/api/posts/derive-tags", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ title: "T", markdownSource: "M", existingTags: [] }),
    });

    const response = await POST(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/posts/derive-tags");
  });

  it("proxies admin AI tag derivation to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"tags":["java","ai"]}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/admin/posts/derive-tags/route");

    const request = new Request("http://localhost:3000/api/admin/posts/derive-tags", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer abc",
      },
      body: JSON.stringify({ title: "T", markdownSource: "M", existingTags: [] }),
    });

    const response = await POST(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "https://api.bytecode.news/admin/posts/00000000-0000-0000-0000-000000000000/derive-tags",
    );
  });

  it("proxies comment edit to backend with auth passthrough", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"id":"c1"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { PUT } = await import("@/app/api/comments/[id]/route");

    const request = new Request("http://localhost:3000/api/comments/c1", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer abc",
      },
      body: JSON.stringify({ markdownSource: "Edited" }),
    });

    const response = await PUT(request, { params: Promise.resolve({ id: "c1" }) });
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/comments/c1");
  });

  it("proxies admin comment delete to backend with hard flag", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"id":"c1"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { DELETE } = await import("@/app/api/admin/comments/[id]/route");

    const request = new Request("http://localhost:3000/api/admin/comments/c1?hard=true", {
      method: "DELETE",
      headers: {
        Authorization: "Bearer abc",
      },
    });

    const response = await DELETE(request, { params: Promise.resolve({ id: "c1" }) });
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "https://api.bytecode.news/admin/comments/c1?hard=true",
    );
  });

  it("proxies log provenance list with auth passthrough", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"provenances":[]}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/logs/provenances/route");
    const request = new Request("http://localhost:3000/api/logs/provenances", {
      method: "GET",
      headers: { Authorization: "Bearer abc" },
    });

    const response = await GET(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/logs/provenances");
  });

  it("proxies log day query to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"provenanceUri":"x","day":"2026-03-12","entries":[]}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/logs/route");
    const request = new Request(
      "http://localhost:3000/api/logs?provenance=irc%3A%2F%2Flibera%2F%2523nevet&day=2026-03-12",
      {
        method: "GET",
        headers: { Authorization: "Bearer abc" },
      },
    );

    const response = await GET(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "https://api.bytecode.news/logs?provenance=irc%3A%2F%2Flibera%2F%2523nevet&day=2026-03-12",
    );
  });
});
