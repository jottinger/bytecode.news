"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { setPrincipal, clearAuth } from "@/lib/client-auth";

/**
 * Handles the redirect after OIDC authentication.
 * The backend has already set httpOnly cookies; this page fetches the session
 * to store the principal for UI rendering, then redirects to the homepage.
 */
export default function AuthCallbackPage() {
  const router = useRouter();

  useEffect(() => {
    async function handleCallback() {
      try {
        const response = await fetch("/api/auth/session", {
          method: "GET",
          credentials: "include",
        });
        if (response.ok) {
          const principal = await response.json();
          setPrincipal(principal);
          router.push("/");
          router.refresh();
        } else {
          clearAuth();
          router.push("/login");
        }
      } catch {
        clearAuth();
        router.push("/login");
      }
    }
    handleCallback();
  }, [router]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <p className="byline">Completing sign-in...</p>
    </div>
  );
}
