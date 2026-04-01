import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function POST(request: Request) {
  const auth = request.headers.get("authorization");
  const response = await fetch(`${getBackendBaseUrl()}/auth/logout`, {
    method: "POST",
    headers: {
      ...(auth ? { Authorization: auth } : {}),
      ...forwardCookieHeader(request),
    },
    cache: "no-store",
  });

  return proxyResponse(response, null);
}
