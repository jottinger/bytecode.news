import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const provenance = url.searchParams.get("provenance") || "";
  const day = url.searchParams.get("day") || "";
  const auth = request.headers.get("authorization");
  const qs = new URLSearchParams({ provenance, day });

  const response = await fetch(`${getBackendBaseUrl()}/logs?${qs.toString()}`, {
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
