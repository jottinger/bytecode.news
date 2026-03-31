import { getBackendBaseUrl } from "@/lib/backend-url";

export async function POST(request: Request) {
  const payload = await request.text();
  const response = await fetch(`${getBackendBaseUrl()}/auth/otp/verify`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
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
