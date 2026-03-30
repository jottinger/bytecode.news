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

  async function onDelete(id: string, categoryName: string) {
    if (!auth.token) return;
    if (!window.confirm(`Delete the "${categoryName}" category?`)) return;

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
      <div className="py-12 text-center">
        <p className="section-label text-muted-foreground">
          Admin access required.
        </p>
      </div>
    );
  }

  const topLevel = categories.filter((c) => !c.parentName);
  const children = categories.filter((c) => c.parentName);

  return (
    <div>
      {/* Create form */}
      <form
        className="mb-8 p-5 border border-border/40"
        onSubmit={onCreate}
      >
        <h3 className="section-label text-amber mb-4">New Category</h3>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
          <div>
            <label className="byline text-muted-foreground/50 block mb-1.5" htmlFor="category-name">
              Name
            </label>
            <input
              id="category-name"
              className="w-full border border-border bg-background text-foreground p-2 text-sm focus:outline-none focus:ring-1 focus:ring-amber"
              style={{ fontFamily: "var(--font-body), Georgia, serif" }}
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
            />
          </div>
          <div>
            <label className="byline text-muted-foreground/50 block mb-1.5" htmlFor="category-parent">
              Parent
            </label>
            <select
              id="category-parent"
              className="w-full border border-border bg-background text-foreground p-2 text-sm focus:outline-none focus:ring-1 focus:ring-amber appearance-none cursor-pointer"
              style={{ fontFamily: "var(--font-body), Georgia, serif" }}
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
          </div>
          <div>
            <button type="submit" className="auth-button w-full" disabled={busy}>
              {busy ? "Creating..." : "Create"}
            </button>
          </div>
        </div>
      </form>

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

      {loading ? (
        <div className="py-12 text-center">
          <p className="section-label text-muted-foreground/50">Loading categories...</p>
        </div>
      ) : categories.length === 0 ? (
        <div className="py-12 text-center">
          <p className="section-label text-muted-foreground/50 mb-3">No categories yet</p>
          <p className="text-muted-foreground/40 text-sm">
            Create your first category above.
          </p>
        </div>
      ) : (
        <div className="animate-fade-in">
          {topLevel.map((category, i) => {
            const subcategories = children.filter((c) => c.parentName === category.name);
            return (
              <div
                key={category.id}
                className={`py-5 ${i > 0 ? "border-t border-border/30" : ""}`}
              >
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <h3 className="headline-brief text-foreground">
                      {category.name}
                    </h3>
                    {subcategories.length > 0 && (
                      <div className="flex flex-wrap gap-2 mt-2">
                        {subcategories.map((sub) => (
                          <span key={sub.id} className="tag">
                            {sub.name}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <button
                    type="button"
                    className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors shrink-0 disabled:opacity-50"
                    disabled={busy}
                    onClick={() => onDelete(category.id, category.name)}
                  >
                    Delete
                  </button>
                </div>
              </div>
            );
          })}
          {/* Show orphaned children (parent not in top-level list) */}
          {children
            .filter((c) => !topLevel.some((t) => t.name === c.parentName))
            .map((category, i) => (
              <div
                key={category.id}
                className={`py-5 border-t border-border/30`}
              >
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <h3 className="headline-brief text-foreground">
                      {category.name}
                    </h3>
                    <p className="byline text-muted-foreground/50 mt-1">
                      Child of {category.parentName}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors shrink-0 disabled:opacity-50"
                    disabled={busy}
                    onClick={() => onDelete(category.id, category.name)}
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
