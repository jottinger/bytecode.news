import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function GET(request: Request) {
  const response = await fetch(`${getBackendBaseUrl()}/features`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      ...forwardCookieHeader(request),
    },
    cache: "no-store",
  });

  return proxyResponse(response, await response.text());
}
