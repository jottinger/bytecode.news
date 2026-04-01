import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function PUT(
  request: Request,
  context: { params: Promise<{ username: string }> },
) {
  const { username } = await context.params;
  const auth = request.headers.get("authorization");

  const response = await fetch(
    `${getBackendBaseUrl()}/admin/users/${encodeURIComponent(username)}/suspend`,
    {
      method: "PUT",
      headers: {
        Accept: "application/json",
        ...(auth ? { Authorization: auth } : {}),
        ...forwardCookieHeader(request),
      },
      cache: "no-store",
    },
  );

  return proxyResponse(response, await response.text());
}
