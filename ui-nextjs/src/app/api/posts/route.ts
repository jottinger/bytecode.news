import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const params = new URLSearchParams();
  for (const key of ["page", "size", "category", "tag"]) {
    const val = url.searchParams.get(key);
    if (val) params.set(key, val);
  }

  const response = await fetch(`${getBackendBaseUrl()}/posts?${params.toString()}`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      ...forwardCookieHeader(request),
    },
    cache: "no-store",
  });

  return proxyResponse(response, await response.text());
}

export async function POST(request: Request) {
  const payload = await request.text();
  const auth = request.headers.get("authorization");

  const response = await fetch(`${getBackendBaseUrl()}/posts`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(auth ? { Authorization: auth } : {}),
      ...forwardCookieHeader(request),
    },
    body: payload,
    cache: "no-store",
  });

  return proxyResponse(response, await response.text());
}
