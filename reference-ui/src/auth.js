const TOKEN_KEY = "nevet_token";
const PRINCIPAL_KEY = "nevet_principal";
const listeners = [];

let token = localStorage.getItem(TOKEN_KEY);
let principal = JSON.parse(localStorage.getItem(PRINCIPAL_KEY) || "null");

export function getToken() { return token; }
export function getPrincipal() { return principal; }
export function isLoggedIn() { return token !== null; }

/** Register a callback for auth state changes */
export function onAuthChange(fn) {
  listeners.push(fn);
}

function notify() {
  listeners.forEach((fn) => fn(principal));
}

/** Store token and decoded principal after login */
export function setAuth(newToken, newPrincipal) {
  token = newToken;
  principal = newPrincipal;
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(PRINCIPAL_KEY, JSON.stringify(principal));
  notify();
}

/** Update just the principal (e.g., after a profile edit) without changing the token */
export function setPrincipal(newPrincipal) {
  principal = newPrincipal;
  localStorage.setItem(PRINCIPAL_KEY, JSON.stringify(principal));
  notify();
}

/** Clear auth state on logout or 401 */
export function clearAuth() {
  token = null;
  principal = null;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(PRINCIPAL_KEY);
  notify();
}

/** Attempt silent session restore via token refresh */
export async function initAuth() {
  if (!token) return;
  try {
    const res = await fetch("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    });
    if (res.ok) {
      const data = await res.json();
      setAuth(data.token, data.principal);
    } else {
      clearAuth();
    }
  } catch {
    // Network error - keep existing token, it may still be valid
  }
}
