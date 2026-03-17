"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { getAuthState } from "@/lib/client-auth";
import { formatDate } from "@/lib/format";
import { CommentNode } from "@/lib/types";
import { HighlightedHtml } from "@/components/highlighted-html";

interface CommentThreadProps {
  comments: CommentNode[];
}

interface CommentItemProps {
  node: CommentNode;
  isAdmin: boolean;
  token: string | null;
}

function htmlToText(html: string): string {
  if (typeof window === "undefined") {
    return "";
  }
  const node = window.document.createElement("div");
  node.innerHTML = html;
  return node.textContent?.trim() || "";
}

function CommentItem({ node, isAdmin, token }: CommentItemProps) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [markdownSource, setMarkdownSource] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSaveEdit(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      setError("Sign in to edit comments.");
      return;
    }

    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch(`/api/comments/${node.id}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ markdownSource }),
      });
      const payload = (await response.json()) as { detail?: string; title?: string };
      if (!response.ok) {
        throw new Error(payload.detail || payload.title || "Could not edit comment.");
      }
      setEditing(false);
      setStatus("Comment updated.");
      router.refresh();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not edit comment.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!token) {
      setError("Sign in to moderate comments.");
      return;
    }

    setBusy(true);
    setStatus(null);
    setError(null);
    try {
      const response = await fetch(`/api/admin/comments/${node.id}`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      const payload = (await response.json()) as { detail?: string; title?: string };
      if (!response.ok) {
        throw new Error(payload.detail || payload.title || "Could not remove comment.");
      }
      setStatus("Comment removed.");
      router.refresh();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Could not remove comment.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="comment">
      <p className="meta">
        {node.authorDisplayName} at {formatDate(node.createdAt)}
      </p>
      {node.deleted ? (
        <p>[deleted]</p>
      ) : editing ? (
        <form className="auth-form" onSubmit={onSaveEdit}>
          <textarea
            className="auth-input submit-textarea"
            value={markdownSource}
            onChange={(event) => setMarkdownSource(event.target.value)}
            required
          />
          <div className="auth-actions">
            <button type="submit" className="auth-button" disabled={busy}>
              {busy ? "Saving..." : "Save comment"}
            </button>
            <button
              type="button"
              className="auth-button secondary"
              onClick={() => {
                setEditing(false);
                setError(null);
                setStatus(null);
              }}
              disabled={busy}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <HighlightedHtml className="post-body comment-body" html={node.renderedHtml} />
      )}

      {isAdmin && !node.deleted ? (
        <div className="auth-actions">
          <button
            type="button"
            className="auth-button secondary"
            disabled={busy}
            onClick={() => {
              setMarkdownSource(node.markdownSource || htmlToText(node.renderedHtml));
              setEditing(true);
              setError(null);
              setStatus(null);
            }}
          >
            Edit comment
          </button>
          <button type="button" className="auth-button secondary" disabled={busy} onClick={onDelete}>
            Remove comment
          </button>
        </div>
      ) : null}

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}

      {node.children.length > 0 ? (
        <div className="comment-children">
          {node.children.map((child) => (
            <CommentItem key={child.id} node={child} isAdmin={isAdmin} token={token} />
          ))}
        </div>
      ) : null}
    </article>
  );
}

export function CommentThread({ comments }: CommentThreadProps) {
  const auth = getAuthState();
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  return (
    <>
      {comments.map((comment) => (
        <CommentItem key={comment.id} node={comment} isAdmin={isAdmin} token={auth.token} />
      ))}
    </>
  );
}
