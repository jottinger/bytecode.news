import { getBackendBaseUrl } from "@/lib/backend-url";

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
      },
      cache: "no-store",
    },
  );

  return new Response(await response.text(), {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") || "application/json",
    },
  });
}
