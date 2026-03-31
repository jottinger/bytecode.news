"use client";

export type Role = "GUEST" | "USER" | "ADMIN" | "SUPER_ADMIN";

export interface UserPrincipal {
  id: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface LoginResponse {
  token: string;
  principal: UserPrincipal;
}

interface AuthState {
  principal: UserPrincipal | null;
}

type SessionValidationResult = "no-token" | "valid" | "refreshed" | "expired" | "network-error";

const PRINCIPAL_KEY = "ui_nextjs_principal";
const AUTH_EVENT = "ui-nextjs-auth-change";

function parsePrincipal(raw: string | null): UserPrincipal | null {
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as UserPrincipal;
  } catch {
    return null;
  }
}

/** Returns the current auth state (principal only; tokens are in httpOnly cookies) */
export function getAuthState(): AuthState {
  if (typeof window === "undefined") {
    return { principal: null };
  }

  const principal = parsePrincipal(
    window.localStorage.getItem(PRINCIPAL_KEY),
  );
  return { principal };
}

function notifyAuthChange(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(AUTH_EVENT));
  }
}

/** Stores the principal from a successful login (tokens are set as httpOnly cookies by the backend) */
export function setAuth(login: LoginResponse): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(
    PRINCIPAL_KEY,
    JSON.stringify(login.principal),
  );
  notifyAuthChange();
}

/** Updates just the principal (for profile changes) */
export function setPrincipal(principal: UserPrincipal): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(PRINCIPAL_KEY, JSON.stringify(principal));
  notifyAuthChange();
}

/** Clears the local principal (cookies are cleared by the logout endpoint) */
export function clearAuth(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(PRINCIPAL_KEY);
  notifyAuthChange();
}

export function onAuthChange(handler: () => void): () => void {
  if (typeof window === "undefined") {
    return () => undefined;
  }

  window.addEventListener(AUTH_EVENT, handler);
  window.addEventListener("storage", handler);
  return () => {
    window.removeEventListener(AUTH_EVENT, handler);
    window.removeEventListener("storage", handler);
  };
}

/** Attempts a silent token refresh via the refresh token cookie */
async function attemptSilentRefresh(
  fetcher: typeof fetch = fetch,
): Promise<boolean> {
  try {
    const response = await fetcher("/api/auth/refresh", {
      method: "POST",
      credentials: "include",
    });
    if (response.ok) {
      const data = await response.json();
      if (data.principal) {
        setPrincipal(data.principal);
      }
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

/**
 * Validates the current session. If the access token has expired, attempts a silent refresh
 * using the refresh token cookie before clearing auth.
 */
export async function validateAuthSession(
  fetcher: typeof fetch = fetch,
): Promise<SessionValidationResult> {
  const { principal } = getAuthState();
  if (!principal) {
    return "no-token";
  }

  try {
    const response = await fetcher("/api/auth/session", {
      method: "GET",
      credentials: "include",
    });
    if (response.ok) {
      return "valid";
    }
    if (response.status === 401 || response.status === 403) {
      const refreshed = await attemptSilentRefresh(fetcher);
      if (refreshed) {
        return "refreshed";
      }
      clearAuth();
      return "expired";
    }
    return "valid";
  } catch {
    return "network-error";
  }
}
