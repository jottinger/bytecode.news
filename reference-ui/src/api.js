import { getToken, clearAuth } from "./auth.js";

const API_BASE = import.meta.env.VITE_API_BASE || "/api";
const API_TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS || 8000);

/**
 * Fetch wrapper that prepends the API base URL, injects JWT, and handles errors.
 * Throws RFC 9457 ProblemDetail objects on non-2xx responses.
 */
async function api(method, path, body) {
  const headers = { Accept: "application/json" };
  const token = getToken();
  if (token) headers["Authorization"] = "Bearer " + token;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), API_TIMEOUT_MS);
  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch (err) {
    if (err?.name === "AbortError") {
      throw {
        status: 0,
        title: "Network timeout",
        detail: `Request timed out after ${API_TIMEOUT_MS}ms`,
      };
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
  }

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
