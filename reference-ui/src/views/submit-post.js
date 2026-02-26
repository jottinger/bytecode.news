import { get, post as apiPost } from "../api.js";
import { renderError } from "../components/error-display.js";
import { navigate } from "../router.js";

/** Draft post creation form */
export async function render(container) {
  let categories = [];
  try {
    categories = await get("/categories");
  } catch {
    // Categories not available - form still works without them
  }

  const categoryOptions = categories
    .map((c) => `<label><input type="checkbox" name="categoryIds" value="${c.id}" /> ${escapeHtml(c.name)}</label>`)
    .join("");

  container.innerHTML = `
    <h2>Submit a Post</h2>
    <form id="submit-form">
      <label for="title">Title</label>
      <input type="text" id="title" name="title" required />

      <label for="markdownSource">Content (Markdown)</label>
      <textarea id="markdownSource" name="markdownSource" rows="12" required></textarea>

      <label for="tags">Tags (comma-separated)</label>
      <input type="text" id="tags" name="tags" placeholder="java, concurrency, loom" />

      ${categoryOptions ? `<fieldset><legend>Categories</legend>${categoryOptions}</fieldset>` : ""}

      <button type="submit">Submit Draft</button>
      <div id="submit-status"></div>
    </form>
  `;

  document.getElementById("submit-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = document.getElementById("submit-status");
    const fd = new FormData(e.target);

    const tags = fd.get("tags")
      ? fd.get("tags").split(",").map((t) => t.trim()).filter(Boolean)
      : [];
    const categoryIds = fd.getAll("categoryIds");

    try {
      const result = await apiPost("/posts", {
        title: fd.get("title"),
        markdownSource: fd.get("markdownSource"),
        tags,
        categoryIds,
      });
      status.innerHTML = `<p>Draft created. <a href="/posts/${escapeHtml(result.slug)}">View post</a></p>`;
    } catch (err) {
      renderError(status, err);
    }
  });
}

function escapeHtml(str) {
  if (!str) return "";
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}
