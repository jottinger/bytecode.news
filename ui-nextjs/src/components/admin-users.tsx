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

  async function userAction(path: string, method: "PUT" | "DELETE", successMessage: string) {
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
      setUsers((current) =>
        current.map((user) => (user.username === username ? { ...user, role: newRole } : user)),
      );
      await loadUsers(status);
    } catch (roleError) {
      setError(roleError instanceof Error ? roleError.message : "Role update failed.");
    }
  }

  if (!auth.token || !isAdmin) {
    return (
      <section className="notice">
        <h2>Manage Users</h2>
        <p>Admin access required.</p>
      </section>
    );
  }

  return (
    <section className="auth-shell submit-shell">
      <h2 className="auth-title">Manage Users</h2>

      <div className="auth-actions">
        <button className="auth-button secondary" type="button" onClick={() => loadUsers("ACTIVE")}>
          Active
        </button>
        <button className="auth-button secondary" type="button" onClick={() => loadUsers("SUSPENDED")}>
          Suspended
        </button>
        <button className="auth-button secondary" type="button" onClick={() => loadUsers("ERASED")}>
          Erased
        </button>
      </div>

      {loading ? <p>Loading users...</p> : null}
      {!loading && users.length === 0 ? <p>No {status.toLowerCase()} users.</p> : null}

      {!loading && users.length > 0 ? (
        <table className="factoid-table" role="grid">
          <thead>
            <tr>
              <th>Username</th>
              <th>Display Name</th>
              <th>Role</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.displayName}</td>
                <td>{user.role}</td>
                <td>
                  <div className="auth-actions">
                    {status === "ACTIVE" ? (
                      <>
                        <button
                          className="auth-button secondary"
                          type="button"
                          onClick={() =>
                            userAction(
                              `/api/admin/users/${encodeURIComponent(user.username)}/suspend`,
                              "PUT",
                              `Suspended ${user.username}.`,
                            )
                          }
                        >
                          Suspend
                        </button>
                        <button
                          className="auth-button secondary"
                          type="button"
                          onClick={() =>
                            userAction(
                              `/api/admin/users/${encodeURIComponent(user.username)}`,
                              "DELETE",
                              `Erased ${user.username}.`,
                            )
                          }
                        >
                          Erase
                        </button>
                        <select
                          className="auth-input"
                          defaultValue=""
                          onChange={(event) => {
                            const value = event.target.value as Role | "";
                            if (!value) return;
                            void changeRole(user.username, value);
                            event.currentTarget.value = "";
                          }}
                        >
                          <option value="">Change role...</option>
                          <option value="USER">USER</option>
                          <option value="ADMIN">ADMIN</option>
                          <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                        </select>
                      </>
                    ) : null}
                    {status === "SUSPENDED" ? (
                      <>
                        <button
                          className="auth-button secondary"
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
                          className="auth-button secondary"
                          type="button"
                          onClick={() =>
                            userAction(
                              `/api/admin/users/${encodeURIComponent(user.username)}`,
                              "DELETE",
                              `Erased ${user.username}.`,
                            )
                          }
                        >
                          Erase
                        </button>
                      </>
                    ) : null}
                    {status === "ERASED" ? (
                      <button
                        className="auth-button secondary"
                        type="button"
                        onClick={() =>
                          userAction(
                            `/api/admin/users/${encodeURIComponent(user.username)}/purge`,
                            "DELETE",
                            `Purged content for ${user.username}.`,
                          )
                        }
                      >
                        Purge Content
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {notice ? <p className="auth-status">{notice}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
