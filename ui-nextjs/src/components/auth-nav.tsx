"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import type { MouseEvent } from "react";
import { clearAuth, getAuthState, onAuthChange, validateAuthSession } from "@/lib/client-auth";

type AuthNavProps = {
  allowAnonymousSubmission?: boolean;
};

export function AuthNav({ allowAnonymousSubmission = false }: AuthNavProps) {
  const [mounted, setMounted] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);
  const lastSessionValidationAt = useRef(0);
  const auth = useMemo(() => getAuthState(), [authVersion]);

  useEffect(() => {
    setMounted(true);
    return onAuthChange(() => setAuthVersion((v) => v + 1));
  }, []);

  useEffect(() => {
    if (!mounted) {
      return;
    }

    let disposed = false;
    const minimumValidationIntervalMs = 15_000;
    const validateNow = async () => {
      const now = Date.now();
      if (now - lastSessionValidationAt.current < minimumValidationIntervalMs) {
        return;
      }
      lastSessionValidationAt.current = now;
      const result = await validateAuthSession();
      if (!disposed && result === "expired") {
        setAuthVersion((v) => v + 1);
      }
    };

    void validateNow();
    const onWindowFocus = () => void validateNow();
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void validateNow();
      }
    };

    window.addEventListener("focus", onWindowFocus);
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      disposed = true;
      window.removeEventListener("focus", onWindowFocus);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [mounted]);

  if (!mounted) {
    return null;
  }

  async function onLogout(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
      });
    } catch {
      // Clear local auth even if backend is temporarily unavailable.
    } finally {
      clearAuth();
      setAuthVersion((v) => v + 1);
    }
  }

  if (!auth.principal) {
    return (
      <div className="inline-flex items-center gap-1">
        {allowAnonymousSubmission && (
          <>
            <Link
              className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
              href="/submit"
            >
              Submit
            </Link>
            <div className="h-4 w-px bg-border/40" />
          </>
        )}
        <Link
          className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
          href="/login"
        >
          Log in
        </Link>
      </div>
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
        <Link
          className="section-label text-muted-foreground hover:text-amber transition-colors duration-200 px-2"
          href="/admin"
        >
          Admin
        </Link>
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
