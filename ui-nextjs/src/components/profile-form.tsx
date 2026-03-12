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
      <section className="notice">
        <h2>Profile</h2>
        <p>Sign in to view your profile.</p>
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
    <section className="auth-shell submit-shell">
      <h2 className="auth-title">Profile</h2>
      <p className="auth-note">
        <strong>Username:</strong> {auth.principal.username}
      </p>
      <p className="auth-note">
        <strong>Role:</strong> {auth.principal.role}
      </p>

      <form className="auth-form" onSubmit={onSave}>
        <label className="auth-label" htmlFor="display-name">
          Display Name
        </label>
        <input
          id="display-name"
          className="auth-input"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          required
          maxLength={100}
        />

        <div className="auth-actions">
          <button type="submit" className="auth-button" disabled={busy}>
            {busy ? "Saving..." : "Update Profile"}
          </button>
          <button type="button" className="auth-button secondary" disabled={busy} onClick={onExport}>
            Export My Data
          </button>
          <button type="button" className="auth-button secondary" disabled={busy} onClick={onDelete}>
            Delete Account
          </button>
        </div>
      </form>

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
