import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

const PLACEHOLDER_POST_ID = "00000000-0000-0000-0000-000000000000";

export async function POST(request: Request) {
  const payload = await request.text();
  const auth = request.headers.get("authorization");

  const response = await fetch(
    `${getBackendBaseUrl()}/admin/posts/${PLACEHOLDER_POST_ID}/derive-tags`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...(auth ? { Authorization: auth } : {}),
        ...forwardCookieHeader(request),
      },
      body: payload,
      cache: "no-store",
    },
  );

  return proxyResponse(response, await response.text());
}
