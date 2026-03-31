"use client";

import { useEffect, useMemo, useState } from "react";
import { getAuthState } from "@/lib/client-auth";

type Role = "USER" | "ADMIN" | "SUPER_ADMIN";
type UserStatus = "ACTIVE" | "SUSPENDED" | "ERASED";
type UserPrincipal = {
  id: string;
  username: string;
  displayName: string;
  role: Role;
};
type ProblemLike = { detail?: string; title?: string; message?: string };

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

const statusTabs: { value: UserStatus; label: string }[] = [
  { value: "ACTIVE", label: "Active" },
  { value: "SUSPENDED", label: "Suspended" },
  { value: "ERASED", label: "Erased" },
];

export function AdminUsers() {
  const auth = useMemo(() => getAuthState(), []);
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  const [status, setStatus] = useState<UserStatus>("ACTIVE");
  const [users, setUsers] = useState<UserPrincipal[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function loadUsers(nextStatus: UserStatus) {
    if (!auth.token) return;
    setLoading(true);
    setError(null);
    setNotice(null);
    setStatus(nextStatus);
    try {
      const response = await fetch(`/api/admin/users?status=${nextStatus}`, {
        headers: {
          Authorization: `Bearer ${auth.token}`,
        },
        cache: "no-store",
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not load users."));
      }
      setUsers((payload as UserPrincipal[]) || []);
    } catch (loadError) {
      setUsers([]);
      setError(loadError instanceof Error ? loadError.message : "Could not load users.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadUsers("ACTIVE");
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function userAction(
    path: string,
    method: "PUT" | "DELETE",
    successMessage: string,
    confirmMessage?: string,
  ) {
    if (confirmMessage && !window.confirm(confirmMessage)) return;
    if (!auth.token) return;
    setError(null);
    setNotice(null);
    try {
      const response = await fetch(path, {
        method,
        headers: {
          Authorization: `Bearer ${auth.token}`,
        },
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "User action failed."));
      }
      setNotice(successMessage);
      await loadUsers(status);
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : "User action failed.");
    }
  }

  async function changeRole(username: string, newRole: Role) {
    if (!auth.token) return;
    setError(null);
    setNotice(null);
    try {
      const response = await fetch(`/api/admin/users/${encodeURIComponent(username)}/role`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({ newRole }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Role update failed."));
      }
      setNotice(`Updated ${username} to ${newRole}.`);
      await loadUsers(status);
    } catch (roleError) {
      setError(roleError instanceof Error ? roleError.message : "Role update failed.");
    }
  }

  if (!auth.token || !isAdmin) {
    return (
      <div className="py-12 text-center">
        <p className="section-label text-muted-foreground">
          Admin access required.
        </p>
      </div>
    );
  }

  return (
    <div>
      {/* Status tabs */}
      <nav className="flex gap-0 mb-6 border-b border-border/40">
        {statusTabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => loadUsers(tab.value)}
            className={`section-label px-4 py-3 -mb-px transition-colors ${
              status === tab.value
                ? "text-amber border-b-2 border-amber"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {tab.label}
            {!loading && status === tab.value && (
              <span className="ml-2 text-muted-foreground/50">({users.length})</span>
            )}
          </button>
        ))}
      </nav>

      {/* Status / error messages */}
      {notice && (
        <div className="mb-6 px-4 py-3 border border-amber/30 bg-amber/5">
          <p className="section-label text-amber">{notice}</p>
        </div>
      )}
      {error && (
        <div className="mb-6 px-4 py-3 border border-destructive/30 bg-destructive/5">
          <p className="section-label text-destructive">{error}</p>
        </div>
      )}

      {loading ? (
        <div className="py-12 text-center">
          <p className="section-label text-muted-foreground/50">Loading users...</p>
        </div>
      ) : users.length === 0 ? (
        <div className="py-12 text-center">
          <p className="section-label text-muted-foreground/50 mb-3">
            No {status.toLowerCase()} users
          </p>
        </div>
      ) : (
        <div className="animate-fade-in">
          {users.map((user, i) => (
            <div
              key={user.id}
              className={`py-5 ${i > 0 ? "border-t border-border/30" : ""}`}
            >
              <div className="flex items-start justify-between gap-4 flex-wrap">
                <div className="min-w-0">
                  <h3 className="headline-brief text-foreground mb-1">
                    {user.displayName}
                  </h3>
                  <div className="byline text-muted-foreground/50 flex items-center gap-1.5 flex-wrap">
                    <span>{user.username}</span>
                    <span className="text-border/40 mx-0.5">|</span>
                    <span className={user.role === "SUPER_ADMIN" ? "text-amber" : ""}>
                      {user.role}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2 flex-wrap">
                  {status === "ACTIVE" && (
                    <>
                      <select
                        className="section-label px-3 py-2 bg-transparent border border-border/40 text-muted-foreground hover:border-amber/40 transition-colors appearance-none cursor-pointer"
                        defaultValue=""
                        onChange={(event) => {
                          const value = event.target.value as Role | "";
                          if (!value) return;
                          void changeRole(user.username, value);
                          event.currentTarget.value = "";
                        }}
                      >
                        <option value="">Role...</option>
                        <option value="USER">User</option>
                        <option value="ADMIN">Admin</option>
                        <option value="SUPER_ADMIN">Super Admin</option>
                      </select>
                      <button
                        className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-amber hover:border-amber/40 transition-colors"
                        type="button"
                        onClick={() =>
                          userAction(
                            `/api/admin/users/${encodeURIComponent(user.username)}/suspend`,
                            "PUT",
                            `Suspended ${user.username}.`,
                            `Suspend ${user.username}?`,
                          )
                        }
                      >
                        Suspend
                      </button>
                      <button
                        className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors"
                        type="button"
                        onClick={() =>
                          userAction(
                            `/api/admin/users/${encodeURIComponent(user.username)}`,
                            "DELETE",
                            `Erased ${user.username}.`,
                            `Erase ${user.username}? This will mark the account for deletion.`,
                          )
                        }
                      >
                        Erase
                      </button>
                    </>
                  )}
                  {status === "SUSPENDED" && (
                    <>
                      <button
                        className="section-label px-3 py-2 text-amber border border-amber/40 hover:bg-amber/10 transition-colors"
                        type="button"
                        onClick={() =>
                          userAction(
                            `/api/admin/users/${encodeURIComponent(user.username)}/unsuspend`,
                            "PUT",
                            `Unsuspended ${user.username}.`,
                          )
                        }
                      >
                        Unsuspend
                      </button>
                      <button
                        className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors"
                        type="button"
                        onClick={() =>
                          userAction(
                            `/api/admin/users/${encodeURIComponent(user.username)}`,
                            "DELETE",
                            `Erased ${user.username}.`,
                            `Erase ${user.username}? This will mark the account for deletion.`,
                          )
                        }
                      >
                        Erase
                      </button>
                    </>
                  )}
                  {status === "ERASED" && (
                    <button
                      className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors"
                      type="button"
                      onClick={() =>
                        userAction(
                          `/api/admin/users/${encodeURIComponent(user.username)}/purge`,
                          "DELETE",
                          `Purged content for ${user.username}.`,
                          `Permanently purge all content from ${user.username}? This cannot be undone.`,
                        )
                      }
                    >
                      Purge Content
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
