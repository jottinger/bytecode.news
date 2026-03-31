import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const page = url.searchParams.get("page") || "0";
  const size = url.searchParams.get("size") || "20";
  const deleted = url.searchParams.get("deleted") || "false";
  const auth = request.headers.get("authorization");

  const response = await fetch(
    `${getBackendBaseUrl()}/admin/posts/pending?page=${encodeURIComponent(page)}&size=${encodeURIComponent(size)}&deleted=${encodeURIComponent(deleted)}`,
    {
      method: "GET",
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
