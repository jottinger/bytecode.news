"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { getAuthState } from "@/lib/client-auth";
import { MarkdownPreview } from "@/components/markdown-preview";
import { ContentDetail } from "@/lib/types";

type ProblemLike = { detail?: string; title?: string; message?: string };
type TagsResponse = { tags?: unknown };
type SummaryResponse = { summary?: string };
type CategorySummary = { id: string; name?: string };

function toLocalDateTimeInput(value?: string): string {
  if (!value) {
    return "";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }
  const offsetMs = parsed.getTimezoneOffset() * 60_000;
  return new Date(parsed.getTime() - offsetMs).toISOString().slice(0, 16);
}

function splitTags(value: string): string[] {
  return value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);
}

function normalizeTags(payload: unknown): string[] {
  const raw =
    payload && typeof payload === "object" && "tags" in payload
      ? (payload as TagsResponse).tags
      : payload;
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

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

interface EditPostFormProps {
  postId: string;
}

export function EditPostForm({ postId }: EditPostFormProps) {
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState("");
  const [markdownSource, setMarkdownSource] = useState("");
  const [summary, setSummary] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  const [categoriesInput, setCategoriesInput] = useState("");
  const [publishedAtInput, setPublishedAtInput] = useState("");
  const [sortOrderInput, setSortOrderInput] = useState("0");
  const [categoryOptions, setCategoryOptions] = useState<CategorySummary[]>([]);
  const [postStatus, setPostStatus] = useState<string>("DRAFT");
  const [savedSlug, setSavedSlug] = useState<string>("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const auth = getAuthState();
  const canDeriveAiTags =
    auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  const canAdminManage = canDeriveAiTags;
  const isAdmin = canDeriveAiTags;
  const canAdminPublishDraft = canDeriveAiTags && postStatus === "DRAFT";
  const canSuggestTags = title.trim().length > 0 && markdownSource.trim().length > 0 && !busy;
  const canDeriveSummary = title.trim().length > 0 && markdownSource.trim().length > 0 && !busy;
  const previewText = useMemo(() => markdownSource, [markdownSource]);
  const principalId = auth.principal?.id ?? null;

  function splitCategories(value: string): string[] {
    return value
      .split(",")
      .map((category) => category.trim())
      .filter((category) => category.length > 0);
  }

  useEffect(() => {
    // @lat: [[http-api#Content and Comment Endpoints]]
    async function load() {
      if (!auth.principal) {
        setError("Sign in to edit posts.");
        setLoading(false);
        return;
      }

      try {
        const response = await fetch(`/api/post-by-id/${postId}`, {
          headers: {
            Accept: "application/json",
          },
          credentials: "include",
          cache: "no-store",
        });

        const payload = (await response.json()) as unknown;
        if (!response.ok) {
          throw new Error(detailMessage(payload, "Could not load post."));
        }

        const post = payload as ContentDetail;
        if (post.markdownSource == null) {
          throw new Error("Not authorized to edit this post.");
        }
        setTitle(post.title || "");
        setMarkdownSource(post.markdownSource);
        setSummary(post.excerpt || "");
        setTagsInput((post.tags || []).join(", "));
        setCategoriesInput((post.categories || []).join(", "));
        setPublishedAtInput(toLocalDateTimeInput(post.publishedAt));
        setSortOrderInput(String(post.sortOrder ?? 0));
        setPostStatus(post.status || "DRAFT");
        setSavedSlug(post.slug || "");
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : "Could not load post.");
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, [postId, principalId]);

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
        // Ignore category loading failures while editing.
      }
    })();
  }, []);

  async function save() {
    if (!auth.principal) {
      setError("Sign in to edit posts.");
      return false;
    }
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const categories = splitCategories(categoriesInput);
      if (!isAdmin && categories.some((category) => category.startsWith("_"))) {
        setError("Special categories (starting with _) are admin-only.");
        return false;
      }
      const categoryLookup = new Map(
        categoryOptions
          .map((option) => [String(option.name || "").trim().toLowerCase(), option.id] as const)
          .filter(([name, id]) => name.length > 0 && Boolean(id)),
      );
      const categoryIds = categories.map((name) => categoryLookup.get(name.toLowerCase())).filter(Boolean) as string[];
      if (categories.length > 0 && categoryIds.length !== categories.length) {
        const unknown = categories.filter((name) => !categoryLookup.has(name.toLowerCase()));
        setError(
          `Unknown categories: ${unknown.join(", ")}. Use existing categories or ask an admin to create them.`,
        );
        return false;
      }

      const response = await fetch(`/api/post-by-id/${postId}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          title,
          markdownSource,
          summary,
          tags: splitTags(tagsInput),
          categoryIds,
          ...(isAdmin
            ? {
                publishedAt: publishedAtInput ? new Date(publishedAtInput).toISOString() : null,
                sortOrder: Number.parseInt(sortOrderInput || "0", 10) || 0,
              }
            : {}),
        }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not save post."));
      }
      const post = payload as ContentDetail;
      setPostStatus(post.status || postStatus);
      setSavedSlug(post.slug || savedSlug);
      setPublishedAtInput(toLocalDateTimeInput(post.publishedAt));
      setSortOrderInput(String(post.sortOrder ?? 0));
      setStatus("Saved.");
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not save post.");
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function applyTags(path: string, mode: "heuristic" | "ai") {
    if (!auth.principal) {
      setError("Sign in to derive tags.");
      return;
    }
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const response = await fetch(path, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          title: title.trim(),
          markdownSource: markdownSource.trim(),
          existingTags: splitTags(tagsInput),
        }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(
          detailMessage(
            payload,
            mode === "ai" ? "Could not derive AI tags." : "Could not suggest heuristic tags.",
          ),
        );
      }
      const tags = normalizeTags(payload);
      if (tags.length === 0) {
        setStatus(mode === "ai" ? "AI returned no tag suggestions." : "No heuristic tags were suggested.");
      } else {
        setTagsInput(tags.join(", "));
        setStatus(
          mode === "ai"
            ? `Applied ${tags.length} AI-derived tag${tags.length === 1 ? "" : "s"} to the form.`
            : `Applied ${tags.length} heuristic tag${tags.length === 1 ? "" : "s"} to the form.`,
        );
      }
    } catch (tagError) {
      setError(tagError instanceof Error ? tagError.message : "Could not derive tags.");
    } finally {
      setBusy(false);
    }
  }

  async function applySummary() {
    if (!auth.principal) {
      setError("Sign in to derive a summary.");
      return;
    }
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const response = await fetch("/api/posts/derive-summary", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          title: title.trim(),
          markdownSource: markdownSource.trim(),
        }),
      });
      const payload = (await response.json()) as SummaryResponse & ProblemLike;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not derive summary."));
      }
      const generated = String(payload.summary || "").trim();
      if (!generated) {
        setStatus("No summary could be derived from the current content.");
        return;
      }
      setSummary(generated);
      setStatus("Applied heuristic summary to the form. Review before saving.");
    } catch (summaryError) {
      setError(summaryError instanceof Error ? summaryError.message : "Could not derive summary.");
    } finally {
      setBusy(false);
    }
  }

  async function approveNow() {
    if (!auth.principal) return;
    const ok = await save();
    if (!ok) return;
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const response = await fetch(`/api/admin/posts/${postId}/approve`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          publishedAt: publishedAtInput ? new Date(publishedAtInput).toISOString() : new Date().toISOString(),
        }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not publish post."));
      }
      const post = payload as ContentDetail;
      setPostStatus(post.status || "APPROVED");
      setSavedSlug(post.slug || savedSlug);
      setPublishedAtInput(toLocalDateTimeInput(post.publishedAt));
      setSortOrderInput(String(post.sortOrder ?? 0));
      setStatus("Published.");
    } catch (publishError) {
      setError(publishError instanceof Error ? publishError.message : "Could not publish post.");
    } finally {
      setBusy(false);
    }
  }

  async function deletePost(hard: boolean) {
    if (!auth.principal) return;
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      const response = await fetch(`/api/admin/posts/${postId}?hard=${hard ? "true" : "false"}`, {
        method: "DELETE",
        credentials: "include",
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not delete post."));
      }
      setStatus(hard ? "Post hard-deleted." : "Post deleted.");
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Could not delete post.");
    } finally {
      setBusy(false);
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    await save();
  }

  if (!auth.principal) {
    return (
      <section className="notice">
        <h2>Edit Post</h2>
        <p>Sign in to edit posts.</p>
      </section>
    );
  }

  if (loading) {
    return (
      <section className="notice">
        <p>Loading post...</p>
      </section>
    );
  }

  return (
    <section className="auth-shell submit-shell">
      <h2 className="auth-title">Edit Post</h2>
      <p className="auth-note">Status: {postStatus}</p>
      {savedSlug ? (
        <p className="auth-note">
          <Link className="pagelink" href={`/posts/${savedSlug}`}>
            View current post
          </Link>
        </p>
      ) : null}

      <form className="auth-form" onSubmit={onSubmit}>
        <label className="auth-label" htmlFor="edit-title">
          Title
        </label>
        <input
          id="edit-title"
          className="auth-input"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          required
        />

        <label className="auth-label" htmlFor="edit-markdown">
          Content (Markdown)
        </label>
        <textarea
          id="edit-markdown"
          className="auth-input submit-textarea markdown-textarea"
          value={markdownSource}
          onChange={(event) => setMarkdownSource(event.target.value)}
          required
        />
        <div className="markdown-preview" aria-live="polite">
          <p className="auth-label">Preview</p>
          {previewText.trim().length === 0 ? (
            <p className="auth-note">Preview appears here as you type markdown.</p>
          ) : (
            <MarkdownPreview source={previewText} className="post-body" />
          )}
        </div>

        <label className="auth-label" htmlFor="edit-summary">
          Summary (optional)
        </label>
        <textarea
          id="edit-summary"
          className="auth-input"
          rows={4}
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          placeholder="Optional manual summary for feed/front page excerpts."
        />

        <label className="auth-label" htmlFor="edit-tags">
          Tags (comma-separated)
        </label>
        <input
          id="edit-tags"
          className="auth-input auth-input-mono"
          value={tagsInput}
          onChange={(event) => setTagsInput(event.target.value)}
        />

        <label className="auth-label" htmlFor="edit-categories">
          Categories (comma-separated)
        </label>
        <input
          id="edit-categories"
          className="auth-input auth-input-mono"
          value={categoriesInput}
          onChange={(event) => setCategoriesInput(event.target.value)}
          list="edit-category-options"
        />
        <datalist id="edit-category-options">
          {categoryOptions.map((category) => (
            <option key={category.id} value={String(category.name || "")} />
          ))}
        </datalist>
        {!isAdmin ? (
          <p className="auth-note">Categories starting with "_" are restricted to admins.</p>
        ) : null}
        {isAdmin ? (
          <>
            <label className="auth-label" htmlFor="edit-published-at">
              Publish Date/Time
            </label>
            <input
              id="edit-published-at"
              type="datetime-local"
              className="auth-input auth-input-mono"
              value={publishedAtInput}
              onChange={(event) => setPublishedAtInput(event.target.value)}
            />

            <label className="auth-label" htmlFor="edit-sort-order">
              Sort Order
            </label>
            <input
              id="edit-sort-order"
              type="number"
              className="auth-input"
              value={sortOrderInput}
              onChange={(event) => setSortOrderInput(event.target.value)}
            />
            <p className="auth-note">
              Lower sort order appears first within the same published day.
            </p>
          </>
        ) : null}

        <div className="auth-actions">
          <button
            type="button"
            className="auth-button secondary"
            disabled={!canDeriveSummary}
            onClick={applySummary}
          >
            Generate Summary
          </button>
          <button
            type="button"
            className="auth-button secondary"
            disabled={!canSuggestTags}
            onClick={() => applyTags("/api/posts/derive-tags", "heuristic")}
          >
            Suggest Tags
          </button>
          {canDeriveAiTags ? (
            <button
              type="button"
              className="auth-button secondary"
              disabled={!canSuggestTags}
              onClick={() => applyTags(`/api/admin/posts/${postId}/derive-tags`, "ai")}
            >
              Derive AI Tags
            </button>
          ) : null}
          <button type="submit" className="auth-button" disabled={busy}>
            {busy ? "Saving..." : "Save Changes"}
          </button>
          {canAdminPublishDraft ? (
            <button type="button" className="auth-button secondary" disabled={busy} onClick={approveNow}>
              Publish Draft
            </button>
          ) : null}
          {canAdminManage ? (
            <button
              type="button"
              className="auth-button secondary"
              disabled={busy}
              onClick={() => deletePost(false)}
            >
              Delete Post
            </button>
          ) : null}
          {canAdminManage ? (
            <button
              type="button"
              className="auth-button secondary"
              disabled={busy}
              onClick={() => deletePost(true)}
            >
              Hard Delete Post
            </button>
          ) : null}
        </div>
      </form>

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
