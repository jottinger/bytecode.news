import { getBackendBaseUrl, forwardCookieHeader, proxyResponse } from "@/lib/proxy-helpers";

export async function DELETE(
  request: Request,
  context: { params: Promise<{ id: string }> },
) {
  const { id } = await context.params;
  const url = new URL(request.url);
  const hard = url.searchParams.get("hard") || "false";
  const auth = request.headers.get("authorization");

  const response = await fetch(
    `${getBackendBaseUrl()}/admin/comments/${id}?hard=${encodeURIComponent(hard)}`,
    {
      method: "DELETE",
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
