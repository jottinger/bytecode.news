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
  token: string | null;
  principal: UserPrincipal | null;
}

const TOKEN_KEY = "ui_nextjs_token";
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

export function getAuthState(): AuthState {
  if (typeof window === "undefined") {
    return { token: null, principal: null };
  }

  const token = window.localStorage.getItem(TOKEN_KEY);
  const principal = parsePrincipal(window.localStorage.getItem(PRINCIPAL_KEY));
  return { token, principal };
}

function notifyAuthChange(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(AUTH_EVENT));
  }
}

export function setAuth(login: LoginResponse): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(TOKEN_KEY, login.token);
  window.localStorage.setItem(PRINCIPAL_KEY, JSON.stringify(login.principal));
  notifyAuthChange();
}

export function setPrincipal(principal: UserPrincipal): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(PRINCIPAL_KEY, JSON.stringify(principal));
  notifyAuthChange();
}

export function clearAuth(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(TOKEN_KEY);
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
