"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { getAuthState } from "@/lib/client-auth";
import { MarkdownPreview } from "@/components/markdown-preview";

interface SubmitPostFormProps {
  anonymousSubmission: boolean;
}

type CategorySummary = { id: string; name?: string };
type TagsResponse = { tags?: unknown };
type ProblemLike = { detail?: string; title?: string; message?: string };

function splitTags(value: string): string[] {
  return value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);
}

function normalizeTags(payload: unknown): string[] {
  const raw = payload && typeof payload === "object" && "tags" in payload ? (payload as TagsResponse).tags : payload;
  if (Array.isArray(raw)) {
    return raw.map((tag) => String(tag).trim()).filter(Boolean);
  }
  if (raw && typeof raw === "object") {
    return Object.keys(raw).map((tag) => String(tag).trim()).filter(Boolean);
  }
  if (typeof raw === "string") {
    return splitTags(raw);
  }
  return [];
}

function toErrorMessage(status: number): string {
  if (status === 401) {
    return "Authentication required to submit posts.";
  }
  if (status === 400) {
    return "Submission rejected. Check title/content and try again.";
  }
  return "Could not submit post draft.";
}

export function SubmitPostForm({ anonymousSubmission }: SubmitPostFormProps) {
  const [title, setTitle] = useState("");
  const [markdownSource, setMarkdownSource] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  const [categoriesInput, setCategoriesInput] = useState("");
  const [categoryOptions, setCategoryOptions] = useState<CategorySummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createdPostId, setCreatedPostId] = useState<string | null>(null);
  const loadedAt = useMemo(() => Date.now(), []);

  const auth = getAuthState();
  const canSubmit = anonymousSubmission || Boolean(auth.token);
  const canDeriveAiTags =
    auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  const isAdmin = canDeriveAiTags;
  const canSuggestTags = title.trim().length > 0 && markdownSource.trim().length > 0 && !busy;

  function splitCategories(value: string): string[] {
    return value
      .split(",")
      .map((category) => category.trim())
      .filter((category) => category.length > 0);
  }

  useEffect(() => {
    void (async () => {
      try {
        const response = await fetch("/api/categories", {
          headers: { Accept: "application/json" },
          cache: "no-store",
        });
        if (!response.ok) return;
        const payload = (await response.json()) as CategorySummary[];
        setCategoryOptions(payload || []);
      } catch {
        // Ignore category loading failures; submission still works.
      }
    })();
  }, []);

  async function applySuggestedTags(path: string, mode: "heuristic" | "ai"): Promise<void> {
    const titleValue = title.trim();
    const markdownValue = markdownSource.trim();
    if (!titleValue) {
      setError("Title is required before deriving tags.");
      setStatus(null);
      return;
    }
    if (!markdownValue) {
      setError("Content is required before deriving tags.");
      setStatus(null);
      return;
    }

    setBusy(true);
    setError(null);
    setStatus(null);
    setCreatedPostId(null);
    try {
      const response = await fetch(path, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
        },
        body: JSON.stringify({
          title: titleValue,
          markdownSource: markdownValue,
          existingTags: splitTags(tagsInput),
        }),
      });

      if (!response.ok) {
        let detail = "";
        try {
          const problem = (await response.json()) as ProblemLike;
          detail = problem.detail || problem.message || problem.title || "";
        } catch {
          // ignore parse failures
        }
        const fallback =
          mode === "ai" ? "Could not derive AI tags." : "Could not suggest heuristic tags.";
        throw new Error(detail || fallback);
      }

      const payload = (await response.json()) as unknown;
      const tags = normalizeTags(payload);
      if (tags.length === 0) {
        setStatus(mode === "ai" ? "AI returned no tag suggestions." : "No heuristic tags were suggested.");
        return;
      }

      setTagsInput(tags.join(", "));
      setStatus(
        mode === "ai"
          ? `Applied ${tags.length} AI-derived tag${tags.length === 1 ? "" : "s"} to the form.`
          : `Applied ${tags.length} heuristic tag${tags.length === 1 ? "" : "s"} to the form.`,
      );
    } catch (suggestError) {
      setError(
        suggestError instanceof Error
          ? suggestError.message
          : mode === "ai"
            ? "Could not derive AI tags."
            : "Could not suggest heuristic tags.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setStatus(null);

    const categories = splitCategories(categoriesInput);
    if (!isAdmin && categories.some((category) => category.startsWith("_"))) {
      setBusy(false);
      setError("Special categories (starting with _) are admin-only.");
      return;
    }
    const categoryLookup = new Map(
      categoryOptions
        .map((option) => [String(option.name || "").trim().toLowerCase(), option.id] as const)
        .filter(([name, id]) => name.length > 0 && Boolean(id)),
    );
    const categoryIds = categories.map((name) => categoryLookup.get(name.toLowerCase())).filter(Boolean) as string[];
    if (categories.length > 0 && categoryIds.length !== categories.length) {
      const unknown = categories.filter((name) => !categoryLookup.has(name.toLowerCase()));
      setBusy(false);
      setError(
        `Unknown categories: ${unknown.join(", ")}. Use existing categories or ask an admin to create them. If your topic is new, submit without categories and an admin can classify it.`,
      );
      return;
    }

    try {
      const response = await fetch("/api/posts", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(auth.token ? { Authorization: `Bearer ${auth.token}` } : {}),
        },
        body: JSON.stringify({
          title,
          markdownSource,
          tags: splitTags(tagsInput),
          categoryIds,
          formLoadedAt: loadedAt,
        }),
      });

      if (!response.ok) {
        throw new Error(toErrorMessage(response.status));
      }

      const created = (await response.json()) as { id?: string; title: string; status: string };
      setStatus(`Draft saved: \"${created.title}\" (${created.status}).`);
      setCreatedPostId(created.id || null);
      setTitle("");
      setMarkdownSource("");
      setTagsInput("");
      setCategoriesInput("");
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not submit post draft.");
    } finally {
      setBusy(false);
    }
  }

  if (!canSubmit) {
    return (
      <section className="notice">
        <h2>Submit</h2>
        <p>Sign in to submit a post draft.</p>
        <p>
          <Link className="pagelink" href="/login?return=/submit">
            Go to login
          </Link>
        </p>
      </section>
    );
  }

  return (
    <section className="auth-shell submit-shell">
      <h2 className="auth-title">Submit a Draft</h2>
      <p className="auth-note">Submissions create drafts for admin review.</p>

      <form className="auth-form" onSubmit={onSubmit}>
        <label className="auth-label" htmlFor="post-title">
          Title
        </label>
        <input
          id="post-title"
          className="auth-input"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          required
        />

        <label className="auth-label" htmlFor="post-markdown">
          Content (Markdown)
        </label>
        <textarea
          id="post-markdown"
          className="auth-input submit-textarea"
          value={markdownSource}
          onChange={(event) => setMarkdownSource(event.target.value)}
          required
        />
        <div className="markdown-preview" aria-live="polite">
          <p className="auth-label">Preview</p>
          {markdownSource.trim().length === 0 ? (
            <p className="auth-note">Preview appears here as you type markdown.</p>
          ) : (
            <MarkdownPreview source={markdownSource} className="post-body" />
          )}
        </div>

        <label className="auth-label" htmlFor="post-tags">
          Tags (comma-separated)
        </label>
        <input
          id="post-tags"
          className="auth-input"
          value={tagsInput}
          onChange={(event) => setTagsInput(event.target.value)}
          placeholder="java, spring"
        />

        <label className="auth-label" htmlFor="post-categories">
          Categories (comma-separated)
        </label>
        <input
          id="post-categories"
          className="auth-input"
          value={categoriesInput}
          onChange={(event) => setCategoriesInput(event.target.value)}
          list="post-category-options"
          placeholder="architecture, documentation"
        />
        <datalist id="post-category-options">
          {categoryOptions.map((category) => (
            <option key={category.id} value={String(category.name || "")} />
          ))}
        </datalist>
        {!isAdmin ? (
          <p className="auth-note">Categories starting with "_" are restricted to admins.</p>
        ) : null}

        <div className="auth-actions">
          <button
            type="button"
            className="auth-button secondary"
            disabled={!canSuggestTags}
            onClick={() => applySuggestedTags("/api/posts/derive-tags", "heuristic")}
          >
            Suggest Tags
          </button>
          {canDeriveAiTags ? (
            <button
              type="button"
              className="auth-button secondary"
              disabled={!canSuggestTags}
              onClick={() => applySuggestedTags("/api/admin/posts/derive-tags", "ai")}
            >
              Derive AI Tags
            </button>
          ) : null}
          <button type="submit" className="auth-button" disabled={busy}>
            {busy ? "Submitting..." : "Submit draft"}
          </button>
        </div>
      </form>

      {status ? (
        <p className="auth-status">
          {status}{" "}
          {createdPostId && isAdmin ? (
            <Link className="pagelink" href={`/edit/${createdPostId}`}>
              Edit draft
            </Link>
          ) : null}
        </p>
      ) : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
