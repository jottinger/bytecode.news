import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function DELETE(request: Request) {
  const payload = await request.text();
  const auth = request.headers.get("authorization");

  const response = await fetch(`${getBackendBaseUrl()}/auth/account`, {
    method: "DELETE",
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
