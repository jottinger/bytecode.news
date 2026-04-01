import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function PUT(
  request: Request,
  context: { params: Promise<{ id: string }> },
) {
  const { id } = await context.params;
  const payload = await request.text();
  const auth = request.headers.get("authorization");

  const response = await fetch(`${getBackendBaseUrl()}/comments/${id}`, {
    method: "PUT",
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
