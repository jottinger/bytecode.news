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
      <Link
        className="section-label text-muted-foreground hover:text-amber transition-colors duration-200"
        href="/login"
      >
        Log in
      </Link>
    );
  }

  const isAdmin = auth.principal.role === "ADMIN" || auth.principal.role === "SUPER_ADMIN";

  return (
    <div className="inline-flex items-center gap-1">
      <Link
        className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
        href="/submit"
      >
        Submit
      </Link>
      <div className="h-4 w-px bg-border/40" />
      <Link
        className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
        href="/profile"
      >
        {auth.principal.displayName}
      </Link>
      {isAdmin && (
        <>
          <Link
            className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
            href="/admin/pending"
          >
            Pending
          </Link>
          <Link
            className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
            href="/admin/users"
          >
            Users
          </Link>
          <Link
            className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
            href="/admin/categories"
          >
            Categories
          </Link>
        </>
      )}
      <a
        href="/logout"
        className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
        onClick={onLogout}
      >
        Logout
      </a>
    </div>
  );
}
