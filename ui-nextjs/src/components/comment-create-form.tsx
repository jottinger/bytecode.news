"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { getAuthState } from "@/lib/client-auth";
import { MarkdownPreview } from "@/components/markdown-preview";

interface CommentCreateFormProps {
  year: string;
  month: string;
  slug: string;
}

export function CommentCreateForm({ year, month, slug }: CommentCreateFormProps) {
  const router = useRouter();
  const [markdownSource, setMarkdownSource] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const auth = getAuthState();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!auth.token) {
      setError("Sign in to comment.");
      return;
    }

    setBusy(true);
    setStatus(null);
    setError(null);

    try {
      const response = await fetch(`/api/posts/${year}/${month}/${slug}/comments`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${auth.token}`,
        },
        body: JSON.stringify({ markdownSource }),
      });

      if (!response.ok) {
        throw new Error(response.status === 401 ? "Sign in to comment." : "Could not post comment.");
      }

      setMarkdownSource("");
      setStatus("Comment posted.");
      router.refresh();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not post comment.");
    } finally {
      setBusy(false);
    }
  }

  if (!auth.token) {
    return (
      <p>
        <Link className="pagelink" href={`/login?return=/posts/${year}/${month}/${slug}`}>
          Sign in to comment
        </Link>
      </p>
    );
  }

  return (
    <form className="auth-form comment-form" onSubmit={onSubmit}>
      <label className="auth-label" htmlFor="comment-markdown">
        Add comment
      </label>
      <textarea
        id="comment-markdown"
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
      <div className="auth-actions">
        <button type="submit" className="auth-button" disabled={busy}>
          {busy ? "Posting..." : "Post comment"}
        </button>
      </div>
      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </form>
  );
}
