"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { MouseEvent } from "react";
import { clearAuth, getAuthState, onAuthChange } from "@/lib/client-auth";

export function AuthNav() {
  const [mounted, setMounted] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);
  const auth = useMemo(() => getAuthState(), [authVersion]);

  useEffect(() => {
    setMounted(true);
    return onAuthChange(() => setAuthVersion((v) => v + 1));
  }, []);

  // Avoid server/client auth-state mismatch during hydration.
  if (!mounted) {
    return null;
  }

  async function onLogout(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    const token = getAuthState().token;
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
    } catch {
      // Clear local auth even if backend is temporarily unavailable.
    } finally {
      clearAuth();
      setAuthVersion((v) => v + 1);
    }
  }

  if (!auth.token || !auth.principal) {
    return (
      <Link className="nav-link" href="/login">
        Login
      </Link>
    );
  }

  return (
    <div className="auth-nav">
      <Link className="nav-link" href="/profile">
        {auth.principal.displayName}
      </Link>
      {auth.principal.role === "ADMIN" || auth.principal.role === "SUPER_ADMIN" ? (
        <>
          <Link className="nav-link" href="/admin/pending">
            Pending
          </Link>
          <Link className="nav-link" href="/admin/users">
            Users
          </Link>
          <Link className="nav-link" href="/admin/categories">
            Categories
          </Link>
        </>
      ) : null}
      <a href="/logout" className="nav-link" onClick={onLogout}>
        Logout
      </a>
    </div>
  );
}
