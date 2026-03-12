"use client";

import { FormEvent, useEffect, useState } from "react";
import { getAuthState } from "@/lib/client-auth";

type CategorySummary = {
  id: string;
  name: string;
  parentName?: string | null;
};
type ProblemLike = { detail?: string; title?: string; message?: string };

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

export function AdminCategories() {
  const auth = getAuthState();
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  const [categories, setCategories] = useState<CategorySummary[]>([]);
  const [name, setName] = useState("");
  const [parentId, setParentId] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadCategories() {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch("/api/categories", {
        headers: { Accept: "application/json" },
        cache: "no-store",
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not load categories."));
      }
      setCategories((payload as CategorySummary[]) || []);
    } catch (loadError) {
      setCategories([]);
      setError(loadError instanceof Error ? loadError.message : "Could not load categories.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadCategories();
  }, []);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    if (!auth.token) return;

    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch("/api/admin/categories", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({
          name: name.trim(),
          parentId: parentId || null,
        }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not create category."));
      }

      setName("");
      setParentId("");
      setStatus("Category created.");
      await loadCategories();
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Could not create category.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(id: string) {
    if (!auth.token) return;
    if (!window.confirm("Delete this category?")) return;

    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch(`/api/admin/categories/${encodeURIComponent(id)}`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${auth.token}`,
        },
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not delete category."));
      }
      setStatus("Category deleted.");
      await loadCategories();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Could not delete category.");
    } finally {
      setBusy(false);
    }
  }

  if (!auth.token || !isAdmin) {
    return (
      <section className="notice">
        <h2>Manage Categories</h2>
        <p>Admin access required.</p>
      </section>
    );
  }

  return (
    <section className="auth-shell submit-shell">
      <h2 className="auth-title">Manage Categories</h2>
      <form className="auth-form" onSubmit={onCreate}>
        <label className="auth-label" htmlFor="category-name">
          New category
        </label>
        <input
          id="category-name"
          className="auth-input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          required
        />

        <label className="auth-label" htmlFor="category-parent">
          Parent (optional)
        </label>
        <select
          id="category-parent"
          className="auth-input"
          value={parentId}
          onChange={(event) => setParentId(event.target.value)}
        >
          <option value="">(top-level)</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>

        <div className="auth-actions">
          <button type="submit" className="auth-button" disabled={busy}>
            {busy ? "Saving..." : "Create category"}
          </button>
        </div>
      </form>

      {loading ? <p>Loading categories...</p> : null}
      {!loading && categories.length === 0 ? <p>No categories yet.</p> : null}

      {!loading && categories.length > 0 ? (
        <table className="factoid-table" role="grid">
          <thead>
            <tr>
              <th>Name</th>
              <th>Parent</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => (
              <tr key={category.id}>
                <td>{category.name}</td>
                <td>{category.parentName || "(top-level)"}</td>
                <td>
                  <button
                    type="button"
                    className="auth-button secondary"
                    disabled={busy}
                    onClick={() => onDelete(category.id)}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
