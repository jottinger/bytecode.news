import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function GET(request: Request) {
  const auth = request.headers.get("authorization");

  const response = await fetch(`${getBackendBaseUrl()}/auth/export`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      ...(auth ? { Authorization: auth } : {}),
      ...forwardCookieHeader(request),
    },
    cache: "no-store",
  });

  return proxyResponse(response, await response.text());
}
