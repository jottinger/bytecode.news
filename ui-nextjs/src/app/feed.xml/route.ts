import { getBackendBaseUrl } from "@/lib/backend-url";

export async function GET(request: Request) {
  try {
    const requestUrl = new URL(request.url);
    const forwardedHost = firstHeaderValue(request.headers.get("x-forwarded-host"));
    const host = forwardedHost || firstHeaderValue(request.headers.get("host")) || requestUrl.host;
    const forwardedProto = firstHeaderValue(request.headers.get("x-forwarded-proto"));
    const proto =
      forwardedProto === "http" || forwardedProto === "https"
        ? forwardedProto
        : requestUrl.protocol.replace(":", "");
    const forwardedPort = firstHeaderValue(request.headers.get("x-forwarded-port"));
    const port = forwardedPort || requestUrl.port || (proto === "https" ? "443" : "80");

    const backendUrl = `${getBackendBaseUrl()}/feed.xml`;
    const response = await fetch(backendUrl, {
      headers: {
        Accept: "application/xml,text/xml;q=0.9,*/*;q=0.8",
        Host: host,
        "X-Forwarded-Host": host,
        "X-Forwarded-Proto": proto,
        "X-Forwarded-Port": port,
      },
      next: { revalidate: 30 },
    });
    if (!response.ok) {
      throw new Error(`API ${response.status} on /feed.xml`);
    }
    const contentType = response.headers.get("content-type") || "application/xml; charset=utf-8";
    const body = await response.text();
    return new Response(body, {
      status: response.status,
      headers: {
        "Content-Type": contentType,
      },
    });
  } catch {
    return new Response("Feed unavailable", {
      status: 503,
      headers: {
        "Content-Type": "text/plain; charset=utf-8",
      },
    });
  }
}

function firstHeaderValue(value: string | null): string | null {
  if (!value) {
    return null;
  }
  return value.split(",")[0]?.trim() || null;
}
