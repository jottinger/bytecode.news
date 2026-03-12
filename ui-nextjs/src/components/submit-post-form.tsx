"use client";

import { FormEvent, useMemo, useState } from "react";
import Link from "next/link";
import { getAuthState } from "@/lib/client-auth";

interface SubmitPostFormProps {
  anonymousSubmission: boolean;
}

function splitTags(value: string): string[] {
  return value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);
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
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loadedAt = useMemo(() => Date.now(), []);

  const auth = getAuthState();
  const canSubmit = anonymousSubmission || Boolean(auth.token);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setStatus(null);

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
          formLoadedAt: loadedAt,
        }),
      });

      if (!response.ok) {
        throw new Error(toErrorMessage(response.status));
      }

      const created = (await response.json()) as { title: string; status: string };
      setStatus(`Draft saved: \"${created.title}\" (${created.status}).`);
      setTitle("");
      setMarkdownSource("");
      setTagsInput("");
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
    <section className="auth-shell">
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

        <button type="submit" className="auth-button" disabled={busy}>
          {busy ? "Submitting..." : "Submit draft"}
        </button>
      </form>

      {status ? <p className="auth-status">{status}</p> : null}
      {error ? <p className="auth-error">{error}</p> : null}
    </section>
  );
}
