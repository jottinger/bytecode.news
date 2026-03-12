import { getBackendBaseUrl } from "@/lib/backend-url";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const provenance = url.searchParams.get("provenance") || "";
  const day = url.searchParams.get("day") || "";
  const auth = request.headers.get("authorization");
  const qs = new URLSearchParams({ provenance, day });

  const response = await fetch(`${getBackendBaseUrl()}/logs?${qs.toString()}`, {
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
