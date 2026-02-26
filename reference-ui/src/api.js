import { getToken, clearAuth } from "./auth.js";

/**
 * Fetch wrapper that prepends /api/, injects JWT, and handles errors.
 * Throws RFC 9457 ProblemDetail objects on non-2xx responses.
 */
async function api(method, path, body) {
  const headers = { Accept: "application/json" };
  const token = getToken();
  if (token) headers["Authorization"] = "Bearer " + token;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const res = await fetch("/api" + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401) {
    clearAuth();
    window.history.pushState(null, "", "/login?return=" + encodeURIComponent(window.location.pathname));
    window.dispatchEvent(new PopStateEvent("popstate"));
    throw { status: 401, title: "Unauthorized", detail: "Session expired" };
  }

  if (res.status === 204) return null;

  // Read body as text first, then try JSON parse - avoids content-type guessing
  const text = await res.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    data = text;
  }

  if (!res.ok) {
    // ProblemDetail objects have a "status" field; plain strings get wrapped
    if (typeof data === "object" && data !== null) {
      throw data;
    }
    throw { status: res.status, title: res.statusText, detail: data };
  }

  return data;
}

export const get = (path) => api("GET", path);
export const post = (path, body) => api("POST", path, body);
export const put = (path, body) => api("PUT", path, body);
export const del = (path, body) => api("DELETE", path, body);
