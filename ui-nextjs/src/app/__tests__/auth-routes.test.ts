import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const originalEnv = { ...process.env };

describe("auth api routes", () => {
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

  it("proxies otp request payload to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{}", {
        status: 202,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/auth/otp/request/route");
    const request = new Request("http://localhost:3000/api/auth/otp/request", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: "you@example.com" }),
    });

    const response = await POST(request);

    expect(response.status).toBe(202);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/otp/request");
  });

  it("proxies otp verify payload to backend and forwards Set-Cookie", async () => {
    const backendHeaders = new Headers({
      "content-type": "application/json",
    });
    backendHeaders.append("set-cookie", "access_token=jwt123; HttpOnly; Path=/");
    backendHeaders.append("set-cookie", "refresh_token=rt456; HttpOnly; Path=/auth");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"token":"t","principal":{"id":"1","username":"u","displayName":"U","role":"USER"}}', {
        status: 200,
        headers: backendHeaders,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/auth/otp/verify/route");
    const request = new Request("http://localhost:3000/api/auth/otp/verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: "you@example.com", code: "123456" }),
    });

    const response = await POST(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/otp/verify");
    const setCookies = response.headers.getSetCookie();
    expect(setCookies.length).toBe(2);
  });

  it("passes authorization through logout", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/auth/logout/route");
    const request = new Request("http://localhost:3000/api/auth/logout", {
      method: "POST",
      headers: { Authorization: "Bearer abc" },
    });

    const response = await POST(request);
    expect(response.status).toBe(204);

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("Authorization")).toBe("Bearer abc");
  });

  it("proxies profile update payload to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"id":"1","username":"u","displayName":"New","role":"USER"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { PUT } = await import("@/app/api/auth/profile/route");
    const request = new Request("http://localhost:3000/api/auth/profile", {
      method: "PUT",
      headers: {
        Authorization: "Bearer abc",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ displayName: "New" }),
    });

    const response = await PUT(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/profile");
  });

  it("proxies account export to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"username":"u"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/auth/export/route");
    const request = new Request("http://localhost:3000/api/auth/export", {
      method: "GET",
      headers: { Authorization: "Bearer abc" },
    });

    const response = await GET(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/export");
  });

  it("forwards cookies on session validation to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"username":"u"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/auth/session/route");
    const request = new Request("http://localhost:3000/api/auth/session", {
      method: "GET",
      headers: { Cookie: "access_token=jwt123" },
    });

    const response = await GET(request);
    expect(response.status).toBe(200);
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("Cookie")).toBe("access_token=jwt123");
  });

  it("proxies refresh token request with cookies", async () => {
    const backendHeaders = new Headers({
      "content-type": "application/json",
    });
    backendHeaders.append("set-cookie", "access_token=newjwt; HttpOnly; Path=/");
    backendHeaders.append("set-cookie", "refresh_token=newrt; HttpOnly; Path=/auth");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"token":"newjwt","principal":{"id":"1","username":"u","displayName":"U","role":"USER"}}', {
        status: 200,
        headers: backendHeaders,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { POST } = await import("@/app/api/auth/refresh/route");
    const request = new Request("http://localhost:3000/api/auth/refresh", {
      method: "POST",
      headers: { Cookie: "refresh_token=oldrt" },
    });

    const response = await POST(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/refresh");
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("Cookie")).toBe("refresh_token=oldrt");
    const setCookies = response.headers.getSetCookie();
    expect(setCookies.length).toBe(2);
  });

  it("proxies session validation to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"username":"u"}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/auth/session/route");
    const request = new Request("http://localhost:3000/api/auth/session", {
      method: "GET",
      headers: { Authorization: "Bearer abc" },
    });

    const response = await GET(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/session");
  });

  it("proxies account delete to backend", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('{"ok":true}', {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { DELETE } = await import("@/app/api/auth/account/route");
    const request = new Request("http://localhost:3000/api/auth/account", {
      method: "DELETE",
      headers: {
        Authorization: "Bearer abc",
        "Content-Type": "application/json",
      },
      body: "{}",
    });

    const response = await DELETE(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/auth/account");
  });

  it("proxies admin user listing by status", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("[]", {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const { GET } = await import("@/app/api/admin/users/route");
    const request = new Request("http://localhost:3000/api/admin/users?status=SUSPENDED", {
      method: "GET",
      headers: { Authorization: "Bearer admin" },
    });

    const response = await GET(request);
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("https://api.bytecode.news/admin/users?status=SUSPENDED");
  });

  it("redirects oauth2 authorization route to backend", async () => {
    const { GET } = await import("@/app/api/oauth2/authorization/[provider]/route");

    const response = await GET(new Request("http://localhost:3000/api/oauth2/authorization/google?origin=http://localhost:3000"), {
      params: Promise.resolve({ provider: "google" }),
    });

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "https://api.bytecode.news/oauth2/authorization/google?origin=http%3A%2F%2Flocalhost%3A3000",
    );
  });
});
