"use client";

export function TagLink({ tag, postId }: { tag: string; postId?: string }) {
  return (
    <a
      className="tag"
      href={`/tags/${encodeURIComponent(tag)}`}
      onClick={(e) => e.stopPropagation()}
      key={postId ? `${postId}-${tag}` : tag}
    >
      {tag}
    </a>
  );
}
