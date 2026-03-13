import { getBackendBaseUrl } from "@/lib/backend-url";

export async function POST(request: Request) {
  const payload = await request.text();
  const auth = request.headers.get("authorization");

  const response = await fetch(`${getBackendBaseUrl()}/posts/derive-summary`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      ...(auth ? { Authorization: auth } : {}),
    },
    body: payload,
    cache: "no-store",
  });

  return new Response(await response.text(), {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") || "application/json",
    },
  });
}
