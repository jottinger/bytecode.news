import {
  getBackendBaseUrl,
  forwardCookieHeader,
  proxyResponse,
} from "@/lib/proxy-helpers";

export async function POST(request: Request) {
  const response = await fetch(`${getBackendBaseUrl()}/auth/refresh`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      ...forwardCookieHeader(request),
    },
    cache: "no-store",
  });

  return proxyResponse(response, await response.text());
}
