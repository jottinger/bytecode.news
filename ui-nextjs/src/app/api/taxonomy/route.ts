import { getBackendBaseUrl } from "@/lib/backend-url";

export async function GET() {
  const response = await fetch(`${getBackendBaseUrl()}/taxonomy`, {
    method: "GET",
    headers: {
      Accept: "application/json",
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
