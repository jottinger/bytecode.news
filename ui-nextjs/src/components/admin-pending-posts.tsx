"use client";

import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { getAuthState } from "@/lib/client-auth";
import { formatDate } from "@/lib/format";
import { ContentListResponse, ContentSummary, ContentDetail } from "@/lib/types";
import { HighlightedHtml } from "@/components/highlighted-html";

type ProblemLike = { detail?: string; title?: string; message?: string };

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

function displayAuthor(name: string | undefined): string {
  if (!name || name === "UNKNOWN") return "Unattributed";
  return name;
}

function ReaderPreview({
  post,
  onClose,
}: {
  post: ContentDetail;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-background/80 backdrop-blur-sm"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="relative w-full max-w-3xl mx-4 my-8 bg-background border border-border shadow-lg">
        {/* Pending banner */}
        <div className="bg-amber/10 border-b border-amber/30 px-6 py-3 flex items-center justify-between">
          <span className="section-label text-amber">
            Draft Preview &mdash; Not Published
          </span>
          <button
            type="button"
            onClick={onClose}
            className="section-label text-muted-foreground hover:text-foreground transition-colors"
          >
            Close
          </button>
        </div>

        {/* Article content mirroring the real article page */}
        <article className="px-6 md:px-8 py-8">
          <header>
            <div className="border-t-2 border-amber/50 border-dashed mb-6" />
            <h1 className="headline-lead text-foreground mb-4">{post.title}</h1>
            <div className="byline text-muted-foreground flex items-center gap-1.5 flex-wrap">
              <span>By {displayAuthor(post.authorDisplayName)}</span>
              {post.publishedAt && (
                <>
                  <span className="text-border/60 mx-1">|</span>
                  <time dateTime={post.publishedAt}>
                    {formatDate(post.publishedAt)}
                  </time>
                </>
              )}
            </div>
          </header>

          {post.tags.length > 0 && (
            <div className="tags mt-4">
              {post.tags.map((tag) => (
                <span className="tag" key={tag}>
                  {tag}
                </span>
              ))}
            </div>
          )}

          <div className="border-t border-border/40 mt-6 mb-6" />

          <HighlightedHtml className="post-body" html={post.renderedHtml} />
        </article>

        {/* Bottom pending banner */}
        <div className="bg-amber/10 border-t border-amber/30 px-6 py-3">
          <span className="section-label text-amber">
            End of Draft Preview
          </span>
        </div>
      </div>
    </div>
  );
}

