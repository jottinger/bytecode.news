import { escapeHtml } from "../escape.js";

/** Render a post summary as an <article> card */
export function renderPostCard(post) {
  const date = post.publishedAt
    ? new Date(post.publishedAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })
    : "Draft";
  const tags = (post.tags || []).map((t) => `<mark>${escapeHtml(t)}</mark>`).join(" ");
  const categories = (post.categories || []).map((c) => escapeHtml(c)).join(", ");
  const commentCount = Number.isFinite(post.commentCount) ? post.commentCount : 0;
  const commentText = commentCount > 0 ? ` | ${commentCount} comment${commentCount === 1 ? "" : "s"}` : "";

  return `<article>
    <header>
      <h3><a href="/posts/${escapeHtml(post.slug)}">${escapeHtml(post.title)}</a></h3>
      <small>By ${escapeHtml(post.authorDisplayName)} | ${date}${commentText}</small>
      ${categories ? `<br><small>${categories}</small>` : ""}
    </header>
    ${post.excerpt ? `<p>${escapeHtml(post.excerpt)}</p>` : ""}
    ${tags ? `<footer>${tags}</footer>` : ""}
  </article>`;
}
