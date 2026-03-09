import { get, put } from "../api.js";
import { escapeHtml, escapeAttr } from "../escape.js";
import { createEditor } from "../editor.js";
import { renderError } from "../components/error-display.js";
import { getPrincipal } from "../auth.js";
import { hasRole } from "../roles.js";

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
    const principal = getPrincipal();
    const canPublishDraft = post.status === "DRAFT" && principal && hasRole(principal.role, "ADMIN");

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

        <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
          <button type="submit">Save Changes</button>
          ${canPublishDraft ? `<button type="button" id="publish-btn" class="contrast">Publish</button>` : ""}
        </div>
        <div id="edit-status"></div>
      </form>
    `;

    const editor = createEditor(document.getElementById("markdownSource"), {
      initialValue: post.markdownSource,
    });

    const savePost = async (formEl) => {
      const status = document.getElementById("edit-status");
      const fd = new FormData(formEl);

      const markdownSource = editor.value();
      if (!markdownSource.trim()) {
        status.innerHTML = "<p>Content is required.</p>";
        return null;
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
        return result;
      } catch (err) {
        renderError(status, err);
        return null;
      }
    };

    const form = document.getElementById("edit-form");
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      await savePost(form);
    });

    if (canPublishDraft) {
      document.getElementById("publish-btn").addEventListener("click", async () => {
        const status = document.getElementById("edit-status");
        const saved = await savePost(form);
        if (!saved) return;
        try {
          const approved = await put(`/admin/posts/${postId}/approve`, {
            publishedAt: new Date().toISOString(),
          });
          const viewPath = approved.slug?.includes("/") ? `/posts/${escapeHtml(approved.slug)}` : "/admin/posts";
          status.innerHTML = `<p>Published. <a href="${viewPath}">View post</a></p>`;
        } catch (err) {
          renderError(status, err);
        }
      });
    }
  } catch (err) {
    renderError(container, err);
  }
}
