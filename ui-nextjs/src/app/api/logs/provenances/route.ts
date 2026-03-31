import { getBackendBaseUrl } from "@/lib/backend-url";

export async function GET(request: Request) {
  const auth = request.headers.get("authorization");
  const response = await fetch(`${getBackendBaseUrl()}/logs/provenances`, {
    method: "GET",
    headers: {
      Accept: "application/json",
      ...(auth ? { Authorization: auth } : {}),
    },
    cache: "no-store",
  });

  return new Response(await response.text(), {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") || "application/json",
    },
  });
}
