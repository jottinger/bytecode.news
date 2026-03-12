"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getAuthState } from "@/lib/client-auth";
import { formatDate } from "@/lib/format";
import { ContentListResponse, ContentSummary } from "@/lib/types";

type ProblemLike = { detail?: string; title?: string; message?: string };

function detailMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === "object") {
    const problem = payload as ProblemLike;
    return problem.detail || problem.message || problem.title || fallback;
  }
  return fallback;
}

export function AdminPendingPosts() {
  const [posts, setPosts] = useState<ContentSummary[]>([]);
  const [showDeleted, setShowDeleted] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const auth = getAuthState();
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";

  async function loadPending() {
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
        throw new Error(detailMessage(payload, "Could not load pending posts."));
      }
      const list = payload as ContentListResponse;
      setPosts(list.posts || []);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Could not load pending posts.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadPending();
  }, [showDeleted]); // eslint-disable-line react-hooks/exhaustive-deps

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
      setError(approveError instanceof Error ? approveError.message : "Could not approve post.");
    } finally {
      setBusyId(null);
    }
  }

  async function remove(postId: string, hard: boolean) {
    if (!auth.token) return;
    setBusyId(postId);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch(`/api/admin/posts/${postId}?hard=${hard ? "true" : "false"}`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${auth.token}`,
        },
      });
      const payload = (await response.json()) as unknown;
      if (!response.ok) {
        throw new Error(detailMessage(payload, "Could not delete post."));
      }
      setStatus(hard ? "Post hard-deleted." : "Post deleted.");
      await loadPending();
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Could not delete post.");
    } finally {
      setBusyId(null);
    }
  }

  if (!auth.token || !isAdmin) {
    return (
      <section className="notice">
        <h2>Pending Drafts</h2>
        <p>Admin access required.</p>
      </section>
    );
  }

  if (loading) {
    return (
      <section className="notice">
        <p>Loading pending drafts...</p>
      </section>
    );
  }

  return (
    <section className="auth-shell submit-shell">
      <h2 className="auth-title">Pending Drafts</h2>
      <div className="auth-actions">
        <button
          type="button"
          className="auth-button secondary"
          disabled={busyId !== null}
          onClick={() => setShowDeleted(false)}
        >
          Active drafts
        </button>
        <button
          type="button"
          className="auth-button secondary"
          disabled={busyId !== null}
          onClick={() => setShowDeleted(true)}
        >
          Deleted drafts
        </button>
      </div>
      {posts.length === 0 ? <p className="auth-note">No pending drafts.</p> : null}

      {posts.map((post) => (
        <article className="story-card" key={post.id}>
          <div className="meta">
            {formatDate(post.publishedAt)} | {post.authorDisplayName}
          </div>
          <h3 className="story-title">{post.title}</h3>
          {post.excerpt ? <p className="story-excerpt">{post.excerpt}</p> : null}
          <div className="auth-actions">
            <Link className="auth-button secondary" href={`/edit/${post.id}`}>
              Edit
            </Link>
            {!showDeleted ? (
              <button
                type="button"
                className="auth-button"
                disabled={busyId === post.id}
                onClick={() => approve(post.id)}
              >
                Approve
              </button>
            ) : null}
            {!showDeleted ? (
              <button
                type="button"
                className="auth-button secondary"
                disabled={busyId === post.id}
                onClick={() => remove(post.id, false)}
              >
                Delete
              </button>
            ) : null}
            <button
              type="button"
              className="auth-button secondary"
              disabled={busyId === post.id}
              onClick={() => remove(post.id, true)}
            >
              Hard Delete
            </button>
          </div>
        </article>
      ))}

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
