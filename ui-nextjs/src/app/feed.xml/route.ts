import { getBackendBaseUrl } from "@/lib/backend-url";

export async function GET(request: Request) {
  try {
    const requestUrl = new URL(request.url);
    const backendUrl = `${getBackendBaseUrl()}/feed.xml`;
    const response = await fetch(backendUrl, {
      headers: {
        Accept: "application/xml,text/xml;q=0.9,*/*;q=0.8",
        Host: requestUrl.host,
        "X-Forwarded-Host": requestUrl.host,
        "X-Forwarded-Proto": requestUrl.protocol.replace(":", ""),
        "X-Forwarded-Port": requestUrl.port || (requestUrl.protocol === "https:" ? "443" : "80"),
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
