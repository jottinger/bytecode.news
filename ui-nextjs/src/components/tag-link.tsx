"use client";

export function TagLink({ tag, postId }: { tag: string; postId?: string }) {
  return (
    <a
      className="tag"
      href={`/tags/${encodeURIComponent(tag)}`}
      onClick={(e) => e.stopPropagation()}
      data-post-id={postId || ""}
    >
      {tag}
    </a>
  );
}
