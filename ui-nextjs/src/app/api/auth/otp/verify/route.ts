import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function POST(request: Request) {
  const payload = await request.text();
  const response = await fetch(`${getBackendBaseUrl()}/auth/otp/verify`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...forwardCookieHeader(request),
    },
    body: payload,
    cache: "no-store",
  });

  return proxyResponse(response, await response.text());
}