export function AdminPendingPosts() {
  const [posts, setPosts] = useState<ContentSummary[]>([]);
  const [showDeleted, setShowDeleted] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [previewPost, setPreviewPost] = useState<ContentDetail | null>(null);
  const [previewLoading, setPreviewLoading] = useState<string | null>(null);

  const auth = getAuthState();
  const isAdmin =
    auth.principal?.role === "ADMIN" ||
    auth.principal?.role === "SUPER_ADMIN";

  const loadPending = useCallback(async () => {
    if (!auth.token || !isAdmin) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(
        `/api/admin/posts/pending?page=0&size=50&deleted=${showDeleted ? "true" : "false"}`,
        {
          headers: {
            Accept: "application/json",
            Authorization: `Bearer ${auth.token}`,
          },
          cache: "no-store",
        },
      );
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(
          detailMessage(payload, "Could not load pending posts."),
        );
      }
      const list = payload as ContentListResponse;
      setPosts(list.posts || []);
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : "Could not load pending posts.",
      );
    } finally {
      setLoading(false);
    }
  }, [auth.token, isAdmin, showDeleted]);

  useEffect(() => {
    void loadPending();
  }, [loadPending]);

  async function approve(postId: string) {
    if (!auth.token) return;
    setBusyId(postId);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch(`/api/admin/posts/${postId}/approve`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({ publishedAt: new Date().toISOString() }),
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not approve post."));
      }
      setStatus("Post approved.");
      await loadPending();
    } catch (approveError) {
      setError(
        approveError instanceof Error
          ? approveError.message
          : "Could not approve post.",
      );
    } finally {
      setBusyId(null);
    }
  }

  async function remove(postId: string, hard: boolean) {
    const message = hard
      ? "Permanently delete this post? This cannot be undone."
      : "Delete this draft? It can be restored later.";
    if (!window.confirm(message)) return;

    if (!auth.token) return;
    setBusyId(postId);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch(
        `/api/admin/posts/${postId}?hard=${hard ? "true" : "false"}`,
        {
          method: "DELETE",
          headers: {
            Authorization: `Bearer ${auth.token}`,
          },
        },
      );
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not delete post."));
      }
      setStatus(hard ? "Post permanently deleted." : "Post deleted.");
      await loadPending();
    } catch (removeError) {
      setError(
        removeError instanceof Error
          ? removeError.message
          : "Could not delete post.",
      );
    } finally {
      setBusyId(null);
    }
  }

  async function openPreview(postId: string) {
    if (!auth.token) return;
    setPreviewLoading(postId);
    try {
      const response = await fetch(`/api/post-by-id/${postId}`, {
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        cache: "no-store",
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not load post preview."));
      }
      setPreviewPost(payload as ContentDetail);
    } catch (previewError) {
      setError(
        previewError instanceof Error
          ? previewError.message
          : "Could not load preview.",
      );
    } finally {
      setPreviewLoading(null);
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
      {/* Tab switcher */}
      <nav className="flex gap-0 mb-6 border-b border-border/40">
          <button
            type="button"
            onClick={() => setShowDeleted(false)}
            disabled={busyId !== null}
            className={`section-label px-4 py-3 -mb-px transition-colors ${
              !showDeleted
                ? "text-amber border-b-2 border-amber"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            Active
          </button>
          <button
            type="button"
            onClick={() => setShowDeleted(true)}
            disabled={busyId !== null}
            className={`section-label px-4 py-3 -mb-px transition-colors ${
              showDeleted
                ? "text-amber border-b-2 border-amber"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            Deleted
          </button>
        </nav>

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

        {/* Loading state */}
        {loading ? (
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground/50">
              Loading drafts...
            </p>
          </div>
        ) : posts.length === 0 ? (
          <div className="py-12 text-center">
            <p className="section-label text-muted-foreground/50 mb-3">
              No {showDeleted ? "deleted" : "pending"} drafts
            </p>
            <p className="text-muted-foreground/40 text-sm">
              {showDeleted
                ? "No drafts have been deleted."
                : "All submissions have been reviewed."}
            </p>
          </div>
        ) : (
          <div className="animate-fade-in">
            {posts.map((post, i) => (
              <article
                key={post.id}
                className={`group py-6 ${i > 0 ? "border-t border-border/30" : ""}`}
              >
                <div className="flex items-start gap-6">
                  <div className="flex-1 min-w-0">
                    <h2 className="headline-secondary text-foreground mb-2">
                      {post.title}
                    </h2>
                    {post.excerpt && (
                      <p className="text-muted-foreground text-sm leading-relaxed line-clamp-2 mb-3">
                        {post.excerpt}
                      </p>
                    )}
                    <div className="byline text-muted-foreground/50 flex items-center gap-1.5 flex-wrap">
                      <span>By {displayAuthor(post.authorDisplayName)}</span>
                      {post.publishedAt && (
                        <>
                          <span className="text-border/40 mx-0.5">|</span>
                          <time dateTime={post.publishedAt}>
                            {formatDate(post.publishedAt)}
                          </time>
                        </>
                      )}
                    </div>
                    {post.categories.length > 0 && (
                      <div className="tags mt-3">
                        {post.categories.map((cat) => (
                          <span className="tag" key={cat}>
                            {cat}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex flex-wrap items-center gap-2 mt-4 pt-4 border-t border-border/20">
                  <button
                    type="button"
                    className="section-label px-3 py-2 text-amber border border-amber/40 hover:bg-amber/10 transition-colors disabled:opacity-50"
                    disabled={previewLoading === post.id || busyId === post.id}
                    onClick={() => openPreview(post.id)}
                  >
                    {previewLoading === post.id
                      ? "Loading..."
                      : "View as Reader"}
                  </button>
                  <Link
                    className="section-label px-3 py-2 text-amber border border-amber/40 hover:bg-amber/10 transition-colors"
                    href={`/edit/${post.id}`}
                  >
                    Edit
                  </Link>
                  {!showDeleted && (
                    <button
                      type="button"
                      className="auth-button"
                      disabled={busyId === post.id}
                      onClick={() => approve(post.id)}
                    >
                      Approve
                    </button>
                  )}
                  <div className="flex-1" />
                  {!showDeleted && (
                    <button
                      type="button"
                      className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors disabled:opacity-50"
                      disabled={busyId === post.id}
                      onClick={() => remove(post.id, false)}
                    >
                      Delete
                    </button>
                  )}
                  <button
                    type="button"
                    className="section-label px-3 py-2 text-muted-foreground border border-border/40 hover:text-destructive hover:border-destructive/40 transition-colors disabled:opacity-50"
                    disabled={busyId === post.id}
                    onClick={() => remove(post.id, true)}
                  >
                    Hard Delete
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}

      {/* Reader preview modal */}
      {previewPost && (
        <ReaderPreview
          post={previewPost}
          onClose={() => setPreviewPost(null)}
        />
      )}
    </div>
  );
}
