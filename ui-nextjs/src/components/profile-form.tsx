"use client";

import { FormEvent, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { clearAuth, getAuthState, setPrincipal, UserPrincipal } from "@/lib/client-auth";

type ProblemLike = { detail?: string; title?: string; message?: string };

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

export function ProfileForm() {
  const router = useRouter();
  const auth = useMemo(() => getAuthState(), []);
  const [displayName, setDisplayName] = useState(auth.principal?.displayName || "");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!auth.token || !auth.principal) {
    return (
      <section className="py-8 md:py-12">
        <div className="max-w-2xl mx-auto px-6 md:px-8">
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground">
              Sign in to view your profile.
            </p>
          </div>
        </div>
      </section>
    );
  }

  async function onSave(event: FormEvent) {
    event.preventDefault();
    if (displayName.trim().length === 0) {
      setError("Display name cannot be empty.");
      return;
    }

    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch("/api/auth/profile", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({ displayName: displayName.trim() }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not update profile."));
      }
      setPrincipal(payload as UserPrincipal);
      setStatus("Profile updated.");
      router.refresh();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not update profile.");
    } finally {
      setBusy(false);
    }
  }

  async function onExport() {
    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch("/api/auth/export", {
        method: "GET",
        headers: {
          Authorization: `Bearer ${auth.token}`,
        },
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not export account data."));
      }
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "bytecode-news-export.json";
      a.click();
      URL.revokeObjectURL(url);
      setStatus("Download started.");
    } catch (exportError) {
      setError(exportError instanceof Error ? exportError.message : "Could not export account data.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!window.confirm("Permanently delete your account? This cannot be undone.")) {
      return;
    }

    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch("/api/auth/account", {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({}),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not delete account."));
      }
      clearAuth();
      router.push("/");
      router.refresh();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Could not delete account.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="py-8 md:py-12">
      <div className="max-w-2xl mx-auto px-6 md:px-8">
        {/* Header */}
        <header className="mb-10">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-8 border-b border-border/40">
            <p className="section-label text-amber mb-3">Account</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{
                fontSize: "clamp(2rem, 5vw, 3rem)",
                letterSpacing: "-0.025em",
              }}
            >
              {auth.principal.displayName}
            </h1>
          </div>
        </header>

        {/* Account details */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4 mb-10">
          <div>
            <p className="byline text-muted-foreground/50 mb-1">Username</p>
            <p className="text-foreground">{auth.principal.username}</p>
          </div>
          <div>
            <p className="byline text-muted-foreground/50 mb-1">Role</p>
            <p className="text-foreground">{auth.principal.role}</p>
          </div>
        </div>

        {/* Status / error messages */}
        {status && (
          <div className="mb-6 px-4 py-3 border border-amber/30 bg-amber/5">
            <p className="section-label text-amber">{status}</p>
          </div>
        )}
        {error && (
          <div className="mb-6 px-4 py-3 border border-destructive/30 bg-destructive/5">
            <p className="section-label text-destructive">{error}</p>
          </div>
        )}

        {/* Edit display name */}
        <div className="border-t border-border/40 pt-8 mb-8">
          <h2 className="section-label text-amber mb-5">Display Name</h2>
          <form className="flex gap-3 items-end" onSubmit={onSave}>
            <div className="flex-1">
              <label className="byline text-muted-foreground/50 block mb-1.5" htmlFor="display-name">
                Shown on your posts and comments
              </label>
              <input
                id="display-name"
                className="w-full border border-border bg-background text-foreground p-2 text-sm focus:outline-none focus:ring-1 focus:ring-amber"
                style={{ fontFamily: "var(--font-body), Georgia, serif" }}
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                required
                maxLength={100}
              />
            </div>
            <button type="submit" className="auth-button shrink-0" disabled={busy}>
              {busy ? "Saving..." : "Save"}
            </button>
          </form>
        </div>

        {/* Data & account actions */}
        <div className="border-t border-border/40 pt-8">
          <h2 className="section-label text-amber mb-5">Data & Account</h2>
          <div className="space-y-4">
            <div className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm text-foreground">Export your data</p>
                <p className="text-muted-foreground/50 text-xs mt-0.5">
                  Download all your account data as JSON
                </p>
              </div>
              <button
                type="button"
                className="section-label px-3 py-2 text-amber border border-amber/40 hover:bg-amber/10 transition-colors disabled:opacity-50"
                disabled={busy}
                onClick={onExport}
              >
                Export
              </button>
            </div>

            <div className="border-t border-border/20" />

            <div className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm text-foreground">Delete account</p>
                <p className="text-muted-foreground/50 text-xs mt-0.5">
                  Permanently remove your account and all associated data
                </p>
              </div>
              <button
                type="button"
                className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors disabled:opacity-50"
                disabled={busy}
                onClick={onDelete}
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
