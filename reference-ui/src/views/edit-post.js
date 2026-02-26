import { get, put } from "../api.js";
import { renderError } from "../components/error-display.js";

/** Edit an existing post - loads markdownSource from the backend */
export async function render(container, params) {
  const postId = params.id;

  try {
    // FindById returns ContentDetail with markdownSource for authors/admins
    const post = await get(`/posts/${postId}`);

    if (!post.markdownSource && post.markdownSource !== "") {
      container.innerHTML = "<article><h2>Cannot edit</h2><p>Markdown source not available. You may not have permission to edit this post.</p></article>";
      return;
    }

    let categories = [];
    try {
      categories = await get("/categories");
    } catch {
      // Continue without categories
    }

    const categoryOptions = categories
      .map((c) => {
        const checked = (post.categories || []).includes(c.name) ? "checked" : "";
        return `<label><input type="checkbox" name="categoryIds" value="${c.id}" ${checked} /> ${escapeHtml(c.name)}</label>`;
      })
      .join("");

    const currentTags = (post.tags || []).join(", ");

    container.innerHTML = `
      <h2>Edit Post</h2>
      <form id="edit-form">
        <label for="title">Title</label>
        <input type="text" id="title" name="title" required value="${escapeAttr(post.title)}" />

        <label for="markdownSource">Content (Markdown)</label>
        <textarea id="markdownSource" name="markdownSource" rows="12" required>${escapeHtml(post.markdownSource)}</textarea>

        <label for="tags">Tags (comma-separated)</label>
        <input type="text" id="tags" name="tags" value="${escapeAttr(currentTags)}" />

        ${categoryOptions ? `<fieldset><legend>Categories</legend>${categoryOptions}</fieldset>` : ""}

        <button type="submit">Save Changes</button>
        <div id="edit-status"></div>
      </form>
    `;

    document.getElementById("edit-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const status = document.getElementById("edit-status");
      const fd = new FormData(e.target);

      const tags = fd.get("tags")
        ? fd.get("tags").split(",").map((t) => t.trim()).filter(Boolean)
        : [];
      const categoryIds = fd.getAll("categoryIds");

      try {
        const result = await put(`/posts/${postId}`, {
          title: fd.get("title"),
          markdownSource: fd.get("markdownSource"),
          tags,
          categoryIds,
        });
        status.innerHTML = `<p>Saved. <a href="/posts/${escapeHtml(result.slug)}">View post</a></p>`;
      } catch (err) {
        renderError(status, err);
      }
    });
  } catch (err) {
    renderError(container, err);
  }
}

function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

function escapeAttr(str) {
  if (!str) return "";
  return str.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}
