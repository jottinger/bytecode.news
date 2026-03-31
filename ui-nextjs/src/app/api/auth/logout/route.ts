import { getBackendBaseUrl } from "@/lib/backend-url";

export async function POST(request: Request) {
  const auth = request.headers.get("authorization");
  const response = await fetch(`${getBackendBaseUrl()}/auth/logout`, {
    method: "POST",
    headers: auth ? { Authorization: auth } : undefined,
    cache: "no-store",
  });

  return new Response(null, {
    status: response.status,
  });
}
