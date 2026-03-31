"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { getAuthState } from "@/lib/client-auth";
import { formatDate } from "@/lib/format";
import { CommentNode } from "@/lib/types";
import { Ellipsis, CornerUpLeft } from "lucide-react";
import { HighlightedHtml } from "@/components/highlighted-html";
import { CommentCreateForm } from "@/components/comment-create-form";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface CommentThreadProps {
  comments: CommentNode[];
  year: string;
  month: string;
  slug: string;
}

interface CommentItemProps {
  node: CommentNode;
  isAdmin: boolean;
  token: string | null;
  userId: string | null;
  year: string;
  month: string;
  slug: string;
}

function htmlToText(html: string): string {
  if (typeof window === "undefined") {
    return "";
  }
  const node = window.document.createElement("div");
  node.innerHTML = html;
  return node.textContent?.trim() || "";
}

function CommentItem({ node, isAdmin, token, userId, year, month, slug }: CommentItemProps) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [replying, setReplying] = useState(false);
  const [markdownSource, setMarkdownSource] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isOwnComment = userId != null && node.authorId === userId;

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

    if (!window.confirm("Are you sure you want to remove this comment? This cannot be undone.")) {
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
          <div className="comment-input-wrapper">
            <textarea
              className="auth-input submit-textarea comment-textarea"
              value={markdownSource}
              onChange={(event) => setMarkdownSource(event.target.value)}
              required
            />
            <div className="comment-input-actions">
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
              <button type="submit" className="auth-button" disabled={busy}>
                {busy ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
        </form>
      ) : (
        <HighlightedHtml className="post-body comment-body" html={node.renderedHtml} />
      )}

      {token && !node.deleted && !replying && !editing ? (
        <div className="comment-actions-row">
          <button
            type="button"
            className="comment-action-link"
            onClick={() => setReplying(true)}
          >
            <CornerUpLeft size={14} /> Reply
          </button>
          {(node.editable || isOwnComment || isAdmin) ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button type="button" className="comment-action-link" aria-label="Comment actions">
                  <Ellipsis size={16} />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {node.editable || isOwnComment ? (
                  <DropdownMenuItem
                    disabled={busy}
                    onClick={() => {
                      setMarkdownSource(node.markdownSource || htmlToText(node.renderedHtml));
                      setEditing(true);
                      setError(null);
                      setStatus(null);
                    }}
                  >
                    Edit comment
                  </DropdownMenuItem>
                ) : null}
                {isAdmin ? (
                  <DropdownMenuItem
                    className="text-destructive"
                    disabled={busy}
                    onClick={onDelete}
                  >
                    Remove comment
                  </DropdownMenuItem>
                ) : null}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      ) : null}

      {replying ? (
        <div className="comment-reply-form">
          <CommentCreateForm
            year={year}
            month={month}
            slug={slug}
            parentCommentId={node.id}
            onCancel={() => setReplying(false)}
          />
        </div>
      ) : null}

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}

      {node.children.length > 0 ? (
        <div className="comment-children">
          {node.children.map((child) => (
            <CommentItem key={child.id} node={child} isAdmin={isAdmin} token={token} userId={userId} year={year} month={month} slug={slug} />
          ))}
        </div>
      ) : null}
    </article>
  );
}

export function CommentThread({ comments, year, month, slug }: CommentThreadProps) {
  const auth = getAuthState();
  const isAdmin = auth.principal?.role === "ADMIN" || auth.principal?.role === "SUPER_ADMIN";
  return (
    <>
      {comments.map((comment) => (
        <CommentItem key={comment.id} node={comment} isAdmin={isAdmin} token={auth.token} userId={auth.principal?.id ?? null} year={year} month={month} slug={slug} />
      ))}
    </>
  );
}
