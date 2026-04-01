import { getBackendBaseUrl } from "@/lib/backend-url";

/** Extracts the Cookie header from the incoming request for forwarding to the backend */
export function forwardCookieHeader(
  request: Request,
): Record<string, string> {
  const cookie = request.headers.get("cookie");
  return cookie ? { Cookie: cookie } : {};
}

/** Creates a Response that forwards Set-Cookie headers from the backend to the client */
export function proxyResponse(
  backendResponse: Response,
  body: string | null,
): Response {
  const headers = new Headers();
  const contentType = backendResponse.headers.get("content-type");
  if (contentType) {
    headers.set("Content-Type", contentType);
  }
  for (const cookie of backendResponse.headers.getSetCookie()) {
    headers.append("Set-Cookie", cookie);
  }
  return new Response(body, { status: backendResponse.status, headers });
}

/** Builds the standard headers for a proxied request, forwarding auth and cookies */
export function proxyHeaders(
  request: Request,
  options?: { contentType?: boolean },
): Record<string, string> {
  const auth = request.headers.get("authorization");
  return {
    Accept: "application/json",
    ...(options?.contentType !== false ? { "Content-Type": "application/json" } : {}),
    ...(auth ? { Authorization: auth } : {}),
    ...forwardCookieHeader(request),
  };
}

/** Returns the backend base URL (re-exported for convenience) */
export { getBackendBaseUrl };
