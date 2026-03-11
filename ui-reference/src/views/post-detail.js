import { get } from "../api.js";
import { isLoggedIn, getPrincipal } from "../auth.js";
import { hasRole } from "../roles.js";
import { escapeHtml } from "../escape.js";
import { renderError } from "../components/error-display.js";
import { renderCommentThread, attachCommentHandlers } from "../components/comment-thread.js";
import { renderCommentForm } from "../components/comment-form.js";
import { setTitle } from "../title.js";

/** Single post view with comments */
export async function render(container, params) {
  const slug = `${params.year}/${params.month}/${params.slug}`;

  try {
    const post = await get(`/posts/${slug}`);
    setTitle(post.title);
    const date = post.publishedAt
      ? new Date(post.publishedAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })
      : "Draft";
    const tags = (post.tags || []).map((t) => `<mark>${escapeHtml(t)}</mark>`).join(" ");
    const categories = (post.categories || []).map((c) => escapeHtml(c)).join(", ");

    const principal = getPrincipal();
    const canEdit = principal && (
      (principal.id === post.authorId) ||
      hasRole(principal.role, "ADMIN")
    );
    const editLink = canEdit ? ` | <small><a href="/edit/${post.id}" class="secondary">Edit</a></small>` : "";

    container.innerHTML = `
      <article>
        <header>
          <hgroup>
            <h2>${escapeHtml(post.title)}</h2>
            <p>By ${escapeHtml(post.authorDisplayName)} | ${date} | ${post.commentCount} comment${post.commentCount === 1 ? "" : "s"}
            ${categories ? ` | ${categories}` : ""}${editLink}</p>
          </hgroup>
        </header>
        <div id="post-body">${post.renderedHtml}</div>
        ${tags ? `<footer>${tags}</footer>` : ""}
      </article>
      <section id="comments">
        <h3>Comments</h3>
        <div id="comment-tree" aria-busy="true">Loading comments...</div>
        <div id="new-comment"></div>
      </section>
    `;

    // Syntax-highlight code blocks (non-blocking, cosmetic)
    import("../editor.js")
      .then(({ highlightCodeBlocks }) => highlightCodeBlocks(document.getElementById("post-body")))
      .catch(() => {});

    // Load comments
    try {
      const commentData = await get(`/posts/${slug}/comments`);
      const treeEl = document.getElementById("comment-tree");
      if (commentData.comments && commentData.comments.length > 0) {
        treeEl.innerHTML = renderCommentThread(commentData.comments, slug);
        attachCommentHandlers(treeEl, slug);
      } else {
        treeEl.innerHTML = "<p>No comments yet.</p>";
      }
      treeEl.removeAttribute("aria-busy");

      // Top-level comment form for logged-in users
      if (isLoggedIn()) {
        const { createEditor } = await import("../editor.js");
        const newComment = document.getElementById("new-comment");
        newComment.innerHTML = "<h4>Add a Comment</h4>" + renderCommentForm(slug, null);
        const textarea = newComment.querySelector("textarea");
        const editor = createEditor(textarea, { compact: true });
        const form = newComment.querySelector("form");
        form.addEventListener("submit", async (e) => {
          e.preventDefault();
          const { post: apiPost } = await import("../api.js");
          try {
            await apiPost(`/posts/${slug}/comments`, {
              parentCommentId: null,
              markdownSource: editor.value(),
            });
            window.dispatchEvent(new PopStateEvent("popstate"));
          } catch (err) {
            newComment.insertAdjacentHTML("beforeend",
              `<p><small style="color:var(--pico-del-color)">${escapeHtml(err.detail || "Failed to post comment")}</small></p>`);
          }
        });
      }
    } catch (err) {
      const treeEl = document.getElementById("comment-tree");
      treeEl.innerHTML = "<p>Could not load comments.</p>";
      treeEl.removeAttribute("aria-busy");
    }
  } catch (err) {
    renderError(container, err);
  }
}
