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
type SummaryResponse = { summary?: string };
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
  const [summary, setSummary] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  const [categoriesInput, setCategoriesInput] = useState("");
  const [categoryOptions, setCategoryOptions] = useState<CategorySummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createdPostId, setCreatedPostId] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);
  const loadedAt = useMemo(() => Date.now(), []);

  const auth = getAuthState();
  const canSubmit = anonymousSubmission || Boolean(auth.principal);
  const canDeriveAiTags =
    auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  const isAdmin = canDeriveAiTags;
  const canSuggestTags = title.trim().length > 0 && markdownSource.trim().length > 0 && !busy;
  const canDeriveSummary =
    Boolean(auth.principal) && title.trim().length > 0 && markdownSource.trim().length > 0 && !busy;

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
        },
        credentials: "include",
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

  async function applyDerivedSummary(): Promise<void> {
    const titleValue = title.trim();
    const markdownValue = markdownSource.trim();
    if (!auth.principal) {
      setError("Sign in to derive a summary.");
      setStatus(null);
      return;
    }
    if (!titleValue) {
      setError("Title is required before deriving a summary.");
      setStatus(null);
      return;
    }
    if (!markdownValue) {
      setError("Content is required before deriving a summary.");
      setStatus(null);
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
        body: JSON.stringify({ title: titleValue, markdownSource: markdownValue }),
      });
      const payload = (await response.json()) as SummaryResponse & ProblemLike;
      if (!response.ok) {
        throw new Error(payload.detail || payload.message || payload.title || "Could not derive summary.");
      }
      const generated = String(payload.summary || "").trim();
      if (!generated) {
        setStatus("No summary could be derived from the current content.");
        return;
      }
      setSummary(generated);
      setStatus("Applied heuristic summary to the form. Review before submitting.");
    } catch (summaryError) {
      setError(summaryError instanceof Error ? summaryError.message : "Could not derive summary.");
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
        `Unknown categories: ${unknown.join(", ")}. Use existing categories or ask an admin to create them.`,
      );
      return;
    }

    try {
      const response = await fetch("/api/posts", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          title,
          markdownSource,
          tags: splitTags(tagsInput),
          categoryIds,
          ...(auth.principal ? { summary } : {}),
          formLoadedAt: loadedAt,
        }),
      });

      if (!response.ok) {
        throw new Error(toErrorMessage(response.status));
      }

      const created = (await response.json()) as { id?: string; title: string; status: string };
      setStatus(`Draft saved: "${created.title}" (${created.status}).`);
      setCreatedPostId(created.id || null);
      setTitle("");
      setMarkdownSource("");
      setSummary("");
      setTagsInput("");
      setCategoriesInput("");
      setShowPreview(false);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not submit post draft.");
    } finally {
      setBusy(false);
    }
  }

  if (!canSubmit) {
    return (
      <section className="py-8 md:py-12">
        <div className="max-w-3xl mx-auto px-6 md:px-8">
          <header className="mb-10">
            <div className="border-t-2 border-amber animate-rule-draw" />
            <div className="pt-6 pb-8 border-b border-border/40">
              <p className="section-label text-amber mb-3">Contribute</p>
              <h1
                className="font-display text-foreground leading-tight"
                style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-0.025em" }}
              >
                Submit a Draft
              </h1>
            </div>
          </header>
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground/50 mb-4">
              Sign in to submit a post draft.
            </p>
            <Link
              className="section-label px-4 py-2.5 text-amber border border-amber/40 hover:bg-amber/10 transition-colors"
              href="/login?return=/submit"
            >
              Go to Login
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="py-8 md:py-12">
      <div className="max-w-3xl mx-auto px-6 md:px-8">
        {/* Header */}
        <header className="mb-10">
          <div className="border-t-2 border-amber animate-rule-draw" />
          <div className="pt-6 pb-8 border-b border-border/40">
            <p className="section-label text-amber mb-3">Contribute</p>
            <h1
              className="font-display text-foreground leading-tight"
              style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-0.025em" }}
            >
              Submit a Draft
            </h1>
            <p className="text-muted-foreground/60 text-sm mt-2">
              Submissions create drafts for admin review before publishing.
            </p>
          </div>
        </header>

        {/* Status / error messages */}
        {status && (
          <div className="mb-6 px-4 py-3 border border-amber/30 bg-amber/5">
            <p className="section-label text-amber">
              {status}{" "}
              {createdPostId && isAdmin ? (
                <Link className="underline hover:no-underline" href={`/edit/${createdPostId}`}>
                  Edit draft
                </Link>
              ) : null}
            </p>
          </div>
        )}
        {error && (
          <div className="mb-6 px-4 py-3 border border-destructive/30 bg-destructive/5">
            <p className="section-label text-destructive">{error}</p>
          </div>
        )}

        <form onSubmit={onSubmit}>
          {/* Title */}
          <div className="mb-6">
            <label className="byline text-muted-foreground/50 block mb-1.5" htmlFor="post-title">
              Title
            </label>
            <input
              id="post-title"
              className="w-full border border-border bg-background text-foreground p-3 focus:outline-none focus:ring-1 focus:ring-amber"
              style={{
                fontFamily: "var(--font-display), Georgia, serif",
                fontSize: "clamp(1.2rem, 3vw, 1.5rem)",
                letterSpacing: "-0.01em",
              }}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              required
              placeholder="Your article title"
            />
          </div>

          {/* Content */}
          <div className="mb-6">
            <div className="flex items-center justify-between mb-1.5">
              <label className="byline text-muted-foreground/50" htmlFor="post-markdown">
                Content (Markdown)
              </label>
              {markdownSource.trim().length > 0 && (
                <button
                  type="button"
                  className="section-label text-muted-foreground hover:text-amber transition-colors"
                  onClick={() => setShowPreview(!showPreview)}
                >
                  {showPreview ? "Hide Preview" : "Show Preview"}
                </button>
              )}
            </div>
            <textarea
              id="post-markdown"
              className="w-full border border-border bg-background text-foreground p-3 focus:outline-none focus:ring-1 focus:ring-amber resize-y"
              style={{
                fontFamily: "var(--font-mono), monospace",
                fontSize: "0.9rem",
                lineHeight: "1.6",
                minHeight: "18rem",
              }}
              value={markdownSource}
              onChange={(event) => setMarkdownSource(event.target.value)}
              required
            />
            {showPreview && markdownSource.trim().length > 0 && (
              <div className="mt-4 p-5 border border-border/40 bg-card/50">
                <p className="section-label text-amber mb-4">Preview</p>
                <MarkdownPreview source={markdownSource} className="post-body" />
              </div>
            )}
          </div>

          {/* Summary (authenticated users only) */}
          {auth.principal && (
            <div className="mb-6">
              <div className="flex items-center justify-between mb-1.5">
                <label className="byline text-muted-foreground/50" htmlFor="post-summary">
                  Summary (optional)
                </label>
                {canDeriveSummary && (
                  <button
                    type="button"
                    className="section-label text-muted-foreground hover:text-amber transition-colors disabled:opacity-50"
                    disabled={!canDeriveSummary}
                    onClick={applyDerivedSummary}
                  >
                    Generate Summary
                  </button>
                )}
              </div>
              <textarea
                id="post-summary"
                className="w-full border border-border bg-background text-foreground p-3 focus:outline-none focus:ring-1 focus:ring-amber resize-y"
                style={{ fontFamily: "var(--font-body), Georgia, serif", fontSize: "0.95rem" }}
                rows={3}
                value={summary}
                onChange={(event) => setSummary(event.target.value)}
                placeholder="Brief summary for the front page and feed excerpts."
              />
            </div>
          )}

          {/* Tags and Categories side by side */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-6">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="byline text-muted-foreground/50" htmlFor="post-tags">
                  Tags
                </label>
                <div className="flex gap-2">
                  {canSuggestTags && (
                    <button
                      type="button"
                      className="section-label text-muted-foreground hover:text-amber transition-colors disabled:opacity-50"
                      disabled={!canSuggestTags}
                      onClick={() => applySuggestedTags("/api/posts/derive-tags", "heuristic")}
                    >
                      Suggest
                    </button>
                  )}
                  {canDeriveAiTags && canSuggestTags && (
                    <button
                      type="button"
                      className="section-label text-muted-foreground hover:text-amber transition-colors disabled:opacity-50"
                      disabled={!canSuggestTags}
                      onClick={() => applySuggestedTags("/api/admin/posts/derive-tags", "ai")}
                    >
                      AI Tags
                    </button>
                  )}
                </div>
              </div>
              <input
                id="post-tags"
                className="w-full border border-border bg-background text-foreground p-2 text-sm focus:outline-none focus:ring-1 focus:ring-amber"
                style={{ fontFamily: "var(--font-body), Georgia, serif" }}
                value={tagsInput}
                onChange={(event) => setTagsInput(event.target.value)}
                placeholder="java, spring, architecture"
              />
            </div>

            <div>
              <label className="byline text-muted-foreground/50 block mb-1.5" htmlFor="post-categories">
                Categories
              </label>
              <input
                id="post-categories"
                className="w-full border border-border bg-background text-foreground p-2 text-sm focus:outline-none focus:ring-1 focus:ring-amber"
                style={{ fontFamily: "var(--font-body), Georgia, serif" }}
                value={categoriesInput}
                onChange={(event) => setCategoriesInput(event.target.value)}
                list="post-category-options"
                placeholder="guides, tutorials"
              />
              <datalist id="post-category-options">
                {categoryOptions.map((category) => (
                  <option key={category.id} value={String(category.name || "")} />
                ))}
              </datalist>
              {!isAdmin && (
                <p className="text-muted-foreground/40 text-xs mt-1">
                  Categories starting with "_" are restricted to admins.
                </p>
              )}
            </div>
          </div>

          {/* Submit */}
          <div className="border-t border-border/40 pt-6">
            <button type="submit" className="auth-button" disabled={busy}>
              {busy ? "Submitting..." : "Submit Draft"}
            </button>
          </div>
        </form>
      </div>
    </section>
  );
}
