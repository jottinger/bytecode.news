import { get, put } from "../api.js";
import { escapeHtml, escapeAttr } from "../escape.js";
import { createEditor } from "../editor.js";
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
        <textarea id="markdownSource" name="markdownSource"></textarea>

        <label for="tags">Tags (comma-separated)</label>
        <input type="text" id="tags" name="tags" value="${escapeAttr(currentTags)}" />

        ${categoryOptions ? `<fieldset><legend>Categories</legend>${categoryOptions}</fieldset>` : ""}

        <button type="submit">Save Changes</button>
        <div id="edit-status"></div>
      </form>
    `;

    const editor = createEditor(document.getElementById("markdownSource"), {
      initialValue: post.markdownSource,
    });

    document.getElementById("edit-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const status = document.getElementById("edit-status");
      const fd = new FormData(e.target);

      const markdownSource = editor.value();
      if (!markdownSource.trim()) {
        status.innerHTML = "<p>Content is required.</p>";
        return;
      }

      const tags = fd.get("tags")
        ? fd.get("tags").split(",").map((t) => t.trim()).filter(Boolean)
        : [];
      const categoryIds = fd.getAll("categoryIds");

      try {
        const result = await put(`/posts/${postId}`, {
          title: fd.get("title"),
          markdownSource,
          tags,
          categoryIds,
        });
        const isPublished = result.status === "APPROVED" && result.publishedAt && new Date(result.publishedAt) <= new Date();
        if (isPublished) {
          const viewPath = result.slug.includes("/") ? `/posts/${escapeHtml(result.slug)}` : `/pages/${escapeHtml(result.slug)}`;
          status.innerHTML = `<p>Saved. <a href="${viewPath}">View post</a></p>`;
        } else {
          status.innerHTML = `<p>Saved.</p>`;
        }
      } catch (err) {
        renderError(status, err);
      }
    });
  } catch (err) {
    renderError(container, err);
  }
}
